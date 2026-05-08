package dev.inditex.scsoutbox.mongodb.config;

import static dev.inditex.scsoutbox.config.OutboxAutoConfiguration.OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME;
import static dev.inditex.scsoutbox.config.OutboxAutoConfiguration.PUBLISHING_OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME;

import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.config.OutboxAutoConfiguration;
import dev.inditex.scsoutbox.mongodb.MongoDbOutboxMessageRepository;
import dev.inditex.scsoutbox.mongodb.MongoDbOutboxTemplateProvider;
import dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Auto-configuration for MongoDB Outbox. Sets up the necessary beans for MongoDB-based outbox message repository.
 *
 * <p>This configuration creates an OutboxMongoTemplateProvider to coordinate MongoTemplate usage and two qualified repositories:
 * outboxMessageRepository for message capture and publishingOutboxMessageRepository for message publishing.
 */
@Slf4j
@AutoConfiguration(before = OutboxAutoConfiguration.class)
@EnableConfigurationProperties({MongoDbProperties.class})
public class MongoDbOutboxAutoConfiguration {

  /**
   * Bean name for the dedicated MongoTemplate used for outbox publishing operations. If a bean with this name exists, it will be used for
   * publishing and archive operations.
   */
  public static final String OUTBOX_PUBLISHING_MONGOTEMPLATE_BEAN_NAME = "outboxPublishingMongoTemplate";

  /**
   * Creates the MongoTemplate provider that coordinates which MongoTemplate to use for each operation. The publishing MongoTemplate is
   * optional - if not present, the primary MongoTemplate is used for both operations.
   *
   * @param captureTemplate the primary MongoTemplate (always present)
   * @param publishingTemplate optional dedicated MongoTemplate for publishing operations
   * @return the OutboxMongoTemplateProvider instance
   */
  @Bean
  public OutboxMongoTemplateProvider outboxMongoTemplateProvider(
      final MongoTemplate captureTemplate,
      @Qualifier(OUTBOX_PUBLISHING_MONGOTEMPLATE_BEAN_NAME) @Autowired(required = false) final MongoTemplate publishingTemplate) {

    return new MongoDbOutboxTemplateProvider(captureTemplate, publishingTemplate);
  }

  /**
   * Creates the OutboxMessageRepository for message capture using the capture MongoTemplate from the provider. This repository is used
   * during application transactions to capture outbox messages.
   *
   * @param templateProvider the provider that coordinates MongoTemplate usage
   * @param outboxMessageSerializer serializer used to persist outbox payloads and headers
   * @param properties MongoDB configuration properties
   * @return the capture repository instance
   */
  @Bean(OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME)
  public OutboxMessageRepository outboxMessageRepository(
      final OutboxMongoTemplateProvider templateProvider,
      final OutboxMessageSerializer outboxMessageSerializer,
      final MongoDbProperties properties) {

    final MongoTemplate mongoTemplate = templateProvider.getPrimary();

    log.info("Creating capture OutboxMessageRepository");

    return new MongoDbOutboxMessageRepository(mongoTemplate, outboxMessageSerializer, properties);
  }

  /**
   * Creates the OutboxMessageRepository for message publishing using the publishing MongoTemplate from the provider. This repository is
   * used during scheduled tasks to publish outbox messages. It will use a dedicated MongoTemplate if configured, otherwise it will use the
   * primary MongoTemplate.
   *
   * @param templateProvider the provider that coordinates MongoTemplate usage
   * @param outboxMessageSerializer serializer used to persist outbox payloads and headers
   * @param properties MongoDB configuration properties
   * @return the publishing repository instance
   */
  @Bean(PUBLISHING_OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME)
  public OutboxMessageRepository publishingOutboxMessageRepository(
      final OutboxMongoTemplateProvider templateProvider,
      final OutboxMessageSerializer outboxMessageSerializer,
      final MongoDbProperties properties) {

    final MongoTemplate mongoTemplate = templateProvider.getDedicatedForPublishing();

    log.info("Creating publishing OutboxMessageRepository");

    return new MongoDbOutboxMessageRepository(mongoTemplate, outboxMessageSerializer, properties);
  }

}
