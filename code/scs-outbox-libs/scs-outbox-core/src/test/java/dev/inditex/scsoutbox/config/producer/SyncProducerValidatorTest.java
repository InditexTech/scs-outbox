package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;
import dev.inditex.scsoutbox.config.OutboxProperties.SyncProducers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class SyncProducerValidatorTest {

  private static final String BOOK_BINDING = "produce-book-out-0";

  private static final String BOOK_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync";

  private static final String KAFKA_DEFAULT_SYNC_PROPERTY = "spring.cloud.stream.kafka.default.producer.sync";

  private static MockEnvironment kafkaEnvironment() {
    return new MockEnvironment().withProperty("spring.cloud.stream.default-binder", "kafka");
  }

  private static BindingServiceProperties bindingServiceProperties(final Map<String, String> bindingToDestination) {
    final BindingServiceProperties properties = new BindingServiceProperties();
    bindingToDestination.forEach((binding, destination) -> {
      final BindingProperties bindingProperties = new BindingProperties();
      bindingProperties.setDestination(destination);
      properties.getBindings().put(binding, bindingProperties);
    });
    return properties;
  }

  private static SyncProducerValidator validator(final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties, final Environment environment) {
    return new SyncProducerValidator(outboxProperties, bindingServiceProperties, environment);
  }

  private static OutboxProperties outboxProperties() {
    return new OutboxProperties(new Bindings(List.of(), List.of()));
  }

  @Nested
  class SupportedBinder {

    @Test
    void when_binding_property_enables_sync_expect_no_failure() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(BOOK_SYNC_PROPERTY, "true");

      assertThatCode(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .doesNotThrowAnyException();
    }

    @Test
    void when_binder_default_property_enables_sync_expect_no_failure() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "true");

      assertThatCode(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .doesNotThrowAnyException();
    }

    @Test
    void when_binding_property_disables_sync_expect_failure() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      assertThatThrownBy(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining(BOOK_BINDING)
              .hasMessageContaining(BOOK_SYNC_PROPERTY + "=false")
              .hasMessageContaining("scs-outbox.bindings.exclusions")
              .hasMessageContaining("scs-outbox.bindings.sync-producers.enabled=false");
    }

    @Test
    void when_binder_default_property_disables_sync_expect_failure() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "false");

      assertThatThrownBy(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining(BOOK_BINDING);
    }

    @Test
    void when_binding_property_overrides_a_disabled_binder_default_expect_no_failure() {
      final MockEnvironment environment = kafkaEnvironment()
          .withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "false")
          .withProperty(BOOK_SYNC_PROPERTY, "true");

      assertThatCode(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .doesNotThrowAnyException();
    }

    @Test
    void when_sync_is_not_configured_at_all_expect_no_failure() {
      assertThatCode(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), kafkaEnvironment()).afterPropertiesSet())
              .doesNotThrowAnyException();
    }

    @Test
    void when_several_bindings_violate_expect_all_of_them_reported() {
      final MockEnvironment environment = kafkaEnvironment()
          .withProperty(BOOK_SYNC_PROPERTY, "false")
          .withProperty("spring.cloud.stream.kafka.bindings.produce-audit-out-0.producer.sync", "false");

      assertThatThrownBy(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination", "produce-audit-out-0", "audit-destination")),
          environment).afterPropertiesSet())
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining(BOOK_BINDING)
              .hasMessageContaining("produce-audit-out-0");
    }
  }

  @Nested
  class OnlyOutboxManagedBindings {

    @Test
    void when_violating_binding_is_excluded_expect_no_failure() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");
      final OutboxProperties outboxProperties = new OutboxProperties(new Bindings(List.of(), List.of(BOOK_BINDING)));

      assertThatCode(() -> validator(outboxProperties,
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .doesNotThrowAnyException();
    }

    @Test
    void when_violating_binding_is_not_included_expect_no_failure() {
      final MockEnvironment environment = kafkaEnvironment()
          .withProperty(BOOK_SYNC_PROPERTY, "false")
          .withProperty("spring.cloud.stream.kafka.bindings.produce-audit-out-0.producer.sync", "true");
      final OutboxProperties outboxProperties = new OutboxProperties(new Bindings(List.of("produce-audit-out-0"), List.of()));
      final BindingServiceProperties bindings =
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination", "produce-audit-out-0", "audit-destination"));

      assertThatCode(() -> validator(outboxProperties, bindings, environment).afterPropertiesSet())
          .doesNotThrowAnyException();
    }

    @Test
    void when_binding_declares_no_destination_expect_it_ignored() {
      final BindingServiceProperties bindingServiceProperties = new BindingServiceProperties();
      final BindingProperties consumerOnly = new BindingProperties();
      consumerOnly.setGroup("book-group");
      bindingServiceProperties.getBindings().put("consume-book-in-0", consumerOnly);

      assertThatCode(() -> validator(outboxProperties(), bindingServiceProperties, kafkaEnvironment()).afterPropertiesSet())
          .doesNotThrowAnyException();
    }

    @Test
    void when_binding_is_a_function_input_expect_it_ignored() {
      final BindingServiceProperties bindingServiceProperties = new BindingServiceProperties();
      final BindingProperties consumer = new BindingProperties();
      consumer.setDestination("outbox");
      consumer.setGroup("outbox");
      bindingServiceProperties.getBindings().put("myConsumer-in-0", consumer);
      final MockEnvironment environment = kafkaEnvironment()
          .withProperty("spring.cloud.stream.kafka.bindings.myConsumer-in-0.producer.sync", "false");

      assertThatCode(() -> validator(outboxProperties(), bindingServiceProperties, environment).afterPropertiesSet())
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class BindingNamesWithUpperCase {

    private static final String CAMEL_CASE_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.myProducer-out-0.producer.sync";

    @Test
    void when_binding_name_contains_upper_case_and_sync_is_enabled_expect_no_failure() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(CAMEL_CASE_SYNC_PROPERTY, "true");

      assertThatCode(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of("myProducer-out-0", "book-destination")), environment).afterPropertiesSet())
              .doesNotThrowAnyException();
    }

    @Test
    void when_binding_name_contains_upper_case_and_sync_is_disabled_expect_failure() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(CAMEL_CASE_SYNC_PROPERTY, "false");

      assertThatThrownBy(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of("myProducer-out-0", "book-destination")), environment).afterPropertiesSet())
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("myProducer-out-0");
    }
  }

  @Nested
  class UnsupportedBinder {

    @Test
    void when_binder_is_not_supported_expect_no_failure() {
      final MockEnvironment environment = new MockEnvironment().withProperty("spring.cloud.stream.default-binder", "rabbit");

      assertThatCode(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .doesNotThrowAnyException();
    }

    @Test
    void when_binder_type_cannot_be_resolved_expect_no_failure() {
      assertThatCode(() -> validator(outboxProperties(),
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), new MockEnvironment()).afterPropertiesSet())
              .doesNotThrowAnyException();
    }
  }

  @Nested
  class OptOut {

    @Test
    void when_sync_producers_disabled_expect_no_failure_even_with_an_async_binding() {
      final MockEnvironment environment = kafkaEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");
      final OutboxProperties outboxProperties =
          new OutboxProperties(new Bindings(List.of(), List.of(), new SyncProducers(false)));

      assertThatCode(() -> validator(outboxProperties,
          bindingServiceProperties(Map.of(BOOK_BINDING, "book-destination")), environment).afterPropertiesSet())
              .doesNotThrowAnyException();
    }
  }
}
