package dev.inditex.scsoutbox.config.producer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.producer.SyncProducerMappings.SyncProducerMapping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.binder.DefaultBinderFactory;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Configures synchronous producers for outbox-managed producer bindings.
 *
 * <p>The listener runs when a binder is initialized, so it can read the main and binder-child environments. Explicit asynchronous
 * configuration fails startup; missing configuration is applied automatically. Unsupported binders and bindings not managed by the outbox
 * are ignored. The order relative to other binder listeners is not guaranteed.
 */
@Slf4j
public class SyncProducerBinderListener implements DefaultBinderFactory.Listener, ApplicationContextAware {

  private ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  public void afterBinderContextInitialized(final String configurationName, final ConfigurableApplicationContext binderContext) {
    final OutboxProperties outboxProperties = this.applicationContext.getBean(OutboxProperties.class);
    if (!outboxProperties.getBindings().getSyncProducers().isEnabled()) {
      log.warn("Automatic synchronous producer configuration is disabled"
          + " ('scs-outbox.bindings.sync-producers.enabled=false')."
          + " The application is fully responsible for configuring synchronous producers on outbox-enabled bindings;"
          + " messages may be lost if a producer publishes asynchronously.");
      return;
    }

    final Object binder = resolveBinder(binderContext);
    if (!(binder instanceof final ExtendedPropertiesBinder<?, ?, ?> extendedPropertiesBinder)) {
      this.warnUnsupported(configurationName, binder.getClass().getName());
      return;
    }

    final Optional<SyncProducerMapping> mapping =
        SyncProducerMappings.findByDefaultsPrefix(extendedPropertiesBinder.getDefaultsPrefix());
    if (mapping.isEmpty()) {
      this.warnUnsupported(configurationName, extendedPropertiesBinder.getDefaultsPrefix());
      return;
    }

    this.configure(configurationName, binderContext.getEnvironment(), extendedPropertiesBinder, mapping.get(), outboxProperties);
  }

  /**
   * Resolves the Spring Cloud Stream {@code Binder} bean from the binder child context.
   *
   * <p>Fully qualified on purpose: {@code org.springframework.cloud.stream.binder.Binder} would otherwise collide with
   * {@link org.springframework.boot.context.properties.bind.Binder}, already imported and used throughout this class to resolve declared
   * properties.
   */
  private static Object resolveBinder(final ConfigurableApplicationContext binderContext) {
    return binderContext.getBean(org.springframework.cloud.stream.binder.Binder.class);
  }

  private void configure(final String configurationName, final Environment binderEnvironment,
      final ExtendedPropertiesBinder<?, ?, ?> binder, final SyncProducerMapping mapping, final OutboxProperties outboxProperties) {

    final BindingServiceProperties bindingServiceProperties = this.applicationContext.getBean(BindingServiceProperties.class);
    // The binder child environment inherits the main environment and adds 'spring.cloud.stream.binders.<name>.environment.*'.
    final Binder propertyBinder = Binder.get(binderEnvironment);

    final List<String> configured = new ArrayList<>();
    final Map<String, String> violations = new LinkedHashMap<>();

    for (final Map.Entry<String, BindingProperties> entry : bindingServiceProperties.getBindings().entrySet()) {
      final String bindingName = entry.getKey();
      final BindingProperties bindingProperties = entry.getValue();

      if (!isEligible(configurationName, outboxProperties, bindingName, bindingProperties)) {
        continue;
      }

      final BeanWrapper producerProperties =
          PropertyAccessorFactory.forBeanPropertyAccess(binder.getExtendedProducerProperties(bindingName));
      if (mapping.requiredValue().equals(producerProperties.getPropertyValue(mapping.producerPropertyPath()))) {
        continue;
      }

      final Optional<DeclaredSetting> declared = declaredSetting(propertyBinder, mapping, bindingName);
      if (declared.isPresent()) {
        violations.put(bindingName, declared.get().property() + "=" + declared.get().value());
      } else {
        producerProperties.setPropertyValue(mapping.producerPropertyPath(), mapping.requiredValue());
        configured.add(bindingName);
      }
    }

    if (!configured.isEmpty()) {
      log.info("Enabled synchronous publishing for outbox-enabled bindings of binder [{}]: {}", configurationName, configured);
    }

    if (!violations.isEmpty()) {
      throw new IllegalStateException(buildViolationMessage(configurationName, violations));
    }
  }

