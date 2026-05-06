package dev.inditex.scsoutbox.serialization.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.inditex.scsoutbox.OutboxServiceProperties;
import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.serialization.HeadersMapper;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageReconverter;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;
import dev.inditex.scsoutbox.serialization.SerializationEngine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.messaging.converter.CompositeMessageConverter;

class SerializationAutoConfigurationTest {

  private ApplicationContextRunner contextRunner;

  @BeforeEach
  void beforeEach() {
    this.contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SerializationAutoConfiguration.class);
  }

  private ApplicationContextRunner contextRunnerWithRequiredDependencies() {
    final OutboxProperties outboxProperties = new OutboxProperties(null);
    final CompositeMessageConverter compositeMessageConverter = mock(CompositeMessageConverter.class);
    final OutboxServiceProperties outboxServiceProperties = mock(OutboxServiceProperties.class);
    return this.contextRunner
        .withBean(OutboxProperties.class, () -> outboxProperties)
        .withBean(CompositeMessageConverter.class, () -> compositeMessageConverter)
        .withBean(OutboxServiceProperties.class, () -> outboxServiceProperties);
  }

  @Nested
  class CreateSerializationEngine {

    @Test
    void when_context_loads_expect_default_serialization_engine_bean_created() {
      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .run(context -> {
            assertThat(context).hasSingleBean(SerializationEngine.class);
            assertThat(context.getBean(SerializationEngine.class))
                .isInstanceOf(JavaSerialization.class);
          });
    }

    @Test
    void when_custom_serialization_engine_provided_expect_custom_bean_used() {
      final SerializationEngine customEngine = mock(SerializationEngine.class);

      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .withBean(SerializationEngine.class, () -> customEngine)
          .run(context -> {
            assertThat(context).hasSingleBean(SerializationEngine.class);
            assertThat(context.getBean(SerializationEngine.class))
                .isSameAs(customEngine);
          });
    }
  }

  @Nested
  class CreateHeadersMapper {

    @Test
    void when_context_loads_expect_default_headers_mapper_bean_created() {
      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .run(context -> {
            assertThat(context).hasSingleBean(HeadersMapper.class);
            assertThat(context.getBean(HeadersMapper.class))
                .isInstanceOf(JsonHeadersMapper.class);
          });
    }

    @Test
    void when_custom_headers_mapper_provided_expect_custom_bean_used() {
      final HeadersMapper customMapper = mock(HeadersMapper.class);

      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .withBean(HeadersMapper.class, () -> customMapper)
          .run(context -> {
            assertThat(context).hasSingleBean(HeadersMapper.class);
            assertThat(context.getBean(HeadersMapper.class))
                .isSameAs(customMapper);
          });
    }
  }

  @Nested
  class CreateOutboxMessageReconverter {

    @Test
    void when_context_loads_expect_outbox_message_reconverter_bean_created() {
      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .run(context -> assertThat(context).hasSingleBean(OutboxMessageReconverter.class));
    }

    @Test
    void when_composite_message_converter_missing_expect_context_startup_failure() {
      final OutboxProperties outboxProperties = new OutboxProperties(null);
      final OutboxServiceProperties outboxServiceProperties = mock(OutboxServiceProperties.class);

      SerializationAutoConfigurationTest.this.contextRunner
          .withBean(OutboxProperties.class, () -> outboxProperties)
          .withBean(OutboxServiceProperties.class, () -> outboxServiceProperties)
          .run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasMessageContaining("CompositeMessageConverter");
          });
    }
  }

  @Nested
  class CreateOutboxMessageSerializer {

    @Test
    void when_context_loads_expect_outbox_message_serializer_bean_created() {
      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .run(context -> {
            assertThat(context).hasSingleBean(OutboxMessageSerializer.class);
            assertThat(context).hasSingleBean(OutboxMessageReconverter.class);
            final OutboxMessageSerializer serializer = context.getBean(OutboxMessageSerializer.class);
            assertThat(serializer).isNotNull();
          });
    }

    @Test
    void when_all_dependencies_available_expect_outbox_message_serializer_properly_initialized() {
      final SerializationEngine serializationEngine = mock(SerializationEngine.class);
      final HeadersMapper headersMapper = mock(HeadersMapper.class);

      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .withBean(SerializationEngine.class, () -> serializationEngine)
          .withBean(HeadersMapper.class, () -> headersMapper)
          .run(context -> {
            assertThat(context).hasSingleBean(OutboxMessageSerializer.class);
            final OutboxMessageSerializer serializer = context.getBean(OutboxMessageSerializer.class);
            assertThat(serializer).isNotNull();
          });
    }

    @Test
    void when_context_loads_expect_default_serialization_engine_injected_into_serializer() {
      SerializationAutoConfigurationTest.this.contextRunnerWithRequiredDependencies()
          .run(context -> {
            final OutboxMessageSerializer serializer = context.getBean(OutboxMessageSerializer.class);
            final SerializationEngine engine = context.getBean(SerializationEngine.class);

            assertThat(serializer).isNotNull();
            assertThat(engine).isInstanceOf(JavaSerialization.class);
          });
    }
  }
}
