package dev.inditex.scsoutbox.it.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test validating the dedicated MongoTemplate functionality for outbox publishing. This test configures a separate
 * MongoTemplate specifically for publishing operations to ensure isolation from the main application MongoTemplate.
 */
@SpringBootTest(
    classes = {DedicatedPublishingMongoTemplateIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.batch-size=1",
        "scs-outbox.publishing.after-commit=false",
        "scs-outbox.publishing.scheduler.fixed-rate=1000",
    })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class DedicatedPublishingMongoTemplateIT {

  public static final String OUTBOX_PUBLISHING_MONGO_TEMPLATE = "outboxPublishingMongoTemplate";

  @Autowired
  private OutboxMongoTemplateProvider templateProvider;

  @Autowired
  private MongoTemplate primaryMongoTemplate;

  @Autowired
  @Qualifier(OUTBOX_PUBLISHING_MONGO_TEMPLATE)
  private MongoTemplate publishingMongoTemplate;

  @Autowired
  private StreamBridge streamBridge;

  @MockitoBean
  private Consumer<String> myConsumer;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Test
  void provider_uses_dedicated_template_for_publishing() {
    assertThat(this.templateProvider.getPrimary())
        .describedAs("Provider should expose different instances when a dedicated publishing MongoTemplate is configured")
        .isNotSameAs(this.templateProvider.getDedicatedForPublishing());
  }

  @Test
  void capture_uses_primary_template() {
    assertThat(this.templateProvider.getPrimary())
        .describedAs("Capture operations should use the primary MongoTemplate")
        .isSameAs(this.primaryMongoTemplate);
  }

  @Test
  void publishing_uses_dedicated_template() {
    assertThat(this.templateProvider.getDedicatedForPublishing())
        .describedAs("Publishing operations should use the dedicated MongoTemplate")
        .isSameAs(this.publishingMongoTemplate);
  }

  @Test
  void templates_are_different_instances() {
    assertThat(this.templateProvider.getPrimary())
        .describedAs("Capture and publishing should use different MongoTemplate instances for isolation")
        .isNotSameAs(this.templateProvider.getDedicatedForPublishing());
  }

  @Test
  void messages_are_captured_and_published_with_dedicated_template() {
    // Send message in transaction (uses primary/capture MongoTemplate)
    this.transactionTemplate.executeWithoutResult(status -> this.streamBridge.send("myConsumer-in-0", "test-message-dedicated-template"));

    // Wait for message to be published (uses dedicated/publishing MongoTemplate)
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(this.myConsumer).accept("test-message-dedicated-template"));
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

    @Bean
    MongoTransactionManager transactionManager(final MongoDatabaseFactory dbFactory) {
      return new MongoTransactionManager(dbFactory);
    }

    /**
     * Primary MongoTemplate configuration for application transactions (capture). Uses MongoConnectionDetails from Spring Boot's service
     * connection (docker-compose).
     */
    @Bean
    @Primary
    public MongoTemplate mongoTemplate(final MongoConnectionDetails connectionDetails) {
      final MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(
          connectionDetails.getConnectionString());
      return new MongoTemplate(factory);
    }

    /**
     * Dedicated MongoTemplate configuration for outbox publishing. Uses the same connection details but creates a separate MongoTemplate
     * instance.
     */
    @Bean(name = OUTBOX_PUBLISHING_MONGO_TEMPLATE)
    public MongoTemplate outboxPublishingMongoTemplate(final MongoConnectionDetails connectionDetails) {
      final MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(
          connectionDetails.getConnectionString());
      return new MongoTemplate(factory);
    }
  }
}
