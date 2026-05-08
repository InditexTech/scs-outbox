package dev.inditex.scsoutbox.publish.archive.mongodb.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.config.ArchiveAutoConfiguration;
import dev.inditex.scsoutbox.publish.archive.mongodb.MongoDbArchivedMessageRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoDbArchiveAutoConfigurationIT {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(MongoDbArchiveAutoConfiguration.class));

  private ApplicationContextRunner contextRunnerWithDependencies() {
    final OutboxMongoTemplateProvider templateProvider = mock(OutboxMongoTemplateProvider.class);
    when(templateProvider.getDedicatedForPublishing()).thenReturn(mock(MongoTemplate.class));
    return this.contextRunner
        .withUserConfiguration(ArchiveAutoConfigurationPresence.class)
        .withBean(OutboxMongoTemplateProvider.class, () -> templateProvider)
        .withBean(ArchivedMessageSerializer.class, () -> mock(ArchivedMessageSerializer.class));
  }

  @Nested
  class ArchivedMessageRepositoryBean {

    @Test
    void when_archive_auto_configuration_present_expect_mongo_repository_created() {
      MongoDbArchiveAutoConfigurationIT.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ArchivedMessageRepository.class);
            assertThat(context.getBean(ArchivedMessageRepository.class))
                .isInstanceOf(MongoDbArchivedMessageRepository.class);
          });
    }

    @Test
    void when_archive_auto_configuration_absent_expect_autoconfiguration_skipped() {
      MongoDbArchiveAutoConfigurationIT.this.contextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ArchivedMessageRepository.class);
          });
    }

    @Test
    void when_template_provider_missing_expect_context_fails() {
      MongoDbArchiveAutoConfigurationIT.this.contextRunner
          .withUserConfiguration(ArchiveAutoConfigurationPresence.class)
          .withBean(ArchivedMessageSerializer.class, () -> mock(ArchivedMessageSerializer.class))
          .run(context -> assertThat(context).hasFailed());
    }
  }

  /**
   * Helper configuration that satisfies {@code @ConditionalOnBean(ArchiveAutoConfiguration.class)} without activating
   * {@link ArchiveAutoConfiguration}'s own {@code @Bean} methods.
   */
  @Configuration(proxyBeanMethods = false)
  static class ArchiveAutoConfigurationPresence {

    @Bean
    ArchiveAutoConfiguration archiveAutoConfiguration() {
      return mock(ArchiveAutoConfiguration.class);
    }
  }
}
