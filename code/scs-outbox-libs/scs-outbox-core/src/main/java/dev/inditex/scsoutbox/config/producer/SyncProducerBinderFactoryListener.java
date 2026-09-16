package dev.inditex.scsoutbox.config.producer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.producer.SyncProducerBinderRegistry.SyncProducerMapping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.binder.DefaultBinderFactory;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Enables synchronous publishing on every outbox-enabled producer binding, and fails fast when a binding is explicitly configured to
 * publish asynchronously.
 *
 * <p>scs-outbox deletes an outbox record as soon as {@code StreamBridge.send} returns {@code true}. With an asynchronous producer that
 * happens before the broker acknowledges the record, so any failure occurring afterwards silently loses the message. Requiring every
 * application to remember the binder-specific property is error-prone, hence this automatic configuration.
 *
 * <p><strong>Why a binder factory listener.</strong> The configuration is applied from
 * {@link DefaultBinderFactory.Listener#afterBinderContextInitialized(String, ConfigurableApplicationContext)}, which Spring Cloud Stream
 * invokes once the binder child context has been refreshed and before the binder is cached or used to create any binding. This is the only
 * place where the effective producer configuration is known, because:
 *
 * <ul> <li>binder-specific properties may be declared in the main environment <em>or</em> under
 * {@code spring.cloud.stream.binders.<name>.environment.*}, which is materialised only in the binder child context;</li> <li>frameworks
 * layered on top of Spring Boot may expose their own configuration namespace and relocate it into the {@code spring.cloud.stream.*} /
 * {@code scs-outbox.*} namespaces from an {@code EnvironmentPostProcessor} ordered at {@link Ordered#LOWEST_PRECEDENCE}. Reading the
 * environment earlier would observe none of those properties.</li> </ul>
 *
 * <p><strong>Precedence.</strong> A binding is never modified when the application declares the setting itself, either for that binding or
 * as a binder-wide default. When the declared value is not synchronous the context fails to start instead of being silently overridden.
 *
 * <p>Bindings served by another binder, bindings that cannot publish, and binders for which no synchronous mapping is known are left
 * untouched.
 *
 * <p>The listener resolves its collaborators lazily from the application context: when Spring Cloud Stream instantiates the binder factory,
 * {@code OutboxProperties} and {@code BindingServiceProperties} are not necessarily resolvable as constructor dependencies yet.
 *
 * <p><strong>Coexisting with other listeners.</strong> Spring Cloud Stream injects the listeners as a {@code Collection}, which Spring
 * materialises as a {@code LinkedHashSet}; the relative order therefore follows bean registration and is not influenced by {@link Ordered}.
 * A third-party listener that validates the producer configuration may consequently run first and reject a binding that this listener was
 * about to configure.
 */
@Slf4j
public class SyncProducerBinderFactoryListener implements DefaultBinderFactory.Listener, ApplicationContextAware, Ordered {

  private ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  public int getOrder() {
    // Best effort only: Spring Cloud Stream injects the listeners as a Collection, which Spring materialises as a LinkedHashSet and
    // therefore does not sort. Should that ever become a List, this makes bindings synchronous before another listener validates them.
    return Ordered.HIGHEST_PRECEDENCE;
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

    final Object binder = binderContext.getBean(org.springframework.cloud.stream.binder.Binder.class);
    if (!(binder instanceof final ExtendedPropertiesBinder<?, ?, ?> extendedPropertiesBinder)) {
      this.warnUnsupported(configurationName, binder.getClass().getName());
      return;
    }

    final Optional<SyncProducerMapping> mapping =
        SyncProducerBinderRegistry.findByDefaultsPrefix(extendedPropertiesBinder.getDefaultsPrefix());
    if (mapping.isEmpty()) {
      this.warnUnsupported(configurationName, extendedPropertiesBinder.getDefaultsPrefix());
      return;
    }

    this.configure(configurationName, binderContext.getEnvironment(), extendedPropertiesBinder, mapping.get(), outboxProperties);
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

      if (!SyncProducerBindings.isProducerBinding(bindingName, bindingProperties)
          || !outboxProperties.getBindings().matches(bindingName)
          || !isServedBy(configurationName, bindingProperties)) {
        continue;
      }

      final BeanWrapper producerProperties =
          PropertyAccessorFactory.forBeanPropertyAccess(binder.getExtendedProducerProperties(bindingName));
      if (mapping.requiredValue().equals(producerProperties.getPropertyValue(mapping.producerPropertyPath()))) {
        continue;
      }

      final String declaredValue = declaredValue(propertyBinder, mapping, bindingName);
      if (declaredValue != null) {
        violations.put(bindingName, declaredProperty(mapping, bindingName, propertyBinder) + "=" + declaredValue);
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
   * A binding is served by this binder when it does not name a different one. Bindings without an explicit binder fall back to the default
   * binder, which is the binder currently being initialised whenever a single binder is in use.
   */
  private static boolean isServedBy(final String configurationName, final BindingProperties bindingProperties) {
    final String declaredBinder = bindingProperties.getBinder();
    return declaredBinder == null || declaredBinder.isBlank() || declaredBinder.equals(configurationName);
  }

  /**
   * Returns the value declared by the application for the binding, or {@code null} when it declares none.
   *
   * <p>Both the binding-scoped key and the binder-wide default key are inspected. Spring Cloud Stream resolves binding-scoped entries over
   * binder-wide defaults regardless of property source ordering, so overriding a binding whose binder-wide default says otherwise would
   * silently discard an explicit decision of the application.
   */
  private static String declaredValue(final Binder propertyBinder, final SyncProducerMapping mapping, final String bindingName) {
    return propertyBinder
        .bind(SyncProducerBindings.propertyName(mapping.bindingProperty(bindingName)), Bindable.of(String.class))
        .orElseGet(() -> propertyBinder
            .bind(SyncProducerBindings.propertyName(mapping.binderDefaultProperty()), Bindable.of(String.class))
            .orElse(null));
  }

  private static String declaredProperty(final SyncProducerMapping mapping, final String bindingName, final Binder propertyBinder) {
    final String bindingProperty = mapping.bindingProperty(bindingName);
    if (propertyBinder.bind(SyncProducerBindings.propertyName(bindingProperty), Bindable.of(String.class)).isBound()) {
      return bindingProperty;
    }
    return mapping.binderDefaultProperty();
  }

  private void warnUnsupported(final String configurationName, final String binderDescription) {
    log.warn("scs-outbox cannot enable synchronous producers automatically for the bindings of binder [{}] ({})."
        + " Supported binders: {}."
        + " Configure synchronous publishing manually for those bindings, otherwise messages may be lost:"
        + " the outbox record is deleted as soon as StreamBridge.send returns true.",
        configurationName, binderDescription, SyncProducerBinderRegistry.supportedBinders());
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
        .append("Fix it by removing that property so scs-outbox can configure it, or opt out explicitly by either")
        .append(System.lineSeparator())
        .append("  - excluding the binding from the outbox via 'scs-outbox.bindings.exclusions', or")
        .append(System.lineSeparator())
        .append("  - setting 'scs-outbox.bindings.sync-producers.enabled=false', ")
        .append("accepting that messages may be lost.");
    return message.toString();
  }
}
