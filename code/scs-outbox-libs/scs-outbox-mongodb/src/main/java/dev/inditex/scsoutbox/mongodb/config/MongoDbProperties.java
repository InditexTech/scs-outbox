package dev.inditex.scsoutbox.mongodb.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("scs-outbox.mongodb")
public class MongoDbProperties {

  private static final String DEFAULT_COLLECTION_NAME = "SCS_OUTBOX";

  @Getter
  private final String collectionName;

  public MongoDbProperties() {
    this.collectionName = DEFAULT_COLLECTION_NAME;
  }

  @ConstructorBinding
  public MongoDbProperties(final String collectionName) {
    if (collectionName == null || collectionName.isEmpty()) {
      this.collectionName = DEFAULT_COLLECTION_NAME;
    } else if (collectionName.contains(" ")) {
      throw new IllegalArgumentException("Collection name cannot contain spaces");
    } else {
      this.collectionName = collectionName;
    }
  }

}
