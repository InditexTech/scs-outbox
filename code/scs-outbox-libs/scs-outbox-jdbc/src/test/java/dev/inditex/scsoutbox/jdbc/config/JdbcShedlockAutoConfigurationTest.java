package dev.inditex.scsoutbox.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JdbcShedlockAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(JdbcShedlockAutoConfiguration.class));

  @Nested
  class LockProvider {

    @Test
    void when_no_lock_provider_present_expect_jdbc_template_lock_provider_created() {
      final OutboxDataSourceProvider dataSourceProvider = mock(OutboxDataSourceProvider.class);
      when(dataSourceProvider.getDedicatedForPublishing()).thenReturn(mock(DataSource.class));

      JdbcShedlockAutoConfigurationTest.this.contextRunner
          .withBean(OutboxDataSourceProvider.class, () -> dataSourceProvider)
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(net.javacrumbs.shedlock.core.LockProvider.class);
            assertThat(context.getBean(net.javacrumbs.shedlock.core.LockProvider.class))
                .isInstanceOf(JdbcTemplateLockProvider.class);
          });
    }

    @Test
    void when_custom_lock_provider_present_expect_auto_configuration_backs_off() {
      final net.javacrumbs.shedlock.core.LockProvider customLockProvider =
          mock(net.javacrumbs.shedlock.core.LockProvider.class);

      JdbcShedlockAutoConfigurationTest.this.contextRunner
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
