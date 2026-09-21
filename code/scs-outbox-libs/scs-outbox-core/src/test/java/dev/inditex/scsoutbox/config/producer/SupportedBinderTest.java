package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;
import dev.inditex.scsoutbox.config.producer.SyncProducerMappings.SyncProducerMapping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.mock.env.MockEnvironment;

class SupportedBinderTest {

  private static final String BINDER_NAME = "named-kafka";

  private static final String BOOK_BINDING = "produce-book-out-0";

  private static BindingServiceProperties bindings(final Map<String, BindingProperties> bindings) {
    final BindingServiceProperties properties = mock(BindingServiceProperties.class);
    when(properties.getBindings()).thenReturn(bindings);
    return properties;
  }

  private static BinderProperties binderProperties(final String type, final boolean defaultCandidate) {
    final BinderProperties properties = new BinderProperties();
    properties.setType(type);
    properties.setDefaultCandidate(defaultCandidate);
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
    return supportedBinder(outboxProperties, declared, InputBindings.conventionalNamesOnly());
  }

  private static SupportedBinder supportedBinder(final OutboxProperties outboxProperties,
      final Map<String, BindingProperties> declared, final InputBindings inputBindings) {
    final KafkaLikeStubBinder binder = new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX);
    final SyncProducerMapping mapping = SyncProducerMappings.findByDefaultsPrefix(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX)
        .orElseThrow();
    return supportedBinder(BINDER_NAME, outboxProperties, bindings(declared), binder, mapping, inputBindings);
  }

  private static SupportedBinder supportedBinder(final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties) {
    final KafkaLikeStubBinder binder = new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX);
    final SyncProducerMapping mapping = SyncProducerMappings.findByDefaultsPrefix(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX)
        .orElseThrow();
    return supportedBinder(BINDER_NAME, outboxProperties, bindingServiceProperties, binder, mapping,
        InputBindings.conventionalNamesOnly());
  }

  private static SupportedBinder supportedBinder(final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties, final KafkaLikeStubBinder binder, final SyncProducerMapping mapping) {
    return supportedBinder(BINDER_NAME, outboxProperties, bindingServiceProperties, binder, mapping,
        InputBindings.conventionalNamesOnly());
  }

  private static SupportedBinder supportedBinder(final String binderConfigurationName, final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties, final KafkaLikeStubBinder binder, final SyncProducerMapping mapping) {
    return supportedBinder(binderConfigurationName, outboxProperties, bindingServiceProperties, binder, mapping,
        InputBindings.conventionalNamesOnly());
  }

  private static SupportedBinder supportedBinder(final String binderConfigurationName, final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties, final KafkaLikeStubBinder binder, final SyncProducerMapping mapping,
      final InputBindings inputBindings) {
    return new SupportedBinder(binderConfigurationName, binder, mapping, Binder.get(new MockEnvironment()), outboxProperties,
        bindingServiceProperties, inputBindings);
  }

  private static List<String> outboxBindingNames(final SupportedBinder supportedBinder) {
    return supportedBinder.outboxManagedProducerBindings().stream().map(OutboxBinding::name).toList();
  }

  private static InputBindings inputBindings(final String... names) {
    final Bindable bindable = mock(Bindable.class);
    when(bindable.getInputs()).thenReturn(Set.of(names));
    return InputBindings.from(List.of(bindable));
  }

  @Nested
  class OutboxManagedProducerBindings {

    @Test
    void when_binding_is_excluded_expect_it_not_returned() {
      final Map<String, BindingProperties> declared = Map.of(BOOK_BINDING, producerBinding("book-destination"));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of(BOOK_BINDING)), declared);

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_is_not_included_expect_it_not_returned() {
      final Map<String, BindingProperties> declared = Map.of(BOOK_BINDING, producerBinding("book-destination"));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of("produce-audit-out-0"), List.of()), declared);

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_is_a_function_input_expect_it_not_returned() {
      final BindingProperties consumer = producerBinding("outbox");
      consumer.setGroup("outbox");

      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of("myConsumer-in-0", consumer));

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_renamed_function_input_is_reported_by_spring_cloud_stream_expect_it_not_returned() {
      final BindingProperties input = producerBinding("inbound");
      final SupportedBinder supportedBinder = supportedBinder(
          outboxProperties(List.of(), List.of()), Map.of("anonymous-inbound", input), inputBindings("anonymous-inbound"));

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_renamed_function_input_is_declared_as_explicit_output_expect_it_returned() {
      final String bindingName = "anonymous-inbound";
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(bindingName, producerBinding("published")));
      when(bindingServiceProperties.getOutputBindings()).thenReturn(bindingName);

      final SupportedBinder supportedBinder = supportedBinder(BINDER_NAME, outboxProperties(List.of(), List.of()), bindingServiceProperties,
          new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX), kafkaMapping(), inputBindings(bindingName));

      assertThat(outboxBindingNames(supportedBinder)).containsExactly(bindingName);
    }

    @Test
    void when_function_input_metadata_is_unavailable_expect_conventional_name_to_be_used_as_fallback() {
      final BindingProperties input = producerBinding("inbound");
      final SupportedBinder supportedBinder = supportedBinder(
          outboxProperties(List.of(), List.of()), Map.of("anonymous-in-0", input), inputBindings());

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_conventional_input_name_is_declared_as_explicit_output_expect_it_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(
          Map.of("legacy-in-0", producerBinding("legacy-destination")));
      when(bindingServiceProperties.getOutputBindings()).thenReturn("legacy-in-0");

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties,
          new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX), kafkaMapping());

      assertThat(outboxBindingNames(supportedBinder)).containsExactly("legacy-in-0");
    }

    @Test
    void when_explicit_outputs_are_separated_by_semicolons_expect_exact_output_name_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(
          "legacy-in-0", producerBinding("legacy-destination"),
          "function-in-0", producerBinding("input-destination")));
      when(bindingServiceProperties.getOutputBindings()).thenReturn("other-output;legacy-in-0");

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties,
          new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX), kafkaMapping());

      assertThat(outboxBindingNames(supportedBinder)).containsExactly("legacy-in-0");
    }

    @Test
    void when_binding_declares_no_destination_expect_it_not_returned() {
      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of("no-destination", new BindingProperties()));

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_is_served_by_another_binder_expect_it_not_returned() {
      final BindingProperties bindingProperties = producerBinding("book-destination");
      bindingProperties.setBinder("another-binder");

      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of(BOOK_BINDING, bindingProperties));

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_has_no_binder_and_default_binder_is_current_expect_it_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(BOOK_BINDING, producerBinding("book-destination")));
      when(bindingServiceProperties.getDefaultBinder()).thenReturn(BINDER_NAME);

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties);

      assertThat(outboxBindingNames(supportedBinder)).containsExactly(BOOK_BINDING);
    }

    @Test
    void when_binding_has_no_binder_and_default_binder_is_another_expect_it_not_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(BOOK_BINDING, producerBinding("book-destination")));
      when(bindingServiceProperties.getDefaultBinder()).thenReturn("another-binder");

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties);

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_has_no_binder_and_current_binder_is_the_single_default_candidate_expect_it_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(BOOK_BINDING, producerBinding("book-destination")));
      when(bindingServiceProperties.getBinders()).thenReturn(Map.of(BINDER_NAME, binderProperties("kafka", true)));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties);

      assertThat(outboxBindingNames(supportedBinder)).containsExactly(BOOK_BINDING);
    }

    @Test
    void when_binding_has_no_binder_and_another_binder_is_the_single_default_candidate_expect_it_not_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(BOOK_BINDING, producerBinding("book-destination")));
      when(bindingServiceProperties.getBinders()).thenReturn(Map.of("another-binder", binderProperties("kafka", true)));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties);

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_binding_has_no_binder_and_multiple_default_candidates_expect_it_not_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(BOOK_BINDING, producerBinding("book-destination")));
      when(bindingServiceProperties.getBinders()).thenReturn(Map.of(
          BINDER_NAME, binderProperties("kafka", true),
          "another-binder", binderProperties("kafka", true)));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties);

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_named_binder_is_not_a_default_candidate_and_current_binder_is_named_expect_it_not_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(BOOK_BINDING, producerBinding("book-destination")));
      when(bindingServiceProperties.getBinders()).thenReturn(Map.of(BINDER_NAME, binderProperties("kafka", false)));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), bindingServiceProperties);

      assertThat(outboxBindingNames(supportedBinder)).isEmpty();
    }

    @Test
    void when_named_binder_is_not_a_default_candidate_and_current_binder_is_auto_discovered_expect_it_returned() {
      final BindingServiceProperties bindingServiceProperties = bindings(Map.of(BOOK_BINDING, producerBinding("book-destination")));
      when(bindingServiceProperties.getBinders()).thenReturn(Map.of(BINDER_NAME, binderProperties("kafka", false)));
      final KafkaLikeStubBinder binder = new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX);
      final SyncProducerMapping mapping = SyncProducerMappings.findByDefaultsPrefix(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX)
          .orElseThrow();

      final SupportedBinder supportedBinder = supportedBinder("kafka", outboxProperties(List.of(), List.of()), bindingServiceProperties,
          binder, mapping);

      assertThat(outboxBindingNames(supportedBinder)).containsExactly(BOOK_BINDING);
    }

    @Test
    void when_binding_declares_this_binder_expect_it_returned() {
      final BindingProperties bindingProperties = producerBinding("book-destination");
      bindingProperties.setBinder(BINDER_NAME);

      final SupportedBinder supportedBinder =
          supportedBinder(outboxProperties(List.of(), List.of()), Map.of(BOOK_BINDING, bindingProperties));

      assertThat(outboxBindingNames(supportedBinder)).containsExactly(BOOK_BINDING);
    }

    @Test
    void when_several_bindings_are_eligible_expect_all_of_them_returned() {
      final Map<String, BindingProperties> declared = Map.of(
          BOOK_BINDING, producerBinding("book-destination"),
          "produce-audit-out-0", producerBinding("audit-destination"));

      final SupportedBinder supportedBinder = supportedBinder(outboxProperties(List.of(), List.of()), declared);

      assertThat(outboxBindingNames(supportedBinder)).containsExactlyInAnyOrder(BOOK_BINDING, "produce-audit-out-0");
    }
  }

  private static SyncProducerMapping kafkaMapping() {
    return SyncProducerMappings.findByDefaultsPrefix(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX).orElseThrow();
  }
}
