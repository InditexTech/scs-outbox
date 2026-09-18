package dev.inditex.scsoutbox.config.producer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.producer.SyncProducerMappings.SyncProducerMapping;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.util.StringUtils;

/**
 * A binder that scs-outbox knows how to enforce synchronous publishing on.
 *
 * <p>Instances are only created by {@link OutboxBindingsContext#supportFor} for binders it recognises, so every method here assumes the
 * mapping between binder and required property is already resolved.
 */
final class SupportedBinder {

  private final String binderConfigurationName;

  private final ExtendedPropertiesBinder<?, ?, ?> binder;

  private final SyncProducerMapping mapping;

  private final Binder propertyBinder;

  private final OutboxProperties outboxProperties;

  private final BindingServiceProperties bindingServiceProperties;

  private final InputBindings inputBindings;

  SupportedBinder(final String binderConfigurationName, final ExtendedPropertiesBinder<?, ?, ?> binder, final SyncProducerMapping mapping,
      final Binder propertyBinder, final OutboxProperties outboxProperties, final BindingServiceProperties bindingServiceProperties,
      final InputBindings inputBindings) {
    this.binderConfigurationName = binderConfigurationName;
    this.binder = binder;
    this.mapping = mapping;
    this.propertyBinder = propertyBinder;
    this.outboxProperties = outboxProperties;
    this.bindingServiceProperties = bindingServiceProperties;
    this.inputBindings = inputBindings;
  }

  /** The Spring Cloud Stream binder configuration name this instance was resolved for. */
  String configurationName() {
    return this.binderConfigurationName;
  }

  /**
   * The outbox-enabled producer bindings of this binder, ready to have synchronous publishing enforced on them.
   *
   * <p>A binding is included when it can publish messages, it is managed by the outbox, and it is served by this binder.
   */
  List<OutboxBinding> getBindings() {
    final List<OutboxBinding> bindings = new ArrayList<>();
    for (final Map.Entry<String, BindingProperties> entry : this.bindingServiceProperties.getBindings().entrySet()) {
      final String bindingName = entry.getKey();
      final BindingProperties bindingProperties = entry.getValue();

      if (isProducerBinding(bindingName, bindingProperties)
          && this.outboxProperties.getBindings().matches(bindingName)
          && this.belongsToCurrentBinder(bindingProperties)) {
        bindings.add(new OutboxBinding(bindingName, this.binder, this.mapping, this.propertyBinder));
      }
    }
    return bindings;
  }

  /** Determines whether the binding belongs to this binder configuration. */
  private boolean belongsToCurrentBinder(final BindingProperties bindingProperties) {
    final String explicitlyAssignedBinder = bindingProperties.getBinder();
    if (hasText(explicitlyAssignedBinder)) {
      return this.binderConfigurationName.equals(explicitlyAssignedBinder);
    }

    return this.currentBinderIsEffectiveDefault();
  }

  private boolean currentBinderIsEffectiveDefault() {
    final String explicitlyConfiguredDefaultBinder = this.bindingServiceProperties.getDefaultBinder();
    if (hasText(explicitlyConfiguredDefaultBinder)) {
      return this.binderConfigurationName.equals(explicitlyConfiguredDefaultBinder);
    }

    final List<String> defaultCandidateNames = this.defaultCandidateNames();
    if (defaultCandidateNames.isEmpty()) {
      return this.currentBinderIsImplicitlyDiscovered();
    }
    if (defaultCandidateNames.size() > 1) {
      return false;
    }

    return this.binderConfigurationName.equals(defaultCandidateNames.get(0));
  }

  private List<String> defaultCandidateNames() {
    final Map<String, BinderProperties> declaredBinderConfigurations = this.bindingServiceProperties.getBinders();
    if (declaredBinderConfigurations == null) {
      return List.of();
    }

    return declaredBinderConfigurations.entrySet().stream()
        .filter(entry -> entry.getValue() != null && entry.getValue().isDefaultCandidate())
        .map(Map.Entry::getKey)
        .toList();
  }

  private boolean currentBinderIsImplicitlyDiscovered() {
    final Map<String, BinderProperties> declaredBinderConfigurations = this.bindingServiceProperties.getBinders();
    if (declaredBinderConfigurations == null || declaredBinderConfigurations.isEmpty()) {
      return true;
    }

    final boolean currentBinderWasDeclared = declaredBinderConfigurations.containsKey(this.binderConfigurationName);
    return !currentBinderWasDeclared;
  }

  private static boolean hasText(final String value) {
    return value != null && !value.isBlank();
  }

  /**
   * Returns {@code true} when the binding may be used to publish messages, and is therefore a candidate for synchronous producer
   * configuration.
   *
   * <p>A binding is discarded when it declares no destination (nothing would be published), when its name follows the Spring Cloud Stream
   * convention for function inputs, or when it is a {@linkplain #isConsumerOnlyBinding consumer-only binding}.
   */
  private boolean isProducerBinding(final String bindingName, final BindingProperties bindingProperties) {
    if (bindingProperties == null || bindingProperties.getDestination() == null || bindingProperties.getDestination().isBlank()) {
      return false;
    }
    if (this.inputBindings.isInputBinding(bindingName) && !this.isExplicitOutputBinding(bindingName)) {
      return false;
    }
    return !isConsumerOnlyBinding(bindingProperties);
  }

  private boolean isExplicitOutputBinding(final String bindingName) {
    final String outputBindings = this.bindingServiceProperties.getOutputBindings();
    return StringUtils.hasText(outputBindings)
        && Arrays.stream(StringUtils.tokenizeToStringArray(outputBindings, ";"))
            .anyMatch(bindingName::equals);
  }

  /**
   * A binding only declares consumer settings when it has no producer configuration and belongs to a consumer group. Contributing producer
   * properties to such a binding would be harmless for the binder but would make scs-outbox report it as a violation.
   */
  private static boolean isConsumerOnlyBinding(final BindingProperties bindingProperties) {
    return bindingProperties.getProducer() == null && bindingProperties.getGroup() != null;
  }
}
