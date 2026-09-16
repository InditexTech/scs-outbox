package dev.inditex.scsoutbox.config.producer;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of the binder-specific properties that enable synchronous publishing.
 *
 * <p>scs-outbox deletes an outbox record as soon as {@code StreamBridge.send} returns {@code true}. Asynchronous producers return
 * immediately, before the broker has acknowledged the record, so a delivery failure happening afterwards would silently lose the message.
 * Every binder exposes a different property to switch its producer into synchronous mode; this registry maps each supported binder type to
 * that property.
 *
 * <p>Entries are intentionally expressed as plain property keys and string values so that scs-outbox-core does not need a compile-time
 * dependency on any binder implementation.
 */
public final class SyncProducerBinderRegistry {

  /**
   * Spring Cloud Stream binder type identifier for the Apache Kafka binder, as declared in its {@code META-INF/spring.binders} descriptor.
   */
  public static final String KAFKA_BINDER_TYPE = "kafka";

  private static final Map<String, SyncProducerMapping> MAPPINGS = Map.of(
      KAFKA_BINDER_TYPE, new SyncProducerMapping(
          KAFKA_BINDER_TYPE,
          "spring.cloud.stream.kafka.bindings.%s.producer.sync",
          "spring.cloud.stream.kafka.default.producer.sync",
          "true"));

  private SyncProducerBinderRegistry() {
  }

  /**
   * Returns the synchronous producer mapping for the given binder type, if scs-outbox knows how to configure it.
   *
   * @param binderType the Spring Cloud Stream binder type (for example {@code kafka}); may be {@code null}
   * @return the mapping, or {@link Optional#empty()} when the binder type is unknown or unsupported
   */
  public static Optional<SyncProducerMapping> findByBinderType(final String binderType) {
    if (binderType == null || binderType.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(MAPPINGS.get(binderType.toLowerCase(java.util.Locale.ROOT)));
  }

  /**
   * Returns the binder types for which scs-outbox can configure synchronous producers automatically.
   */
  public static Set<String> supportedBinderTypes() {
    return MAPPINGS.keySet();
  }

  /**
   * Describes how to enable synchronous publishing for a given binder.
   *
   * @param binderType the Spring Cloud Stream binder type
   * @param bindingPropertyTemplate binding-scoped property key template, with a single {@code %s} placeholder for the binding name
   * @param binderDefaultProperty binder-wide default property key for the same setting
   * @param requiredValue the value that enables synchronous publishing
   */
  public record SyncProducerMapping(
      String binderType,
      String bindingPropertyTemplate,
      String binderDefaultProperty,
      String requiredValue) {

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