  /**
   * A binding is eligible for automatic configuration when it can publish messages, it is managed by the outbox, and it is served by the
   * binder currently being initialised.
   */
  private static boolean isEligible(final String configurationName, final OutboxProperties outboxProperties, final String bindingName,
      final BindingProperties bindingProperties) {
    return SyncProducerBindings.isProducerBinding(bindingName, bindingProperties)
        && outboxProperties.getBindings().matches(bindingName)
        && isServedBy(configurationName, bindingProperties);
  }

  /**
   * A binding is served by this binder when it does not name a different one. Bindings without an explicit binder fall back to the default
   * binder, which is the binder currently being initialised whenever a single binder is in use.
   */
  private static boolean isServedBy(final String configurationName, final BindingProperties bindingProperties) {
    final String declaredBinder = bindingProperties.getBinder();
    return declaredBinder == null || declaredBinder.isBlank() || declaredBinder.equals(configurationName);
  }

  /**
   * The property key and value the application declared for a binding, either at the binding-scoped key or, failing that, at the
   * binder-wide default key.
   */
  private record DeclaredSetting(String property, String value) {
  }

  /**
   * Returns the setting declared by the application for the binding, or {@link Optional#empty()} when it declares none.
   *
   * <p>The binding-scoped key is inspected first, then the binder-wide default key. Spring Cloud Stream resolves binding-scoped entries
   * over binder-wide defaults regardless of property source ordering, so overriding a binding whose binder-wide default says otherwise
   * would silently discard an explicit decision of the application.
   */
  private static Optional<DeclaredSetting> declaredSetting(final Binder propertyBinder, final SyncProducerMapping mapping,
      final String bindingName) {
    final String bindingProperty = mapping.bindingProperty(bindingName);
    final BindResult<String> bindingResult =
        propertyBinder.bind(SyncProducerBindings.propertyName(bindingProperty), Bindable.of(String.class));
    if (bindingResult.isBound()) {
      return Optional.of(new DeclaredSetting(bindingProperty, bindingResult.get()));
    }

    final String defaultProperty = mapping.binderDefaultProperty();
    final BindResult<String> defaultResult =
        propertyBinder.bind(SyncProducerBindings.propertyName(defaultProperty), Bindable.of(String.class));
    return defaultResult.isBound() ? Optional.of(new DeclaredSetting(defaultProperty, defaultResult.get())) : Optional.empty();
  }

  private void warnUnsupported(final String configurationName, final String binderDescription) {
    log.warn("scs-outbox cannot enable synchronous producers automatically for the bindings of binder [{}] ({})."
        + " Supported binders: {}."
        + " Configure synchronous publishing manually for those bindings, otherwise messages may be lost:"
        + " the outbox record is deleted as soon as StreamBridge.send returns true.",
        configurationName, binderDescription, SyncProducerMappings.supportedBinders());
  }

  private static String buildViolationMessage(final String configurationName, final Map<String, String> violations) {
    final StringBuilder message = new StringBuilder()
        .append("The following outbox-enabled bindings of binder [").append(configurationName)
        .append("] are explicitly configured to publish asynchronously, ")
        .append("which breaks the delivery guarantee of the transactional outbox ")
        .append("(the outbox record is deleted as soon as StreamBridge.send returns true, ")
        .append("which for an asynchronous producer happens before the broker acknowledges the record):");
    violations.forEach((binding, detail) -> message.append(System.lineSeparator())
        .append("  - binding '").append(binding).append("': ").append(detail));
    message.append(System.lineSeparator())
        .append("Fix it by removing that property so scs-outbox can configure it, or, if this binding must genuinely publish")
        .append(
            " asynchronously, exclude it from the outbox via 'scs-outbox.bindings.exclusions' so it is no longer managed by scs-outbox.");
    return message.toString();
  }
}
