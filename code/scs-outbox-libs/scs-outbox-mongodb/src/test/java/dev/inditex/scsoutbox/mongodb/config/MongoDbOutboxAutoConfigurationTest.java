package dev.inditex.scsoutbox.mongodb.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.inditex.scsoutbox.mongodb.MongoDbOutboxTemplateProvider;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoDbOutboxAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(MongoDbOutboxAutoConfiguration.class));

  private ApplicationContextRunner contextRunnerWithDependencies() {
    return this.contextRunner
        .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class), bd -> bd.setPrimary(true))
        .withBean(OutboxMessageSerializer.class, () -> mock(OutboxMessageSerializer.class));
  }

  @Nested
  class OutboxMongoTemplateProvider {

    @Test
    void when_primary_template_present_expect_provider_created() {
      MongoDbOutboxAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider.class);
            assertThat(context.getBean(dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider.class))
                .isInstanceOf(MongoDbOutboxTemplateProvider.class);
          });
    }

    @Test
    void when_no_dedicated_publishing_template_expect_provider_uses_primary_for_both() {
      MongoDbOutboxAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            final var provider = context.getBean(dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider.class);
            assertThat(provider.getPrimary()).isSameAs(provider.getDedicatedForPublishing());
          });
    }

    @Test
    void when_dedicated_publishing_template_present_expect_provider_uses_different_templates() {
      MongoDbOutboxAutoConfigurationTest.this.contextRunnerWithDependencies()
          .withBean(MongoDbOutboxAutoConfiguration.OUTBOX_PUBLISHING_MONGOTEMPLATE_BEAN_NAME,
              MongoTemplate.class, () -> mock(MongoTemplate.class))
          .run(context -> {
            assertThat(context).hasNotFailed();
            final var provider = context.getBean(dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider.class);
            assertThat(provider.getPrimary()).isNotSameAs(provider.getDedicatedForPublishing());
          });
    }
  }

  @Nested
  class OutboxMessageRepository {

    @Test
    void when_dependencies_present_expect_both_repository_beans_created() {
      MongoDbOutboxAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("outboxMessageRepository");
            assertThat(context).hasBean("publishingOutboxMessageRepository");
          });
    }

    @Test
    void when_dependencies_present_expect_repositories_are_outbox_message_repository_instances() {
      MongoDbOutboxAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context.getBean("outboxMessageRepository"))
                .isInstanceOf(dev.inditex.scsoutbox.OutboxMessageRepository.class);
            assertThat(context.getBean("publishingOutboxMessageRepository"))
                .isInstanceOf(dev.inditex.scsoutbox.OutboxMessageRepository.class);
          });
    }

    @Test
    void when_mongo_template_missing_expect_context_fails() {
      MongoDbOutboxAutoConfigurationTest.this.contextRunner
          .withBean(OutboxMessageSerializer.class, () -> mock(OutboxMessageSerializer.class))
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void when_serializer_missing_expect_context_fails() {
      MongoDbOutboxAutoConfigurationTest.this.contextRunner
          .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class))
          .run(context -> assertThat(context).hasFailed());
    }
  }
}
