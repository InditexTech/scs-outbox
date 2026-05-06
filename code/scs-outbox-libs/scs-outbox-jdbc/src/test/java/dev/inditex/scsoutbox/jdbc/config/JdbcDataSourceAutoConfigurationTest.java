package dev.inditex.scsoutbox.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.jdbc.JdbcOutboxDataSourceProvider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcDataSourceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(JdbcDataSourceAutoConfiguration.class));

  @Nested
  class OutboxDataSourceProvider {

    @Test
    void when_primary_datasource_present_expect_provider_created() {
      JdbcDataSourceAutoConfigurationTest.this.contextRunner
          .withBean(DataSource.class, JdbcDataSourceAutoConfigurationTest::createTestDataSource)
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider.class);
            assertThat(context.getBean(dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider.class))
                .isInstanceOf(JdbcOutboxDataSourceProvider.class);
          });
    }

    @Test
    void when_no_dedicated_datasource_expect_provider_uses_primary_for_both() {
      JdbcDataSourceAutoConfigurationTest.this.contextRunner
          .withBean(DataSource.class, JdbcDataSourceAutoConfigurationTest::createTestDataSource)
          .run(context -> {
            assertThat(context).hasNotFailed();
            final var provider = context.getBean(dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider.class);
            assertThat(provider.getPrimary()).isSameAs(provider.getDedicatedForPublishing());
          });
    }

    @Test
    void when_dedicated_datasource_present_expect_provider_uses_different_datasources() {
      JdbcDataSourceAutoConfigurationTest.this.contextRunner
          .withBean("captureDataSource", DataSource.class, JdbcDataSourceAutoConfigurationTest::createTestDataSource)
          .withBean("outboxPublishingDataSource", DataSource.class, JdbcDataSourceAutoConfigurationTest::createTestDataSource)
          .run(context -> {
            assertThat(context).hasNotFailed();
            final var provider = context.getBean(dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider.class);
            assertThat(provider.getPrimary()).isNotSameAs(provider.getDedicatedForPublishing());
          });
    }

    @Test
    void when_no_datasource_present_expect_context_fails() {
      JdbcDataSourceAutoConfigurationTest.this.contextRunner
          .run(context -> assertThat(context).hasFailed());
    }
  }

  private static DataSource createTestDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }
}
