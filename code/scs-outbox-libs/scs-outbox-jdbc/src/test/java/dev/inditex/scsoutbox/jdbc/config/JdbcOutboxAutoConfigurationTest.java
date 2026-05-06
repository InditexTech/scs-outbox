package dev.inditex.scsoutbox.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.jdbc.DataSourceMetadata;
import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JdbcOutboxAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(JdbcOutboxAutoConfiguration.class));

  private ApplicationContextRunner contextRunnerWithDependencies() {
    final OutboxDataSourceProvider provider = mock(OutboxDataSourceProvider.class);
    final DataSourceMetadata metadata = mock(DataSourceMetadata.class);
    when(metadata.getDatabaseType()).thenReturn(DataSourceMetadata.JdbcDatabaseType.POSTGRESQL);
    when(metadata.getDefaultSchema()).thenReturn("public");
    when(provider.getPrimary()).thenReturn(mock(DataSource.class));
    when(provider.getDedicatedForPublishing()).thenReturn(mock(DataSource.class));
    when(provider.getPrimaryDataSourceMetadata()).thenReturn(metadata);
    when(provider.getDedicatedForPublishingDataSourceMetadata()).thenReturn(metadata);
    return this.contextRunner
        .withBean(OutboxDataSourceProvider.class, () -> provider)
        .withBean(OutboxMessageSerializer.class, () -> mock(OutboxMessageSerializer.class));
  }

  @Nested
  class OutboxMessageRepository {

    @Test
    void when_dependencies_present_expect_both_repository_beans_created() {
      JdbcOutboxAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("outboxMessageRepository");
            assertThat(context).hasBean("publishingOutboxMessageRepository");
          });
    }

    @Test
    void when_dependencies_present_expect_repositories_are_outbox_message_repository_instances() {
      JdbcOutboxAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context.getBean("outboxMessageRepository"))
                .isInstanceOf(dev.inditex.scsoutbox.OutboxMessageRepository.class);
            assertThat(context.getBean("publishingOutboxMessageRepository"))
                .isInstanceOf(dev.inditex.scsoutbox.OutboxMessageRepository.class);
          });
    }

    @Test
    void when_datasource_provider_missing_expect_context_fails() {
      JdbcOutboxAutoConfigurationTest.this.contextRunner
          .withBean(OutboxMessageSerializer.class, () -> mock(OutboxMessageSerializer.class))
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void when_serializer_missing_expect_context_fails() {
      final OutboxDataSourceProvider provider = mock(OutboxDataSourceProvider.class);

      JdbcOutboxAutoConfigurationTest.this.contextRunner
          .withBean(OutboxDataSourceProvider.class, () -> provider)
          .run(context -> assertThat(context).hasFailed());
    }
  }
}
