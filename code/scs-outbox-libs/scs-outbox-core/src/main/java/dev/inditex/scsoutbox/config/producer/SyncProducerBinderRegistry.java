package dev.inditex.scsoutbox.config.producer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of the binder-specific properties that enable synchronous publishing.
 *
 * <p>scs-outbox deletes an outbox record as soon as {@code StreamBridge.send} returns {@code true}. Asynchronous producers return
 * immediately, before the broker has acknowledged the record, so a delivery failure happening afterwards would silently lose the message.
 * Every binder exposes a different property to switch its producer into synchronous mode; this registry maps each supported binder to that
 * property.
 *
 * <p>Entries are keyed by the binder's {@link org.springframework.cloud.stream.binder.ExtendedBindingProperties#getDefaultsPrefix()
 * defaults prefix}, which the binder reports itself (for example {@code spring.cloud.stream.kafka.default}). Keying on the prefix avoids
 * having to infer a binder <em>type</em> from the binding, the default binder or the classpath, and it works unchanged for named binder
 * instances such as {@code kafka-pipe}.
 *
 * <p>Entries are intentionally expressed as plain property keys and JavaBean property paths so that scs-outbox-core does not need a
 * compile-time dependency on any binder implementation.
 */
public final class SyncProducerBinderRegistry {

  /**
   * Defaults prefix reported by the Apache Kafka binder.
   */
  public static final String KAFKA_DEFAULTS_PREFIX = "spring.cloud.stream.kafka.default";

  private static final Map<String, SyncProducerMapping> MAPPINGS = Map.of(
      KAFKA_DEFAULTS_PREFIX, new SyncProducerMapping(
          "kafka",
          KAFKA_DEFAULTS_PREFIX,
          "spring.cloud.stream.kafka.bindings.%s.producer.sync",
          "spring.cloud.stream.kafka.default.producer.sync",
          "sync",
          Boolean.TRUE));

  private SyncProducerBinderRegistry() {
  }

  /**
   * Returns the synchronous producer mapping for the binder reporting the given defaults prefix.
   *
   * @param defaultsPrefix the value of {@code ExtendedBindingProperties#getDefaultsPrefix()}; may be {@code null}
   * @return the mapping, or {@link Optional#empty()} when the binder is unknown or unsupported
   */
  public static Optional<SyncProducerMapping> findByDefaultsPrefix(final String defaultsPrefix) {
    if (defaultsPrefix == null || defaultsPrefix.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(MAPPINGS.get(defaultsPrefix));
  }

  /**
   * Returns the binder names for which scs-outbox can configure synchronous producers automatically.
   */
  public static Set<String> supportedBinders() {
    return MAPPINGS.values().stream().map(SyncProducerMapping::binderName).collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /**
   * Describes how to enable synchronous publishing for a given binder.
   *
   * @param binderName human readable binder name, used in log and error messages
   * @param defaultsPrefix the defaults prefix reported by the binder
   * @param bindingPropertyTemplate binding-scoped property key template, with a single {@code %s} placeholder for the binding name
   * @param binderDefaultProperty binder-wide default property key for the same setting
   * @param producerPropertyPath JavaBean property path of the setting on the binder-specific producer properties object
   * @param requiredValue the value that enables synchronous publishing
   */
  public record SyncProducerMapping(
      String binderName,
      String defaultsPrefix,
      String bindingPropertyTemplate,
      String binderDefaultProperty,
      String producerPropertyPath,
      Object requiredValue) {

    /**
     * Returns the binding-scoped property key for the given binding name.
     *
     * @param bindingName the Spring Cloud Stream binding name
     * @return the fully qualified property key
     */
    public String bindingProperty(final String bindingName) {
      return String.format(this.bindingPropertyTemplate, bindingName);
    }
  }
}
