package dev.inditex.scsoutbox.config.producer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Binder-specific mappings used to configure synchronous producers.
 *
 * <p>Mappings are keyed by the defaults prefix reported by each binder and use property names and JavaBean paths, so the core module does
 * not depend on a concrete binder implementation.
 */
final class SyncProducerMappings {

  /** Defaults prefix reported by the Apache Kafka binder. */
  static final String KAFKA_DEFAULTS_PREFIX = "spring.cloud.stream.kafka.default";

  private static final Map<String, SyncProducerMapping> MAPPINGS = Map.of(
      KAFKA_DEFAULTS_PREFIX, new SyncProducerMapping(
          "kafka",
          "spring.cloud.stream.kafka.bindings.%s.producer.sync",
          "spring.cloud.stream.kafka.default.producer.sync",
          "sync",
          Boolean.TRUE));

  private SyncProducerMappings() {
  }

  /** Finds the mapping for a binder defaults prefix. */
  static Optional<SyncProducerMapping> findByDefaultsPrefix(final String defaultsPrefix) {
    if (defaultsPrefix == null || defaultsPrefix.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(MAPPINGS.get(defaultsPrefix));
  }

  /** Returns the binder names supported by automatic synchronous producer configuration. */
  static Set<String> supportedBinders() {
    return MAPPINGS.values().stream().map(SyncProducerMapping::binderName).collect(Collectors.toUnmodifiableSet());
  }

  /** Describes the binder-specific setting required for synchronous publishing. */
  record SyncProducerMapping(
      String binderName,
      String bindingPropertyTemplate,
      String binderDefaultProperty,
      String producerPropertyPath,
      Object requiredValue) {

    /** Builds the binding-scoped property name. */
    String bindingProperty(final String bindingName) {
      return String.format(this.bindingPropertyTemplate, bindingName);
    }
  }
}
