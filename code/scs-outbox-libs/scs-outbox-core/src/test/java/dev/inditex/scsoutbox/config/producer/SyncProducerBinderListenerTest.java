package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;
import dev.inditex.scsoutbox.config.OutboxProperties.SyncProducers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class SyncProducerBinderListenerTest {

  private static final String BINDER_NAME = "named-kafka";

  private static final String BOOK_BINDING = "produce-book-out-0";

  private static final String BOOK_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync";

  private static final String KAFKA_DEFAULT_SYNC_PROPERTY = "spring.cloud.stream.kafka.default.producer.sync";

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

  private static Map<String, BindingProperties> bookBinding() {
    return Map.of(BOOK_BINDING, producerBinding("book-destination"));
  }

  private static OutboxProperties outboxProperties() {
    return new OutboxProperties(new Bindings(List.of(), List.of()));
  }

  private static ConfigurableApplicationContext binderContext(final Object binder, final MockEnvironment environment) {
    final GenericApplicationContext context = new GenericApplicationContext();
    context.setEnvironment(environment);
    context.getBeanFactory().registerSingleton("stubBinder", binder);
    context.refresh();
    return context;
  }

  private static SyncProducerBinderListener listener(final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties) {
    final ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeansOfType(Bindable.class)).thenReturn(Map.of());
    when(applicationContext.getBean(OutboxBindingsContext.class))
        .thenReturn(new OutboxBindingsContext(outboxProperties, bindingServiceProperties));
    final SyncProducerBinderListener listener = new SyncProducerBinderListener();
    listener.setApplicationContext(applicationContext);
    return listener;
  }

  private static SyncProducerBinderListener listener(final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties, final Set<String> inputBindingNames) {
    final ApplicationContext applicationContext = mock(ApplicationContext.class);
    final Bindable bindable = mock(Bindable.class);
    when(bindable.getInputs()).thenReturn(inputBindingNames);
    when(applicationContext.getBeansOfType(Bindable.class)).thenReturn(Map.of("function", bindable));
    when(applicationContext.getBean(OutboxBindingsContext.class))
        .thenReturn(new OutboxBindingsContext(outboxProperties, bindingServiceProperties));
    final SyncProducerBinderListener listener = new SyncProducerBinderListener();
    listener.setApplicationContext(applicationContext);
    return listener;
  }

  private static KafkaLikeStubBinder kafkaBinder() {
    return new KafkaLikeStubBinder(SyncProducerMappings.KAFKA_DEFAULTS_PREFIX);
  }

  @Nested
  class Configuration {

    /**
     * Per-binding synchronisation rules (already-synchronous, declared settings, precedence, upper-case names...) are covered by
     * {@link OutboxBindingTest}. This test is about the listener's own responsibility: iterating every binding reported by
     * {@link BindingServiceProperties} and applying the same rule to each of them.
     */
    @Test
    void when_several_bindings_expect_all_of_them_switched_to_synchronous() {
      final KafkaLikeStubBinder binder = kafkaBinder();
      final Map<String, BindingProperties> declared = Map.of(
          BOOK_BINDING, producerBinding("book-destination"),
          "produce-audit-out-0", producerBinding("audit-destination"));

      listener(outboxProperties(), bindings(declared))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
      assertThat(binder.getExtendedProducerProperties("produce-audit-out-0").isSync()).isTrue();
    }
  }

  @Nested
  class InputBindings {

    @Test
    void when_renamed_input_is_known_to_spring_cloud_stream_expect_it_not_treated_as_producer() {
      final String inputBindingName = "anonymous-inbound";
      final BindingProperties input = producerBinding("inbound");
      final Map<String, BindingProperties> declared = Map.of(
          inputBindingName, input,
          BOOK_BINDING, producerBinding("book-destination"));
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.kafka.bindings.anonymous-inbound.producer.sync", "false");
      final KafkaLikeStubBinder binder = kafkaBinder();

      assertThatCode(() -> listener(outboxProperties(), bindings(declared), Set.of(inputBindingName))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, environment)))
              .doesNotThrowAnyException();
      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
    }
  }

  @Nested
  class ApplicationConfigurationPrecedence {

    /**
     * Regression test: binder specific properties may be declared under {@code spring.cloud.stream.binders.<name>.environment.*}, which is
     * materialised only in the binder child environment. Reading the main environment would miss them.
     *
     * <p>The precedence rules themselves (binding-scoped vs. binder-default) are covered by {@link OutboxBindingTest}; this test only
     * verifies the listener wires the binder child environment, not the main one, into that resolution.
     */
    @Test
    void when_application_disables_sync_in_the_binder_child_environment_expect_failure() {
      // The binder child environment is what Spring Cloud Stream builds out of 'binders.<name>.environment.*' plus the inherited main
      // environment, which is exactly what the listener receives.
      final MockEnvironment binderEnvironment = new MockEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "false");
      final SyncProducerBinderListener listener = listener(outboxProperties(), bindings(bookBinding()));
      final ConfigurableApplicationContext binderContext = binderContext(kafkaBinder(), binderEnvironment);

      assertThatThrownBy(() -> listener.afterBinderContextInitialized(BINDER_NAME, binderContext))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(BOOK_BINDING);
    }

    @Test
    void when_only_one_binding_is_misconfigured_expect_the_other_one_still_configured() {
      final KafkaLikeStubBinder binder = kafkaBinder();
      final Map<String, BindingProperties> declared = Map.of(
          BOOK_BINDING, producerBinding("book-destination"),
          "produce-audit-out-0", producerBinding("audit-destination"));
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");
      final SyncProducerBinderListener listener = listener(outboxProperties(), bindings(declared));
      final ConfigurableApplicationContext binderContext = binderContext(binder, environment);

      assertThatThrownBy(() -> listener.afterBinderContextInitialized(BINDER_NAME, binderContext))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(BINDER_NAME)
          .hasMessageContaining(BOOK_BINDING)
          .hasMessageContaining(BOOK_SYNC_PROPERTY + "=false");
      assertThat(binder.getExtendedProducerProperties("produce-audit-out-0").isSync()).isTrue();
    }
  }

  @Nested
  class UnsupportedBinders {

    /**
     * The set of causes that make {@code supportFor(...)} return empty (wrong binder type, unknown defaults prefix) is covered by
     * {@link OutboxBindingsContextTest}; this only verifies the listener does not fail when that happens.
     */
    @Test
    void when_binder_is_not_supported_expect_no_failure() {
      final Binder<?, ?, ?> plainBinder = mock(Binder.class);

      assertThatCode(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(plainBinder, new MockEnvironment())))
              .doesNotThrowAnyException();
    }
  }

  @Nested
  class OptOut {

    /**
     * Verifies the feature flag short-circuits before any binder/eligibility logic runs: even though the binding is explicitly declared
     * asynchronous, no violation is ever evaluated. {@link OutboxBindingsContextTest} covers the flag's boolean logic on its own.
     */
    @Test
    void when_sync_producers_are_disabled_expect_binding_untouched_and_no_failure() {
      final KafkaLikeStubBinder binder = kafkaBinder();
      final OutboxProperties outboxProperties =
          new OutboxProperties(new Bindings(List.of(), List.of(), new SyncProducers(false)));
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      assertThatCode(() -> listener(outboxProperties, bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, environment)))
              .doesNotThrowAnyException();
      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isFalse();
    }
  }
}
