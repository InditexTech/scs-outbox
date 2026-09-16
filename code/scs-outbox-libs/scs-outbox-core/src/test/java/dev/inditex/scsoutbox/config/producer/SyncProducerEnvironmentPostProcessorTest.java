package dev.inditex.scsoutbox.config.producer;

import static dev.inditex.scsoutbox.config.producer.SyncProducerEnvironmentPostProcessor.PROPERTY_SOURCE_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

class SyncProducerEnvironmentPostProcessorTest {

  private static final String BOOK_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync";

  private static final String AUDIT_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.produce-audit-out-0.producer.sync";

  private static final String KAFKA_DEFAULT_SYNC_PROPERTY = "spring.cloud.stream.kafka.default.producer.sync";

  private final SyncProducerEnvironmentPostProcessor postProcessor =
      new SyncProducerEnvironmentPostProcessor(mock(Log.class));

  private static MockEnvironment environmentWithKafkaBinding(final String bindingName) {
    return new MockEnvironment()
        .withProperty("spring.cloud.stream.default-binder", "kafka")
        .withProperty("spring.cloud.stream.bindings." + bindingName + ".destination", bindingName + "-destination");
  }

  @Nested
  class Ordering {

    @Test
    void expect_to_run_after_configuration_data_is_loaded() {
      assertThat(SyncProducerEnvironmentPostProcessorTest.this.postProcessor.getOrder())
          .isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
    }
  }

  @Nested
  class Injection {

    @Test
    void when_single_outbox_binding_without_sync_configured_expect_sync_injected() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isTrue();
      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
    }

    @Test
    void when_several_outbox_bindings_expect_sync_injected_for_all_of_them() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("spring.cloud.stream.bindings.produce-audit-out-0.destination", "audit-destination");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
      assertThat(environment.getProperty(AUDIT_SYNC_PROPERTY)).isEqualTo("true");
    }

    @Test
    void when_binder_declared_on_the_binding_expect_sync_injected() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.bindings.produce-book-out-0.destination", "book-destination")
          .withProperty("spring.cloud.stream.bindings.produce-book-out-0.binder", "kafka");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
    }

    @Test
    void when_invoked_twice_expect_property_source_added_once() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);
      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().stream()
          .filter(source -> PROPERTY_SOURCE_NAME.equals(source.getName()))
          .count()).isOne();
    }

    @Test
    void when_no_bindings_declared_expect_no_property_source_added() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }
  }

  @Nested
  class OnlyOutboxManagedBindings {

    @Test
    void when_binding_is_excluded_expect_sync_not_injected() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("spring.cloud.stream.bindings.produce-audit-out-0.destination", "audit-destination")
          .withProperty("scs-outbox.bindings.exclusions[0]", "produce-audit-out-0");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
      assertThat(environment.getProperty(AUDIT_SYNC_PROPERTY)).isNull();
    }

    @Test
    void when_inclusions_declared_expect_sync_injected_only_for_included_bindings() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("spring.cloud.stream.bindings.produce-audit-out-0.destination", "audit-destination")
          .withProperty("scs-outbox.bindings.inclusions[0]", "produce-book-out-0");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
      assertThat(environment.getProperty(AUDIT_SYNC_PROPERTY)).isNull();
    }

    @Test
    void when_regex_inclusion_declared_expect_sync_injected_only_for_matching_bindings() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("spring.cloud.stream.bindings.consume-book-in-0.destination", "book-destination")
          .withProperty("scs-outbox.bindings.inclusions[0]", "regex:produce-.*");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
      assertThat(environment.getProperty("spring.cloud.stream.kafka.bindings.consume-book-in-0.producer.sync")).isNull();
    }

    @Test
    void when_regex_exclusion_declared_expect_sync_not_injected_for_matching_bindings() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("spring.cloud.stream.bindings.produce-audit-out-0.destination", "audit-destination")
          .withProperty("scs-outbox.bindings.exclusions[0]", "regex:produce-audit-.*");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
      assertThat(environment.getProperty(AUDIT_SYNC_PROPERTY)).isNull();
    }

    @Test
    void when_binding_declares_no_destination_expect_sync_not_injected() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka")
          .withProperty("spring.cloud.stream.bindings.consume-book-in-0.group", "book-group");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void when_binding_is_a_function_input_expect_sync_not_injected() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka")
          .withProperty("spring.cloud.stream.bindings.myConsumer-in-0.destination", "outbox")
          .withProperty("spring.cloud.stream.bindings.myConsumer-in-0.group", "outbox");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void when_binding_declares_a_consumer_group_expect_sync_not_injected() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka")
          .withProperty("spring.cloud.stream.bindings.legacy-input.destination", "outbox")
          .withProperty("spring.cloud.stream.bindings.legacy-input.group", "outbox");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }
  }

  @Nested
  class BindingNamesWithUpperCase {

    private static final String CAMEL_CASE_SYNC_PROPERTY =
        "spring.cloud.stream.kafka.bindings.myProducer-out-0.producer.sync";

    @Test
    void when_binding_name_contains_upper_case_expect_sync_injected() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka")
          .withProperty("spring.cloud.stream.bindings.myProducer-out-0.destination", "book-destination");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(CAMEL_CASE_SYNC_PROPERTY)).isEqualTo("true");
    }

    @Test
    void when_application_configures_a_binding_name_with_upper_case_expect_it_untouched() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka")
          .withProperty("spring.cloud.stream.bindings.myProducer-out-0.destination", "book-destination")
          .withProperty(CAMEL_CASE_SYNC_PROPERTY, "false");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
      assertThat(environment.getProperty(CAMEL_CASE_SYNC_PROPERTY)).isEqualTo("false");
    }
  }

  @Nested
  class ApplicationConfigurationPrecedence {

    @Test
    void when_application_sets_the_binding_property_to_true_expect_it_untouched() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty(BOOK_SYNC_PROPERTY, "true");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void when_application_sets_the_binding_property_to_false_expect_it_untouched() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty(BOOK_SYNC_PROPERTY, "false");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("false");
    }

    @Test
    void when_application_sets_the_binder_default_property_expect_binding_property_untouched() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "false");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isNull();
    }

    @Test
    void when_application_configures_only_one_binding_expect_the_other_one_still_injected() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("spring.cloud.stream.bindings.produce-audit-out-0.destination", "audit-destination")
          .withProperty(BOOK_SYNC_PROPERTY, "false");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("false");
      assertThat(environment.getProperty(AUDIT_SYNC_PROPERTY)).isEqualTo("true");
    }
  }

  @Nested
  class UnsupportedBinders {

    @Test
    void when_binder_is_not_supported_expect_nothing_injected() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "rabbit")
          .withProperty("spring.cloud.stream.bindings.produce-book-out-0.destination", "book-destination");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }

    @Test
    void when_binder_type_cannot_be_resolved_expect_nothing_injected() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.bindings.produce-book-out-0.destination", "book-destination");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
    }
  }

  @Nested
  class OptOut {

    @Test
    void when_sync_producers_disabled_expect_nothing_injected() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("scs-outbox.bindings.sync-producers.enabled", "false");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)).isFalse();
      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isNull();
    }

    @Test
    void when_sync_producers_explicitly_enabled_expect_sync_injected() {
      final MockEnvironment environment = environmentWithKafkaBinding("produce-book-out-0")
          .withProperty("scs-outbox.bindings.sync-producers.enabled", "true");

      SyncProducerEnvironmentPostProcessorTest.this.postProcessor.postProcessEnvironment(environment);

      assertThat(environment.getProperty(BOOK_SYNC_PROPERTY)).isEqualTo("true");
    }
  }
}
