package dev.inditex.scsoutbox.publish.archive.mongodb.config;

import dev.inditex.scsoutbox.mongodb.OutboxMongoTemplateProvider;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.config.ArchiveAutoConfiguration;
import dev.inditex.scsoutbox.publish.archive.mongodb.MongoDbArchivedMessageRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Auto-configuration for MongoDB-based archived message repository. Archive operations always use the publishing MongoTemplate since
 * archiving happens during publishing tasks.
 */
@Slf4j
@ConditionalOnBean(ArchiveAutoConfiguration.class)
@AutoConfiguration
@EnableConfigurationProperties(MongoDbArchiveProperties.class)
public class MongoDbArchiveAutoConfiguration {

  /**
   * Creates the archived message repository using the publishing MongoTemplate from the provider. Archive operations always use the
   * publishing MongoTemplate since archiving happens during publishing tasks.
   *
   * @param templateProvider the provider that coordinates MongoTemplate usage
   * @param archivedMessageSerializer the serializer for archived messages
   * @param properties archive configuration properties
   * @return the archived message repository instance
   */
  @Bean
  public ArchivedMessageRepository archivedMessageRepository(
      final OutboxMongoTemplateProvider templateProvider,
      final ArchivedMessageSerializer archivedMessageSerializer,
      final MongoDbArchiveProperties properties) {

    final MongoTemplate mongoTemplate = templateProvider.getDedicatedForPublishing();

    log.info("Creating archived message repository");

    return new MongoDbArchivedMessageRepository(mongoTemplate, archivedMessageSerializer, properties);
  }
}
