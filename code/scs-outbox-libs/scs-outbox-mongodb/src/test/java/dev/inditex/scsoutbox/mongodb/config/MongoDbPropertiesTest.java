package dev.inditex.scsoutbox.mongodb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

class MongoDbPropertiesTest {

  public static final String DEFAULT_COLLECTION_NAME = "SCS_OUTBOX";

  @Test
  void default_values() {
    assertEquals(DEFAULT_COLLECTION_NAME, new MongoDbProperties().getCollectionName());
    assertEquals(DEFAULT_COLLECTION_NAME, new MongoDbProperties(null).getCollectionName());
    assertEquals(DEFAULT_COLLECTION_NAME, new MongoDbProperties("").getCollectionName());
  }

  @Test
  void with_invalid_collection_name() {
    assertThrows(IllegalArgumentException.class,
        () -> new MongoDbProperties("VALUE WITH SPACES"));
  }

  @Test
  void with_collection_name() {
    assertEquals("SCS_OUTBOX_ARCHIVE_TEST",
        new MongoDbProperties("SCS_OUTBOX_ARCHIVE_TEST").getCollectionName());
  }

  @Nested
  @SpringBootTest(classes = {MongoDbPropertiesTest.class})
  @EnableConfigurationProperties(MongoDbProperties.class)
  class SpringBootTestWithoutProperties {
    @Autowired
    private MongoDbProperties properties;

    @Test
    void default_values() {
      assertEquals(DEFAULT_COLLECTION_NAME, this.properties.getCollectionName());
    }
  }

  @Nested
  @SpringBootTest(classes = {MongoDbPropertiesTest.class},
      properties = {
          "scs-outbox.mongodb.collection-name=SCS_OUTBOX_ARCHIVE_TEST",
      })
  @EnableConfigurationProperties(MongoDbProperties.class)
  class SpringBootTestWithProperties {
    @Autowired
    private MongoDbProperties properties;

    @Test
    void property_values() {
      assertEquals("SCS_OUTBOX_ARCHIVE_TEST", this.properties.getCollectionName());
    }
  }

}
