package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sql.DataSource;

import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test validating the dedicated DataSource functionality for outbox publishing. This test configures a separate DataSource
 * specifically for publishing operations to ensure isolation from the main application DataSource.
 */
@SpringBootTest(
    classes = {DedicatedPublishingDataSourceIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.batch-size=1",
        "scs-outbox.publishing.after-commit=false",
        "scs-outbox.publishing.scheduler.fixed-rate=1000",
    })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class DedicatedPublishingDataSourceIT {

  public static final String OUTBOX_PUBLISHING_DATA_SOURCE = "outboxPublishingDataSource";

  @Autowired
  private OutboxDataSourceProvider dataSourceProvider;

  @Autowired
  private DataSource primaryDataSource;

  @Autowired
  @Qualifier(OUTBOX_PUBLISHING_DATA_SOURCE)
  private DataSource publishingDataSource;

  @Autowired
  private StreamBridge streamBridge;

  @MockitoBean
  private Consumer<String> myConsumer;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Test
  void provider_uses_dedicated_datasource_for_publishing() {
    assertThat(this.dataSourceProvider.getPrimary())
        .describedAs("Provider should expose different instances when a dedicated publishing DataSource is configured")
        .isNotSameAs(this.dataSourceProvider.getDedicatedForPublishing());
  }

  @Test
  void capture_uses_primary_datasource() {
    assertThat(this.dataSourceProvider.getPrimary())
        .describedAs("Capture operations should use the primary DataSource")
        .isSameAs(this.primaryDataSource);
  }

  @Test
  void publishing_uses_dedicated_datasource() {
    assertThat(this.dataSourceProvider.getDedicatedForPublishing())
        .describedAs("Publishing operations should use the dedicated DataSource")
        .isSameAs(this.publishingDataSource);
  }

  @Test
  void datasources_are_different_instances() {
    assertThat(this.dataSourceProvider.getPrimary())
        .describedAs("Capture and publishing should use different DataSource instances for isolation")
        .isNotSameAs(this.dataSourceProvider.getDedicatedForPublishing());
  }

  @Test
  void primary_datasource_exists_and_is_hikari() {
    assertThat(this.primaryDataSource).isInstanceOf(HikariDataSource.class);
    final HikariDataSource hikariDs = (HikariDataSource) this.primaryDataSource;
    assertThat(hikariDs.getPoolName())
        .describedAs("Primary DataSource should be HikariCP with a pool name")
        .isEqualTo("PrimaryPool");
  }

  @Test
  void publication_datasource_has_correct_pool_name() {
    assertThat(this.publishingDataSource).isInstanceOf(HikariDataSource.class);
    final HikariDataSource hikariDs = (HikariDataSource) this.publishingDataSource;
    assertThat(hikariDs.getPoolName())
        .describedAs("Publishing DataSource should have the configured pool name")
        .isEqualTo("PublicationPool");
  }

  @Test
  void messages_are_captured_and_published_with_dedicated_datasource() throws Exception {
    // Send message in transaction (uses primary/capture DataSource)
    this.transactionTemplate.executeWithoutResult(status -> {
      this.streamBridge.send("myConsumer-in-0", "test-message-dedicated-pool");
    });

    // Wait for message to be published (uses dedicated/publishing DataSource)
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(this.myConsumer).accept("test-message-dedicated-pool"));
  }

  @Configuration
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  @Slf4j
  static class TestConfig {

    @Bean
    public Consumer<String> myConsumer() {
      return value -> log.info("Message received: {}", value);
    }

    /**
     * Primary DataSource configuration for application transactions (capture). Uses JdbcConnectionDetails from Spring Boot's service
     * connection (docker-compose).
     */
    @Bean
    @Primary
    public HikariDataSource dataSource(
        final org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails connectionDetails) {
      final HikariDataSource primary = new HikariDataSource();
      primary.setJdbcUrl(connectionDetails.getJdbcUrl());
      primary.setUsername(connectionDetails.getUsername());
      primary.setPassword(connectionDetails.getPassword());
      primary.setPoolName("PrimaryPool");
      primary.setMaximumPoolSize(10);
      primary.setMinimumIdle(2);
      return primary;
    }

    /**
     * Dedicated DataSource configuration for outbox publishing. Uses the same connection details but creates a separate connection pool.
     */
    @Bean(name = OUTBOX_PUBLISHING_DATA_SOURCE)
    public HikariDataSource outboxPublishingDataSource(
        final org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails connectionDetails) {
      final HikariDataSource publication = new HikariDataSource();
      publication.setJdbcUrl(connectionDetails.getJdbcUrl());
      publication.setUsername(connectionDetails.getUsername());
      publication.setPassword(connectionDetails.getPassword());
      publication.setPoolName("PublicationPool");
      publication.setMaximumPoolSize(5);
      publication.setMinimumIdle(1);
      return publication;
    }
  }
}
