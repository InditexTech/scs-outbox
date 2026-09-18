package dev.inditex.scsoutbox.config.producer;

import java.util.Optional;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.producer.SyncProducerMappings.SyncProducerMapping;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.env.Environment;

/**
 * Single point of contact between {@link SyncProducerBinderListener} and the outbox configuration: whether the automatic synchronous
 * producer feature is enabled, and, for each binder being initialised, whether scs-outbox knows how to configure it.
 */
public class OutboxBindingsContext {

  private final OutboxProperties outboxProperties;

  private final BindingServiceProperties bindingServiceProperties;

  public OutboxBindingsContext(final OutboxProperties outboxProperties, final BindingServiceProperties bindingServiceProperties) {
    this.outboxProperties = outboxProperties;
    this.bindingServiceProperties = bindingServiceProperties;
  }

  /** Whether scs-outbox should automatically configure synchronous producers at all. */
  public boolean isSyncProducerAutoConfigurationEnabled() {
    return this.outboxProperties.getBindings().getSyncProducers().isEnabled();
  }

  /**
   * Resolves the given binder into a {@link SupportedBinder}, or {@link Optional#empty()} when scs-outbox does not know how to configure
   * synchronous producers for it (it is not an {@link ExtendedPropertiesBinder}, or its defaults prefix is not one of the
   * {@linkplain SyncProducerMappings#supportedBinders() supported binders}).
   *
   * @param binderConfigurationName the binder configuration name
   * @param binder the raw {@code org.springframework.cloud.stream.binder.Binder} bean resolved from the binder child context
   * @param binderEnvironment the environment of the binder child context, used to detect settings the application declared explicitly
   */
  public Optional<SupportedBinder> supportFor(final String binderConfigurationName, final Object binder,
      final Environment binderEnvironment) {
    if (!(binder instanceof final ExtendedPropertiesBinder<?, ?, ?> extendedPropertiesBinder)) {
      return Optional.empty();
    }

    // The binder child environment inherits the main environment and adds 'spring.cloud.stream.binders.<name>.environment.*'.
    return SyncProducerMappings.findByDefaultsPrefix(extendedPropertiesBinder.getDefaultsPrefix())
        .flatMap(mapping -> this.supportFor(binderConfigurationName, extendedPropertiesBinder, mapping, binderEnvironment,
            InputBindings.conventionalNamesOnly()));
  }

  Optional<SupportedBinder> supportFor(final String binderConfigurationName, final Object binder, final Environment binderEnvironment,
      final InputBindings inputBindings) {
    if (!(binder instanceof final ExtendedPropertiesBinder<?, ?, ?> extendedPropertiesBinder)) {
      return Optional.empty();
    }

    return SyncProducerMappings.findByDefaultsPrefix(extendedPropertiesBinder.getDefaultsPrefix())
        .flatMap(mapping -> this.supportFor(binderConfigurationName, extendedPropertiesBinder, mapping, binderEnvironment, inputBindings));
  }

  private Optional<SupportedBinder> supportFor(final String binderConfigurationName,
      final ExtendedPropertiesBinder<?, ?, ?> binder, final SyncProducerMapping mapping, final Environment binderEnvironment,
      final InputBindings inputBindings) {
    return Optional.of(new SupportedBinder(binderConfigurationName, binder, mapping, Binder.get(binderEnvironment), this.outboxProperties,
        this.bindingServiceProperties, inputBindings));
  }
}
