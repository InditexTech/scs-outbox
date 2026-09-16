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
  class FindByBinderType {

    @Test
    void when_kafka_expect_sync_producer_property() {
      final SyncProducerMapping mapping = SyncProducerBinderRegistry.findByBinderType("kafka").orElseThrow();

      assertThat(mapping.binderType()).isEqualTo("kafka");
      assertThat(mapping.bindingProperty("produce-book-out-0"))
          .isEqualTo("spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync");
      assertThat(mapping.binderDefaultProperty()).isEqualTo("spring.cloud.stream.kafka.default.producer.sync");
      assertThat(mapping.requiredValue()).isEqualTo("true");
    }

    @Test
    void when_binder_type_has_different_case_expect_mapping_found() {
      assertThat(SyncProducerBinderRegistry.findByBinderType("KAFKA")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"rabbit", "pubsub", "solace", "unknown"})
    void when_unsupported_binder_expect_empty(final String binderType) {
      assertThat(SyncProducerBinderRegistry.findByBinderType(binderType)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void when_null_or_blank_binder_expect_empty(final String binderType) {
      assertThat(SyncProducerBinderRegistry.findByBinderType(binderType)).isEmpty();
    }
  }

  @Nested
  class SupportedBinderTypes {

    @Test
    void expect_kafka_only() {
      assertThat(SyncProducerBinderRegistry.supportedBinderTypes()).containsExactly("kafka");
    }
  }
}
