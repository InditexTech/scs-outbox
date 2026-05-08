package dev.inditex.scsoutbox.mongodb;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB implementation of {@link OutboxMongoTemplateProvider}. Manages MongoTemplate instances for capture and publishing operations,
 * with optional dedicated publishing MongoTemplate.
 */
@Slf4j
public class MongoDbOutboxTemplateProvider implements OutboxMongoTemplateProvider {

  private final MongoTemplate primaryTemplate; // capture

  private final MongoTemplate publishingTemplate; // dedicated or same as primary

  /**
   * Creates a provider with a mandatory capture MongoTemplate and an optional publishing MongoTemplate.
   *
   * @param captureTemplate the MongoTemplate for capture operations (required, typically the primary MongoTemplate)
   * @param publishingTemplate the MongoTemplate for publishing operations (optional, can be null)
   */
  public MongoDbOutboxTemplateProvider(
      MongoTemplate captureTemplate,
      MongoTemplate publishingTemplate) {

    this.primaryTemplate = Objects.requireNonNull(captureTemplate,
        "Capture MongoTemplate is required");

    if (publishingTemplate == null) {
      this.publishingTemplate = this.primaryTemplate;
      log.info(
          "No dedicated publishing MongoTemplate configured. Using primary MongoTemplate for both capture and publishing operations");
    } else {
      this.publishingTemplate = publishingTemplate;
      log.info("Using dedicated MongoTemplates: capture and publishing operations are isolated");
    }
  }

  @Override
  public MongoTemplate getPrimary() {
    return this.primaryTemplate;
  }

  @Override
  public MongoTemplate getDedicatedForPublishing() {
    return this.publishingTemplate;
  }
}
