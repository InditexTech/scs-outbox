package dev.inditex.scsoutbox.publish.archive.mongodb.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("scs-outbox.publishing.archive.mongodb")
public class MongoDbArchiveProperties {

  private static final String DEFAULT_COLLECTION_NAME = "SCS_OUTBOX_ARCHIVE";

  @Getter
  private final String collectionName;

  public MongoDbArchiveProperties() {
    this.collectionName = DEFAULT_COLLECTION_NAME;
  }

  @ConstructorBinding
  public MongoDbArchiveProperties(final String collectionName) {
    if (collectionName == null || collectionName.isEmpty()) {
      this.collectionName = DEFAULT_COLLECTION_NAME;
    } else if (collectionName.contains(" ")) {
      throw new IllegalArgumentException("Collection name cannot contain spaces");
    } else {
      this.collectionName = collectionName;
    }
  }

}
