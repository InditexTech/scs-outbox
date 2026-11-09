package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.inditex.scsoutbox.config.producer.SyncOutcome.Status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class OutboxBindingTest {

  private static final String BOOK_BINDING = "produce-book-out-0";

  private static final String BOOK_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync";

  private static final String KAFKA_DEFAULT_SYNC_PROPERTY = "spring.cloud.stream.kafka.default.producer.sync";

  private static KafkaLikeStubBinder kafkaBinder() {
    return new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX);
  }

  private static SyncProducerMappings.SyncProducerMapping kafkaMapping() {
    return SyncProducerMappings.findByDefaultsPrefix(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX).orElseThrow();
  }

  private static OutboxBinding binding(final String bindingName, final KafkaLikeStubBinder binder, final MockEnvironment environment) {
    return new OutboxBinding(bindingName, binder, kafkaMapping(), Binder.get(environment));
  }

  @Nested
  class EnforceSync {

    @Test
    void when_sync_is_not_configured_expect_producer_switched_to_synchronous_and_configured_outcome() {
      final KafkaLikeStubBinder binder = kafkaBinder();

      final SyncOutcome outcome = binding(BOOK_BINDING, binder, new MockEnvironment()).enforceSync();

      assertThat(outcome.status()).isEqualTo(Status.CONFIGURED);
      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
    }

    @Test
    void when_binding_name_contains_upper_case_expect_producer_switched_to_synchronous() {
      final KafkaLikeStubBinder binder = kafkaBinder();

      final SyncOutcome outcome = binding("myProducer-out-0", binder, new MockEnvironment()).enforceSync();

      assertThat(outcome.status()).isEqualTo(Status.CONFIGURED);
      assertThat(binder.getExtendedProducerProperties("myProducer-out-0").isSync()).isTrue();
    }

    @Test
    void when_producer_is_already_synchronous_expect_already_synchronous_outcome_and_no_change() {
      final KafkaLikeStubBinder binder = kafkaBinder();
      binder.getExtendedProducerProperties(BOOK_BINDING).setSync(true);
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "true");

      final SyncOutcome outcome = binding(BOOK_BINDING, binder, environment).enforceSync();

      assertThat(outcome.status()).isEqualTo(Status.ALREADY_SYNCHRONOUS);
      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
    }

    @Test
    void when_application_enables_sync_as_binder_default_expect_already_synchronous_outcome() {
      final KafkaLikeStubBinder binder = kafkaBinder();
      // A binder default of 'true' is already reflected in the producer properties bound by Spring Cloud Stream.
      binder.getExtendedProducerProperties(BOOK_BINDING).setSync(true);
      final MockEnvironment environment = new MockEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "true");

      final SyncOutcome outcome = binding(BOOK_BINDING, binder, environment).enforceSync();

      assertThat(outcome.status()).isEqualTo(Status.ALREADY_SYNCHRONOUS);
    }

    @Test
    void when_application_disables_sync_for_the_binding_expect_violation_outcome() {
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      final SyncOutcome outcome = binding(BOOK_BINDING, kafkaBinder(), environment).enforceSync();

      assertThat(outcome.status()).isEqualTo(Status.VIOLATION);
      assertThat(outcome.declaredSetting()).contains(new DeclaredSetting(BOOK_SYNC_PROPERTY, "false"));
    }

    @Test
    void when_application_disables_sync_as_binder_default_expect_violation_outcome() {
      final MockEnvironment environment = new MockEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "false");

      final SyncOutcome outcome = binding(BOOK_BINDING, kafkaBinder(), environment).enforceSync();

      assertThat(outcome.status()).isEqualTo(Status.VIOLATION);
      assertThat(outcome.declaredSetting()).contains(new DeclaredSetting(KAFKA_DEFAULT_SYNC_PROPERTY, "false"));
    }

    @Test
    void when_binding_scoped_and_binder_default_are_both_declared_expect_binding_scoped_setting_to_win() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty(BOOK_SYNC_PROPERTY, "false")
          .withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "true");

      final SyncOutcome outcome = binding(BOOK_BINDING, kafkaBinder(), environment).enforceSync();

      assertThat(outcome.status()).isEqualTo(Status.VIOLATION);
      assertThat(outcome.declaredSetting()).contains(new DeclaredSetting(BOOK_SYNC_PROPERTY, "false"));
    }
  }

  @Nested
  class Name {

    @Test
    void expect_name_returns_the_binding_name() {
      assertThat(binding(BOOK_BINDING, kafkaBinder(), new MockEnvironment()).name()).isEqualTo(BOOK_BINDING);
    }
  }
}
