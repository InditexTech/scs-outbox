package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;
import dev.inditex.scsoutbox.config.OutboxProperties.SyncProducers;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class SyncProducerBinderFactoryListenerTest {

  private static final String BINDER_NAME = "kafka-pipe";

  private static final String BOOK_BINDING = "produce-book-out-0";

  private static final String BOOK_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync";

  private static final String KAFKA_DEFAULT_SYNC_PROPERTY = "spring.cloud.stream.kafka.default.producer.sync";

  /**
   * Minimal stand-in for a binder-specific producer properties object, exposing the same {@code sync} JavaBean property as
   * {@code KafkaProducerProperties}.
   */
  public static class StubProducerProperties {

    private boolean sync;

    public boolean isSync() {
      return this.sync;
    }

    public void setSync(final boolean sync) {
      this.sync = sync;
    }
  }

  public static class StubBindingProperties implements BinderSpecificPropertiesProvider {

    private final StubProducerProperties producer = new StubProducerProperties();

    @Override
    public StubProducerProperties getProducer() {
      return this.producer;
    }

    @Override
    public Object getConsumer() {
      return null;
    }
  }

  /**
   * Stub binder reporting the Kafka defaults prefix and handing out one cached producer properties instance per binding, exactly like
   * {@code AbstractExtendedBindingProperties} does.
   */
  static class StubBinder implements ExtendedPropertiesBinder<Object, Object, StubProducerProperties> {

    private final Map<String, StubProducerProperties> producerProperties = new LinkedHashMap<>();

    private final String defaultsPrefix;

    StubBinder(final String defaultsPrefix) {
      this.defaultsPrefix = defaultsPrefix;
    }

    @Override
    public StubProducerProperties getExtendedProducerProperties(final String bindingName) {
      return this.producerProperties.computeIfAbsent(bindingName, name -> new StubProducerProperties());
    }

    @Override
    public Object getExtendedConsumerProperties(final String bindingName) {
      return null;
    }

    @Override
    public String getDefaultsPrefix() {
      return this.defaultsPrefix;
    }

    @Override
    public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
      return StubBindingProperties.class;
    }

    @Override
    public Binding<Object> bindConsumer(final String name, final String group, final Object target,
        final ExtendedConsumerProperties<Object> properties) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Binding<Object> bindProducer(final String name, final Object target,
        final ExtendedProducerProperties<StubProducerProperties> properties) {
      throw new UnsupportedOperationException();
    }
  }

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

  private static SyncProducerBinderFactoryListener listener(final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties) {
    final ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBean(OutboxProperties.class)).thenReturn(outboxProperties);
    when(applicationContext.getBean(BindingServiceProperties.class)).thenReturn(bindingServiceProperties);
    final SyncProducerBinderFactoryListener listener = new SyncProducerBinderFactoryListener();
    listener.setApplicationContext(applicationContext);
    return listener;
  }

  private static StubBinder kafkaBinder() {
    return new StubBinder(SyncProducerBinderRegistry.KAFKA_DEFAULTS_PREFIX);
  }

  @Nested
  class Configuration {

    @Test
    void when_sync_is_not_configured_expect_producer_switched_to_synchronous() {
      final StubBinder binder = kafkaBinder();

      listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
    }

    @Test
    void when_several_bindings_expect_all_of_them_switched_to_synchronous() {
      final StubBinder binder = kafkaBinder();
      final Map<String, BindingProperties> declared = Map.of(
          BOOK_BINDING, producerBinding("book-destination"),
          "produce-audit-out-0", producerBinding("audit-destination"));

      listener(outboxProperties(), bindings(declared))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
      assertThat(binder.getExtendedProducerProperties("produce-audit-out-0").isSync()).isTrue();
    }

    @Test
    void when_binding_name_contains_upper_case_expect_producer_switched_to_synchronous() {
      final StubBinder binder = kafkaBinder();
      final Map<String, BindingProperties> declared = Map.of("myProducer-out-0", producerBinding("book-destination"));

      listener(outboxProperties(), bindings(declared))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties("myProducer-out-0").isSync()).isTrue();
    }

    @Test
    void when_producer_is_already_synchronous_expect_no_failure() {
      final StubBinder binder = kafkaBinder();
      binder.getExtendedProducerProperties(BOOK_BINDING).setSync(true);
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "true");

      assertThatCode(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, environment)))
              .doesNotThrowAnyException();
      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
    }
  }

  @Nested
  class ApplicationConfigurationPrecedence {

    @Test
    void when_application_disables_sync_for_the_binding_expect_failure() {
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      assertThatThrownBy(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(kafkaBinder(), environment)))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining(BINDER_NAME)
              .hasMessageContaining(BOOK_BINDING)
              .hasMessageContaining(BOOK_SYNC_PROPERTY + "=false")
              .hasMessageContaining("scs-outbox.bindings.exclusions")
              // The global 'scs-outbox.bindings.sync-producers.enabled=false' switch must never be suggested as a fix for a single
              // misconfigured binding: it disables the delivery guarantee for every outbox-enabled binding in the application.
              .hasMessageNotContaining("scs-outbox.bindings.sync-producers.enabled=false");
    }

    @Test
    void when_application_disables_sync_as_binder_default_expect_failure() {
      final MockEnvironment environment = new MockEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "false");

      assertThatThrownBy(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(kafkaBinder(), environment)))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining(KAFKA_DEFAULT_SYNC_PROPERTY + "=false");
    }

    /**
     * Regression test: binder specific properties may be declared under {@code spring.cloud.stream.binders.<name>.environment.*}, which is
     * materialised only in the binder child environment. Reading the main environment would miss them.
     */
    @Test
    void when_application_disables_sync_in_the_binder_child_environment_expect_failure() {
      // The binder child environment is what Spring Cloud Stream builds out of 'binders.<name>.environment.*' plus the inherited main
      // environment, which is exactly what the listener receives.
      final MockEnvironment binderEnvironment = new MockEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "false");

      assertThatThrownBy(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(kafkaBinder(), binderEnvironment)))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining(BOOK_BINDING);
    }

    @Test
    void when_application_enables_sync_as_binder_default_expect_binding_untouched_and_no_failure() {
      final StubBinder binder = kafkaBinder();
      // A binder default of 'true' is already reflected in the producer properties bound by Spring Cloud Stream.
      binder.getExtendedProducerProperties(BOOK_BINDING).setSync(true);
      final MockEnvironment environment = new MockEnvironment().withProperty(KAFKA_DEFAULT_SYNC_PROPERTY, "true");

      assertThatCode(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, environment)))
              .doesNotThrowAnyException();
    }

    @Test
    void when_only_one_binding_is_misconfigured_expect_the_other_one_still_configured() {
      final StubBinder binder = kafkaBinder();
      final Map<String, BindingProperties> declared = Map.of(
          BOOK_BINDING, producerBinding("book-destination"),
          "produce-audit-out-0", producerBinding("audit-destination"));
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      assertThatThrownBy(() -> listener(outboxProperties(), bindings(declared))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, environment)))
              .isInstanceOf(IllegalStateException.class);
      assertThat(binder.getExtendedProducerProperties("produce-audit-out-0").isSync()).isTrue();
    }
  }

  @Nested
  class OnlyOutboxManagedBindings {

    @Test
    void when_binding_is_excluded_expect_it_untouched() {
      final StubBinder binder = kafkaBinder();
      final OutboxProperties outboxProperties = new OutboxProperties(new Bindings(List.of(), List.of(BOOK_BINDING)));

      listener(outboxProperties, bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isFalse();
    }

    @Test
    void when_excluded_binding_is_explicitly_asynchronous_expect_no_failure() {
      final OutboxProperties outboxProperties = new OutboxProperties(new Bindings(List.of(), List.of(BOOK_BINDING)));
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      assertThatCode(() -> listener(outboxProperties, bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(kafkaBinder(), environment)))
              .doesNotThrowAnyException();
    }

    @Test
    void when_binding_is_not_included_expect_it_untouched() {
      final StubBinder binder = kafkaBinder();
      final OutboxProperties outboxProperties = new OutboxProperties(new Bindings(List.of("produce-audit-out-0"), List.of()));

      listener(outboxProperties, bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isFalse();
    }

    @Test
    void when_binding_is_a_function_input_expect_it_untouched() {
      final StubBinder binder = kafkaBinder();
      final BindingProperties consumer = producerBinding("outbox");
      consumer.setGroup("outbox");

      listener(outboxProperties(), bindings(Map.of("myConsumer-in-0", consumer)))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties("myConsumer-in-0").isSync()).isFalse();
    }

    @Test
    void when_binding_declares_no_destination_expect_it_untouched() {
      final StubBinder binder = kafkaBinder();

      listener(outboxProperties(), bindings(Map.of("no-destination", new BindingProperties())))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties("no-destination").isSync()).isFalse();
    }

    @Test
    void when_binding_is_served_by_another_binder_expect_it_untouched() {
      final StubBinder binder = kafkaBinder();
      final BindingProperties bindingProperties = producerBinding("book-destination");
      bindingProperties.setBinder("another-binder");

      listener(outboxProperties(), bindings(Map.of(BOOK_BINDING, bindingProperties)))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isFalse();
    }

    @Test
    void when_binding_declares_this_binder_expect_it_configured() {
      final StubBinder binder = kafkaBinder();
      final BindingProperties bindingProperties = producerBinding("book-destination");
      bindingProperties.setBinder(BINDER_NAME);

      listener(outboxProperties(), bindings(Map.of(BOOK_BINDING, bindingProperties)))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, new MockEnvironment()));

      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isTrue();
    }
  }

  @Nested
  class UnsupportedBinders {

    @Test
    void when_binder_is_not_an_extended_properties_binder_expect_no_failure() {
      final Binder<?, ?, ?> plainBinder = mock(Binder.class);

      assertThatCode(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(plainBinder, new MockEnvironment())))
              .doesNotThrowAnyException();
    }

    @Test
    void when_binder_defaults_prefix_is_unknown_expect_binding_untouched_and_no_failure() {
      final StubBinder binder = new StubBinder("spring.cloud.stream.rabbit.default");
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      assertThatCode(() -> listener(outboxProperties(), bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, environment)))
              .doesNotThrowAnyException();
      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isFalse();
    }
  }

  @Nested
  class OptOut {

    @Test
    void when_sync_producers_are_disabled_expect_binding_untouched_and_no_failure() {
      final StubBinder binder = kafkaBinder();
      final OutboxProperties outboxProperties =
          new OutboxProperties(new Bindings(List.of(), List.of(), new SyncProducers(false)));
      final MockEnvironment environment = new MockEnvironment().withProperty(BOOK_SYNC_PROPERTY, "false");

      assertThatCode(() -> listener(outboxProperties, bindings(bookBinding()))
          .afterBinderContextInitialized(BINDER_NAME, binderContext(binder, environment)))
              .doesNotThrowAnyException();
      assertThat(binder.getExtendedProducerProperties(BOOK_BINDING).isSync()).isFalse();
    }
  }

  @Nested
  class Ordering {

    @Test
    void expect_to_run_before_any_other_binder_factory_listener() {
      assertThat(new SyncProducerBinderFactoryListener().getOrder())
          .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
  }
}
