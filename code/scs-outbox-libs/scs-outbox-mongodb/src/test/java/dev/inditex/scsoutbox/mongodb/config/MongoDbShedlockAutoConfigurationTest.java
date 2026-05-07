package dev.inditex.scsoutbox.mongodb.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoDbShedlockAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(MongoDbShedlockAutoConfiguration.class));

  @Nested
  class LockProvider {

    @Test
    @SuppressWarnings("unchecked")
    void when_no_lock_provider_present_expect_mongo_lock_provider_created() {
      final MongoCollection mongoCollection = mock(MongoCollection.class);
      when(mongoCollection.withWriteConcern(any())).thenReturn(mongoCollection);
      when(mongoCollection.withReadConcern(any())).thenReturn(mongoCollection);
      final MongoDatabase mongoDatabase = mock(MongoDatabase.class);
      when(mongoDatabase.getCollection("shedLock")).thenReturn(mongoCollection);
      final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
      when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
      final OutboxMongoTemplateProvider templateProvider = mock(OutboxMongoTemplateProvider.class);
      when(templateProvider.getDedicatedForPublishing()).thenReturn(mongoTemplate);

      MongoDbShedlockAutoConfigurationTest.this.contextRunner
          .withBean(OutboxMongoTemplateProvider.class, () -> templateProvider)
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(net.javacrumbs.shedlock.core.LockProvider.class);
            assertThat(context.getBean(net.javacrumbs.shedlock.core.LockProvider.class))
                .isInstanceOf(MongoLockProvider.class);
          });
    }

    @Test
    void when_custom_lock_provider_present_expect_auto_configuration_backs_off() {
      final net.javacrumbs.shedlock.core.LockProvider customLockProvider =
          mock(net.javacrumbs.shedlock.core.LockProvider.class);

      MongoDbShedlockAutoConfigurationTest.this.contextRunner
          .withBean(net.javacrumbs.shedlock.core.LockProvider.class, () -> customLockProvider)
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(net.javacrumbs.shedlock.core.LockProvider.class);
            assertThat(context.getBean(net.javacrumbs.shedlock.core.LockProvider.class))
                .isSameAs(customLockProvider);
          });
    }
  }
}
