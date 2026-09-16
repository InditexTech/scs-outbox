package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.inditex.scsoutbox.config.producer.SyncProducerBinderRegistry.SyncProducerMapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SyncProducerBinderRegistryTest {

  @Nested
  class FindByDefaultsPrefix {

    @Test
    void when_kafka_expect_sync_producer_mapping() {
      final SyncProducerMapping mapping =
          SyncProducerBinderRegistry.findByDefaultsPrefix("spring.cloud.stream.kafka.default").orElseThrow();

      assertThat(mapping.binderName()).isEqualTo("kafka");
      assertThat(mapping.bindingProperty("produce-book-out-0"))
          .isEqualTo("spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync");
      assertThat(mapping.binderDefaultProperty()).isEqualTo("spring.cloud.stream.kafka.default.producer.sync");
      assertThat(mapping.producerPropertyPath()).isEqualTo("sync");
      assertThat(mapping.requiredValue()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void when_binding_name_contains_upper_case_expect_it_preserved() {
      final SyncProducerMapping mapping =
          SyncProducerBinderRegistry.findByDefaultsPrefix(SyncProducerBinderRegistry.KAFKA_DEFAULTS_PREFIX).orElseThrow();

      assertThat(mapping.bindingProperty("myProducer-out-0"))
          .isEqualTo("spring.cloud.stream.kafka.bindings.myProducer-out-0.producer.sync");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "spring.cloud.stream.rabbit.default",
        "spring.cloud.stream.pubsub.default",
        "spring.cloud.stream.kafka",
        "unknown"})
    void when_unsupported_binder_expect_empty(final String defaultsPrefix) {
      assertThat(SyncProducerBinderRegistry.findByDefaultsPrefix(defaultsPrefix)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void when_null_or_blank_expect_empty(final String defaultsPrefix) {
      assertThat(SyncProducerBinderRegistry.findByDefaultsPrefix(defaultsPrefix)).isEmpty();
    }
  }

  @Nested
  class SupportedBinders {

    @Test
    void expect_kafka_only() {
      assertThat(SyncProducerBinderRegistry.supportedBinders()).containsExactly("kafka");
    }
  }
}
