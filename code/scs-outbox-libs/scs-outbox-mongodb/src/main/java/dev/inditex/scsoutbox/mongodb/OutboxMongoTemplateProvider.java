package dev.inditex.scsoutbox.mongodb;

import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Provides MongoTemplate instances for different outbox operations. This allows using different MongoDB connections for capture
 * (transactional) and publishing (scheduled) operations.
 */
public interface OutboxMongoTemplateProvider {

  /**
   * Gets the MongoTemplate used for message capture operations. This MongoTemplate is used during application transactions.
   *
   * @return the capture MongoTemplate (never null)
   */
  MongoTemplate getPrimary();

  /**
   * Gets the MongoTemplate used for message publishing operations. This MongoTemplate is used during scheduled publishing tasks. Returns
   * the same as capture MongoTemplate if no dedicated publishing MongoTemplate is configured.
   *
   * @return the publishing MongoTemplate (never null)
   */
  MongoTemplate getDedicatedForPublishing();

}
