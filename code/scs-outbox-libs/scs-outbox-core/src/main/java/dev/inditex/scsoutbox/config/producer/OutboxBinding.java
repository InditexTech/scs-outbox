package dev.inditex.scsoutbox.config.producer;

import java.util.Optional;

import dev.inditex.scsoutbox.config.producer.SyncProducerMappings.SyncProducerMapping;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;

/**
 * A Spring Cloud Stream producer binding managed by the outbox, ready to have synchronous publishing enforced on it.
 *
 * <p>Instances are only created for bindings that already passed outbox eligibility, so every method here assumes the binding is one
 * scs-outbox should configure.
 */
final class OutboxBinding {

  private final String bindingName;

  private final ExtendedPropertiesBinder<?, ?, ?> binder;

  private final SyncProducerMapping mapping;

  private final Binder propertyBinder;

  OutboxBinding(final String bindingName, final ExtendedPropertiesBinder<?, ?, ?> binder, final SyncProducerMapping mapping,
      final Binder propertyBinder) {
    this.bindingName = bindingName;
    this.binder = binder;
    this.mapping = mapping;
    this.propertyBinder = propertyBinder;
  }

  String name() {
    return this.bindingName;
  }

  /**
   * Enforces synchronous publishing on this binding.
   *
   * <p>Leaves it untouched if it already publishes synchronously, switches it to synchronous if the application declared nothing, or
   * reports a violation if the application explicitly declared it asynchronous.
   */
  SyncOutcome enforceSync() {
    if (this.isAlreadySynchronous()) {
      return SyncOutcome.alreadySynchronous();
    }

    final Optional<DeclaredSetting> declared = this.declaredSetting();
    if (declared.isPresent()) {
      return SyncOutcome.violation(declared.get());
    }

    this.applySync();
    return SyncOutcome.configured();
  }

  private boolean isAlreadySynchronous() {
    return this.mapping.requiredValue().equals(this.producerProperties().getPropertyValue(this.mapping.producerPropertyPath()));
  }

  private void applySync() {
    this.producerProperties().setPropertyValue(this.mapping.producerPropertyPath(), this.mapping.requiredValue());
  }

  /**
   * Wraps the binder-specific producer properties object (e.g. {@code KafkaProducerProperties}) for reflective access.
   *
   * <p>Reflection is used, instead of a binder-specific type, so this class (and the core module) does not need a compile-time dependency
   * on any concrete binder implementation; {@link SyncProducerMapping} only carries the JavaBean property path to read and write.
   */
  private BeanWrapper producerProperties() {
    return PropertyAccessorFactory.forBeanPropertyAccess(this.binder.getExtendedProducerProperties(this.bindingName));
  }

  /**
   * Returns the setting declared by the application for the binding, or {@link Optional#empty()} when it declares none.
   *
   * <p>The binding-scoped key is inspected first, then the binder-wide default key. Spring Cloud Stream resolves binding-scoped entries
   * over binder-wide defaults regardless of property source ordering, so overriding a binding whose binder-wide default says otherwise
   * would silently discard an explicit decision of the application.
   */
  private Optional<DeclaredSetting> declaredSetting() {
    final String bindingProperty = this.mapping.bindingProperty(this.bindingName);
    final BindResult<String> bindingResult = this.propertyBinder.bind(propertyName(bindingProperty), Bindable.of(String.class));
    if (bindingResult.isBound()) {
      return Optional.of(new DeclaredSetting(bindingProperty, bindingResult.get()));
    }

    final String defaultProperty = this.mapping.binderDefaultProperty();
    final BindResult<String> defaultResult = this.propertyBinder.bind(propertyName(defaultProperty), Bindable.of(String.class));
    return defaultResult.isBound() ? Optional.of(new DeclaredSetting(defaultProperty, defaultResult.get())) : Optional.empty();
  }

  /**
   * Adapts a property key into a {@link ConfigurationPropertyName}.
   *
   * <p>{@link ConfigurationPropertyName#of(CharSequence)} rejects characters that are legal in a binding name, such as the upper case
   * letters of {@code myProducer-out-0}. {@link ConfigurationPropertyName#adapt(CharSequence, char)} accepts them and still matches
   * properties declared in any of the relaxed forms supported by Spring Boot.
   */
  private static ConfigurationPropertyName propertyName(final String propertyKey) {
    return ConfigurationPropertyName.adapt(propertyKey, '.');
  }
}
