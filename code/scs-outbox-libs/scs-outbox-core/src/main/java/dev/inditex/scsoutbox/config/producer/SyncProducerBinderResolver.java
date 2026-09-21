package dev.inditex.scsoutbox.config.producer;

import java.util.Optional;

import dev.inditex.scsoutbox.config.OutboxProperties;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.env.Environment;

/**
 * Resolves initialized binders that scs-outbox can configure for synchronous publishing.
 *
 * <p>Also exposes whether automatic synchronous producer configuration is enabled.
 */
public class SyncProducerBinderResolver {

  private final OutboxProperties outboxProperties;

  private final BindingServiceProperties bindingServiceProperties;

  public SyncProducerBinderResolver(final OutboxProperties outboxProperties, final BindingServiceProperties bindingServiceProperties) {
    this.outboxProperties = outboxProperties;
    this.bindingServiceProperties = bindingServiceProperties;
  }

  /** Whether scs-outbox should automatically configure synchronous producers at all. */
  boolean isSyncProducerAutoConfigurationEnabled() {
    return this.outboxProperties.getBindings().getSyncProducers().isEnabled();
  }

  /**
   * Resolves the given binder into a {@link SupportedBinder}, or {@link Optional#empty()} when scs-outbox does not know how to configure
   * synchronous producers for it (it is not an {@link ExtendedPropertiesBinder}, or its defaults prefix is not one of the binders supported
   * by scs-outbox).
   *
   * @param binderConfigurationName the binder configuration name
   * @param binder the raw {@code org.springframework.cloud.stream.binder.Binder} bean resolved from the binder child context
   * @param binderEnvironment the environment of the binder child context, used to detect settings the application declared explicitly
   * @param inputBindings the input binding names resolved by Spring Cloud Stream for the application
   */
  Optional<SupportedBinder> resolve(final String binderConfigurationName, final Object binder, final Environment binderEnvironment,
      final InputBindings inputBindings) {
    if (!(binder instanceof final ExtendedPropertiesBinder<?, ?, ?> extendedPropertiesBinder)) {
      return Optional.empty();
    }

    // The binder child environment inherits the main environment and adds 'spring.cloud.stream.binders.<name>.environment.*'.
    return SyncProducerMappings.findByDefaultsPrefix(extendedPropertiesBinder.getDefaultsPrefix())
        .map(mapping -> new SupportedBinder(binderConfigurationName, extendedPropertiesBinder, mapping, Binder.get(binderEnvironment),
            this.outboxProperties, this.bindingServiceProperties, inputBindings));
  }
}
