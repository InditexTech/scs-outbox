package dev.inditex.scsoutbox.publish.archive.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.inditex.scsoutbox.publish.archive.ArchiveOutboxMessagePublisherInterceptor;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository;
import dev.inditex.scsoutbox.serialization.HeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageReconverter;
import dev.inditex.scsoutbox.serialization.SerializationEngine;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.stream.config.BindingServiceProperties;

class ArchiveAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ArchiveAutoConfiguration.class));

  private ApplicationContextRunner contextRunnerWithDependencies() {
    return this.contextRunner
        .withPropertyValues("scs-outbox.publishing.archive.enabled=true")
        .withBean(SerializationEngine.class, () -> mock(SerializationEngine.class))
        .withBean(HeadersMapper.class, () -> mock(HeadersMapper.class))
        .withBean(ArchivedMessageRepository.class, () -> mock(ArchivedMessageRepository.class))
        .withBean(BindingServiceProperties.class, () -> mock(BindingServiceProperties.class))
        .withBean(OutboxMessageReconverter.class, () -> mock(OutboxMessageReconverter.class));
  }

  @Nested
  class ArchiveOutboxMessagePublisherInterceptorBean {

    @Test
    void when_archive_enabled_with_dependencies_expect_interceptor_created() {
      ArchiveAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ArchiveOutboxMessagePublisherInterceptor.class);
          });
    }

    @Test
    void when_archive_disabled_expect_autoconfiguration_skipped() {
      ArchiveAutoConfigurationTest.this.contextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ArchiveOutboxMessagePublisherInterceptor.class);
          });
    }

    @Test
    void when_archived_message_repository_missing_expect_context_fails() {
      ArchiveAutoConfigurationTest.this.contextRunner
          .withPropertyValues("scs-outbox.publishing.archive.enabled=true")
          .withBean(SerializationEngine.class, () -> mock(SerializationEngine.class))
          .withBean(HeadersMapper.class, () -> mock(HeadersMapper.class))
          .withBean(BindingServiceProperties.class, () -> mock(BindingServiceProperties.class))
          .withBean(OutboxMessageReconverter.class, () -> mock(OutboxMessageReconverter.class))
          .run(context -> assertThat(context).hasFailed());
    }
  }
}
