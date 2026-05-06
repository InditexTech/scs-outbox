package dev.inditex.scsoutbox.mongodb.config;

import dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5m")
public class MongoDbShedlockAutoConfiguration {

  /**
   * LockProvider Bean by default.
   */
  @ConditionalOnMissingBean
  @Bean
  public LockProvider lockProvider(final OutboxMongoTemplateProvider templateProvider) {
    return new MongoLockProvider(templateProvider.getDedicatedForPublishing().getDb());
  }

}
