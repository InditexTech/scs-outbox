package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;
import dev.inditex.scsoutbox.config.OutboxProperties.SyncProducers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.mock.env.MockEnvironment;

class SyncProducerBinderResolverTest {

  private static final String BINDER_NAME = "named-kafka";

  private static SyncProducerBinderResolver resolver(final OutboxProperties outboxProperties) {
    return new SyncProducerBinderResolver(outboxProperties, mock(BindingServiceProperties.class));
  }

  private static OutboxProperties outboxProperties() {
    return new OutboxProperties(new Bindings(List.of(), List.of()));
  }

  @Nested
  class IsSyncProducerAutoConfigurationEnabled {

    @Test
    void when_not_configured_expect_enabled_by_default() {
      assertThat(resolver(outboxProperties()).isSyncProducerAutoConfigurationEnabled()).isTrue();
    }

    @Test
    void when_explicitly_disabled_expect_disabled() {
      final OutboxProperties outboxProperties = new OutboxProperties(new Bindings(List.of(), List.of(), new SyncProducers(false)));

      assertThat(resolver(outboxProperties).isSyncProducerAutoConfigurationEnabled()).isFalse();
    }
  }

  @Nested
  class Resolve {

    @Test
    void when_binder_is_not_an_extended_properties_binder_expect_empty() {
      final Binder<?, ?, ?> plainBinder = mock(Binder.class);

      final Optional<SupportedBinder> supportedBinder = resolver(outboxProperties())
          .resolve(BINDER_NAME, plainBinder, new MockEnvironment(), InputBindings.conventionalNamesOnly());

      assertThat(supportedBinder).isEmpty();
    }

    @Test
    void when_binder_defaults_prefix_is_unknown_expect_empty() {
      final KafkaLikeStubBinder binder = new KafkaLikeStubBinder("spring.cloud.stream.rabbit.default");

      final Optional<SupportedBinder> supportedBinder = resolver(outboxProperties())
          .resolve(BINDER_NAME, binder, new MockEnvironment(), InputBindings.conventionalNamesOnly());

      assertThat(supportedBinder).isEmpty();
    }

    @Test
    void when_binder_defaults_prefix_is_known_expect_supported_binder_present() {
      final KafkaLikeStubBinder binder = new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX);

      final Optional<SupportedBinder> supportedBinder = resolver(outboxProperties())
          .resolve(BINDER_NAME, binder, new MockEnvironment(), InputBindings.conventionalNamesOnly());

      assertThat(supportedBinder).isPresent();
    }
  }
}
