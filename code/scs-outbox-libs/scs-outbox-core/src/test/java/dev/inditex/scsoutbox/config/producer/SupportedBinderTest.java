package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;
import dev.inditex.scsoutbox.config.producer.SyncProducerMappings.SyncProducerMapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.mock.env.MockEnvironment;

class SupportedBinderTest {

  private static final String BINDER_NAME = "kafka-pipe";

  private static final String BOOK_BINDING = "produce-book-out-0";

  private static BindingServiceProperties bindings(final Map<String, BindingProperties> bindings) {
    final BindingServiceProperties properties = mock(BindingServiceProperties.class);
    when(properties.getBindings()).thenReturn(bindings);
    return properties;
  }

  private static BindingProperties producerBinding(final String destination) {
    final BindingProperties bindingProperties = new BindingProperties();
    bindingProperties.setDestination(destination);
    return bindingProperties;
  }

  private static OutboxProperties outboxProperties(final List<String> inclusions, final List<String> exclusions) {
    return new OutboxProperties(new Bindings(inclusions, exclusions));
  }

  private static SupportedBinder supportedBinder(final OutboxProperties outboxProperties, final Map<String, BindingProperties> declared) {
    final KafkaLikeStubBinder binder = new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX);
    final SyncProducerMapping mapping = SyncProducerMappings.findByDefaultsPrefix(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX)
        .orElseThrow();
    return new SupportedBinder(BINDER_NAME, binder, mapping, Binder.get(new MockEnvironment()), outboxProperties, bindings(declared));
  }

  private static List<String> bindingNames(final SupportedBinder supportedBinder) {
    return supportedBinder.getBindings().stream().map(OutboxBinding::name).toList();
  }

  @Nested
  class GetBindings {

    @Test
    void when_binding_is_excluded_expect_it_not_returned() {
      final Map<String, BindingProperties> declared = Map.of(BOOK_BINDING, producerBinding("book-destination"));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of(BOOK_BINDING)), declared);

      assertThat(bindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_is_not_included_expect_it_not_returned() {
      final Map<String, BindingProperties> declared = Map.of(BOOK_BINDING, producerBinding("book-destination"));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of("produce-audit-out-0"), List.of()), declared);

      assertThat(bindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_is_a_function_input_expect_it_not_returned() {
      final BindingProperties consumer = producerBinding("outbox");
      consumer.setGroup("outbox");

      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of("myConsumer-in-0", consumer));

      assertThat(bindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_declares_no_destination_expect_it_not_returned() {
      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of("no-destination", new BindingProperties()));

      assertThat(bindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_is_served_by_another_binder_expect_it_not_returned() {
      final BindingProperties bindingProperties = producerBinding("book-destination");
      bindingProperties.setBinder("another-binder");

      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of(BOOK_BINDING, bindingProperties));

      assertThat(bindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_declares_this_binder_expect_it_returned() {
      final BindingProperties bindingProperties = producerBinding("book-destination");
      bindingProperties.setBinder(BINDER_NAME);

      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of(BOOK_BINDING, bindingProperties));

      assertThat(bindingNames(supportedBinder)).containsExactly(BOOK_BINDING);
    }

    @Test
    void when_several_bindings_are_eligible_expect_all_of_them_returned() {
      final Map<String, BindingProperties> declared = Map.of(
          BOOK_BINDING, producerBinding("book-destination"),
          "produce-audit-out-0", producerBinding("audit-destination"));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), declared);

      assertThat(bindingNames(supportedBinder)).containsExactlyInAnyOrder(BOOK_BINDING, "produce-audit-out-0");
    }
  }
}
