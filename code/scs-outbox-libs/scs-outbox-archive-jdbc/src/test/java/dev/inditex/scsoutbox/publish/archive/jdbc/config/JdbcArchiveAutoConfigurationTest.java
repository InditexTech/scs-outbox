package dev.inditex.scsoutbox.publish.archive.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.jdbc.DataSourceMetadata;
import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.config.ArchiveAutoConfiguration;
import dev.inditex.scsoutbox.publish.archive.jdbc.JdbcArchivedMessageRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcArchiveAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(JdbcArchiveAutoConfiguration.class))
      .withPropertyValues("scs-outbox.publishing.archive.jdbc.schema=PUBLIC");

  private ApplicationContextRunner contextRunnerWithDependencies() {
    final OutboxDataSourceProvider provider = mock(OutboxDataSourceProvider.class);
    when(provider.getDedicatedForPublishing()).thenReturn(createTestDataSource());
    when(provider.getDedicatedForPublishingDataSourceMetadata()).thenReturn(mock(DataSourceMetadata.class));
    return this.contextRunner
        .withUserConfiguration(ArchiveAutoConfigurationPresence.class)
        .withBean(OutboxDataSourceProvider.class, () -> provider)
        .withBean(ArchivedMessageSerializer.class, () -> mock(ArchivedMessageSerializer.class));
  }

  @Nested
  class ArchivedMessageRepository {

    @Test
    void when_archive_auto_configuration_present_expect_archived_message_repository_created() {
      JdbcArchiveAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository.class);
            assertThat(context.getBean(dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository.class))
                .isInstanceOf(JdbcArchivedMessageRepository.class);
          });
    }

    @Test
    void when_archive_auto_configuration_absent_expect_auto_configuration_skipped() {
      JdbcArchiveAutoConfigurationTest.this.contextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository.class);
          });
    }

    @Test
    void when_datasource_provider_missing_expect_context_fails() {
      JdbcArchiveAutoConfigurationTest.this.contextRunner
          .withUserConfiguration(ArchiveAutoConfigurationPresence.class)
          .withBean(ArchivedMessageSerializer.class, () -> mock(ArchivedMessageSerializer.class))
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void when_archived_message_serializer_missing_expect_context_fails() {
      final OutboxDataSourceProvider provider = mock(OutboxDataSourceProvider.class);
      when(provider.getDedicatedForPublishing()).thenReturn(createTestDataSource());
      when(provider.getDedicatedForPublishingDataSourceMetadata()).thenReturn(mock(DataSourceMetadata.class));
      JdbcArchiveAutoConfigurationTest.this.contextRunner
          .withUserConfiguration(ArchiveAutoConfigurationPresence.class)
          .withBean(OutboxDataSourceProvider.class, () -> provider)
          .run(context -> assertThat(context).hasFailed());
    }
  }

  /**
   * Helper configuration that satisfies {@code @ConditionalOnBean(ArchiveAutoConfiguration.class)} without activating
   * {@link ArchiveAutoConfiguration}'s own {@code @Bean} methods. Spring skips reprocessing factory-method beans as configuration
   * candidates ({@code factoryMethodName != null}), so no transitive dependencies are required.
   */
  @Configuration(proxyBeanMethods = false)
  static class ArchiveAutoConfigurationPresence {

    @Bean
    ArchiveAutoConfiguration archiveAutoConfiguration() {
      return mock(ArchiveAutoConfiguration.class);
    }
  }

  private static DataSource createTestDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }
}
