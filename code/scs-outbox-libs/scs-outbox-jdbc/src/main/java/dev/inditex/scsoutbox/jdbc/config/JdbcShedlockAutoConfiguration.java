package dev.inditex.scsoutbox.jdbc.config;

import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the JDBC ShedLock {@link LockProvider} used by outbox publishing tasks.
 */
@AutoConfiguration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5m")
public class JdbcShedlockAutoConfiguration {

  /**
   * Creates the default JDBC {@link LockProvider} for outbox scheduled publishing.
   *
   * @param dataSourceProvider provider that supplies the DataSource used for publishing
   * @return the default JDBC lock provider
   */
  @ConditionalOnMissingBean
  @Bean
  public LockProvider lockProvider(final OutboxDataSourceProvider dataSourceProvider) {
    return new JdbcTemplateLockProvider(dataSourceProvider.getDedicatedForPublishing());
  }

}
