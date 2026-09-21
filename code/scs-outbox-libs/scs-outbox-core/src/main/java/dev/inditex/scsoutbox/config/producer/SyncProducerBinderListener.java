package dev.inditex.scsoutbox.config.producer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.stream.binder.DefaultBinderFactory;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Configures synchronous producers for outbox-managed producer bindings.
 *
 * <p>The listener runs when a binder is initialized, so it can read the main and binder-child environments. Explicit asynchronous
 * configuration fails startup; missing configuration is applied automatically. Unsupported binders and bindings not managed by the outbox
 * are ignored. The order relative to other binder listeners is not guaranteed.
 */
@Slf4j
public class SyncProducerBinderListener implements DefaultBinderFactory.Listener {

  private final OutboxBindingsContext outboxBindingsContext;

  private final ObjectProvider<Bindable> bindables;

  public SyncProducerBinderListener(final OutboxBindingsContext outboxBindingsContext, final ObjectProvider<Bindable> bindables) {
    this.outboxBindingsContext = outboxBindingsContext;
    this.bindables = bindables;
  }

  @Override
  public void afterBinderContextInitialized(final String binderConfigurationName, final ConfigurableApplicationContext binderContext) {
    if (!this.outboxBindingsContext.isSyncProducerAutoConfigurationEnabled()) {
      log.warn(SyncProducerDiagnostics.autoConfigurationDisabledMessage());
      return;
    }

    final Object binder = resolveBinder(binderContext);
    final InputBindings inputBindings = InputBindings.from(this.bindables.stream().toList());
    final Optional<SupportedBinder> supportedBinder =
        this.outboxBindingsContext.supportFor(binderConfigurationName, binder, binderContext.getEnvironment(), inputBindings);

    if (supportedBinder.isEmpty()) {
      log.warn(SyncProducerDiagnostics.unsupportedBinderMessage(binderConfigurationName, binder.getClass().getName()));
      return;
    }

    this.configure(supportedBinder.get());
  }

  /**
   * Resolves the Spring Cloud Stream {@code Binder} bean from the binder child context.
   *
   * <p>Fully qualified on purpose: {@code org.springframework.cloud.stream.binder.Binder} would otherwise collide with
   * {@link org.springframework.boot.context.properties.bind.Binder}, used by {@link OutboxBindingsContext} and {@link OutboxBinding} to
   * resolve declared properties.
   */
  private static Object resolveBinder(final ConfigurableApplicationContext binderContext) {
    return binderContext.getBean(org.springframework.cloud.stream.binder.Binder.class);
  }

  private void configure(final SupportedBinder supportedBinder) {
    final List<String> configured = new ArrayList<>();
    final Map<String, String> violations = new LinkedHashMap<>();

    for (final OutboxBinding binding : supportedBinder.getBindings()) {
      final SyncOutcome outcome = binding.enforceSync();
      switch (outcome.status()) {
        case CONFIGURED -> configured.add(binding.name());
        case VIOLATION -> violations.put(binding.name(), describe(outcome.declaredSetting().orElseThrow()));
        case ALREADY_SYNCHRONOUS -> {
          // No-op: the binding already publishes synchronously, nothing to configure or report.
        }
        default -> throw new IllegalStateException("Unexpected sync outcome status: " + outcome.status());
      }
    }

    if (!configured.isEmpty()) {
      log.info("Enabled synchronous publishing for outbox-enabled bindings of binder [{}]: {}", supportedBinder.configurationName(),
          configured);
    }

    if (!violations.isEmpty()) {
      throw new IllegalStateException(SyncProducerDiagnostics.violationMessage(supportedBinder.configurationName(), violations));
    }
  }

  private static String describe(final DeclaredSetting declaredSetting) {
    return declaredSetting.property() + "=" + declaredSetting.value();
  }
}
