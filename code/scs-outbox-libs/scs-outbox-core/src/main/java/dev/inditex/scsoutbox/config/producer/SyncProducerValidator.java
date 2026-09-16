package dev.inditex.scsoutbox.config.producer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.producer.SyncProducerBinderRegistry.SyncProducerMapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.env.Environment;

/**
 * Verifies at startup that every outbox-enabled producer binding publishes synchronously.
 *
 * <p>{@link SyncProducerEnvironmentPostProcessor} deliberately steps aside whenever the application configures the synchronous producer
 * mode itself. This validator closes that gap: if the application explicitly selected an asynchronous producer for a binding managed by the
 * outbox, the context fails to start instead of silently running without the delivery guarantee the outbox is supposed to provide.
 *
 * <p>Rationale for failing fast: {@code OutboxMessagePublisher} deletes the outbox record as soon as {@code StreamBridge.send} returns
 * {@code true}. An asynchronous producer returns {@code true} before the broker acknowledges the record, so a subsequent broker failure,
 * retry exhaustion or serialization error loses the message with no trace left in the outbox table. This is the same reasoning behind
 * {@code MessageCaptureTxService} using {@code Propagation.MANDATORY}: a broken correctness precondition is reported loudly rather than
 * silently tolerated.
 *
 * <p>Bindings backed by a binder for which scs-outbox has no synchronous mapping, or whose binder type cannot be resolved, cannot be
 * verified. They are reported with a single aggregated warning and never block startup, because scs-outbox cannot tell whether such a
 * configuration is unsafe.
 *
 * <p>The same applies to bindings for which the synchronous producer property is not set at all. That can only happen when the binding is
 * not declared through {@code spring.cloud.stream.bindings.*} (so {@link SyncProducerEnvironmentPostProcessor} could not see it) or when
 * the application context was not bootstrapped through {@code SpringApplication}, in which case Spring Boot does not run
 * {@code EnvironmentPostProcessor}s at all. Failing in that situation would break perfectly valid test contexts, so it is only reported.
 */
@Slf4j
@RequiredArgsConstructor
public class SyncProducerValidator implements InitializingBean {

  private final OutboxProperties outboxProperties;

  private final BindingServiceProperties bindingServiceProperties;

  private final Environment environment;

  @Override
  public void afterPropertiesSet() {
    if (!this.outboxProperties.getBindings().getSyncProducers().isEnabled()) {
      log.warn("Automatic synchronous producer configuration is disabled"
          + " ('scs-outbox.bindings.sync-producers.enabled=false')."
          + " The application is fully responsible for configuring synchronous producers on outbox-enabled bindings;"
          + " messages may be lost if a producer publishes asynchronously.");
      return;
    }

    final BinderTypeResolver binderTypeResolver = new BinderTypeResolver(this.environment);
    final Binder binder = Binder.get(this.environment);
    final List<String> unverifiable = new ArrayList<>();
    final List<String> notApplied = new ArrayList<>();
    final Map<String, String> violations = new LinkedHashMap<>();

    for (final Map.Entry<String, BindingProperties> entry : this.bindingServiceProperties.getBindings().entrySet()) {
      final String bindingName = entry.getKey();
      final BindingProperties bindingProperties = entry.getValue();

      if (!SyncProducerBindings.isProducerBinding(bindingName, bindingProperties)
          || !this.outboxProperties.getBindings().matches(bindingName)) {
        continue;
      }

      final Optional<String> binderType = binderTypeResolver.resolve(bindingName, bindingProperties.getBinder());
      final Optional<SyncProducerMapping> mapping = binderType.flatMap(SyncProducerBinderRegistry::findByBinderType);
      if (mapping.isEmpty()) {
        unverifiable.add(bindingName + " (binder: " + binderType.orElse("unresolved") + ")");
        continue;
      }

      final SyncProducerMapping syncProducerMapping = mapping.get();
      final String bindingProperty = syncProducerMapping.bindingProperty(bindingName);
      final String effectiveValue = resolveEffectiveValue(binder, syncProducerMapping, bindingProperty);
      if (effectiveValue == null) {
        // The post-processor could not see this binding: it is not declared through 'spring.cloud.stream.bindings.*', or the
        // application context was not bootstrapped through SpringApplication (EnvironmentPostProcessors do not run then).
        notApplied.add(bindingName + " (" + bindingProperty + ")");
      } else if (!syncProducerMapping.requiredValue().equalsIgnoreCase(effectiveValue)) {
        violations.put(bindingName, bindingProperty + "=" + effectiveValue);
      }
    }

    if (!unverifiable.isEmpty()) {
      log.warn("scs-outbox cannot enable synchronous producers automatically for the following outbox-enabled bindings: {}."
          + " Supported binders: {}."
          + " Configure synchronous publishing manually for these bindings, otherwise messages may be lost:"
          + " the outbox record is deleted as soon as StreamBridge.send returns true.",
          unverifiable, SyncProducerBinderRegistry.supportedBinderTypes());
    }

    if (!notApplied.isEmpty()) {
      log.warn("Automatic synchronous producer configuration did not take effect for the following outbox-enabled bindings: {}."
          + " They are not declared through 'spring.cloud.stream.bindings.*', or the application context was not bootstrapped"
          + " through SpringApplication. Set the property explicitly, otherwise messages may be lost:"
          + " the outbox record is deleted as soon as StreamBridge.send returns true.",
          notApplied);
    }

    if (!violations.isEmpty()) {
      throw new IllegalStateException(buildViolationMessage(violations));
    }
  }

  private static String resolveEffectiveValue(final Binder binder, final SyncProducerMapping mapping, final String bindingProperty) {
    // Spring Cloud Stream resolves the binding-scoped entry over the binder-wide default, regardless of property source ordering.
    return binder.bind(SyncProducerBindings.propertyName(bindingProperty), Bindable.of(String.class))
        .orElseGet(() -> binder.bind(SyncProducerBindings.propertyName(mapping.binderDefaultProperty()), Bindable.of(String.class))
            .orElse(null));
  }

  private static String buildViolationMessage(final Map<String, String> violations) {
    final StringBuilder message = new StringBuilder()
        .append("The following outbox-enabled bindings are explicitly configured to publish asynchronously, ")
        .append("which breaks the delivery guarantee of the transactional outbox ")
        .append("(the outbox record is deleted as soon as StreamBridge.send returns true, ")
        .append("which for an asynchronous producer happens before the broker acknowledges the record):");
    violations.forEach((binding, detail) -> message.append(System.lineSeparator())
        .append("  - binding '").append(binding).append("': ").append(detail));
    message.append(System.lineSeparator())
        .append("Fix it by removing that property so scs-outbox can configure it, or opt out explicitly by either")
        .append(System.lineSeparator())
        .append("  - excluding the binding from the outbox via 'scs-outbox.bindings.exclusions', or")
        .append(System.lineSeparator())
        .append("  - setting 'scs-outbox.bindings.sync-producers.enabled=false', ")
        .append("accepting that messages may be lost.");
    return message.toString();
  }
}
