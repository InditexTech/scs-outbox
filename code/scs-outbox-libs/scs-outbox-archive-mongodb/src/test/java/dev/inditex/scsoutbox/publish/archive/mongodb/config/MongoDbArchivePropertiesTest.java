package dev.inditex.scsoutbox.publish.archive.mongodb.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

class MongoDbArchivePropertiesTest {

  public static final String DEFAULT_COLLECTION_NAME = "SCS_OUTBOX_ARCHIVE";

  @Test
  void default_values() {
    assertEquals(DEFAULT_COLLECTION_NAME, new MongoDbArchiveProperties().getCollectionName());
    assertEquals(DEFAULT_COLLECTION_NAME, new MongoDbArchiveProperties(null).getCollectionName());
    assertEquals(DEFAULT_COLLECTION_NAME, new MongoDbArchiveProperties("").getCollectionName());
  }

  @Test
  void with_invalid_collection_name() {
    assertThrows(IllegalArgumentException.class,
        () -> new MongoDbArchiveProperties("VALUE WITH SPACES"));
  }

  @Test
  void with_collection_name() {
    assertEquals("SCS_OUTBOX_ARCHIVE_TEST",
        new MongoDbArchiveProperties("SCS_OUTBOX_ARCHIVE_TEST").getCollectionName());
  }

  @Nested
  @SpringBootTest(classes = {MongoDbArchivePropertiesTest.class})
  @EnableConfigurationProperties(MongoDbArchiveProperties.class)
  class SpringBootTestWithoutProperties {
    @Autowired
    private MongoDbArchiveProperties properties;

    @Test
    void default_values() {
      assertEquals(DEFAULT_COLLECTION_NAME, this.properties.getCollectionName());
    }
  }

  @Nested
  @SpringBootTest(classes = {MongoDbArchivePropertiesTest.class},
      properties = {
          "scs-outbox.publishing.archive.mongodb.collection-name=SCS_OUTBOX_ARCHIVE_TEST",
      })
  @EnableConfigurationProperties(MongoDbArchiveProperties.class)
  class SpringBootTestWithProperties {
    @Autowired
    private MongoDbArchiveProperties properties;

    @Test
    void property_values() {
      assertEquals("SCS_OUTBOX_ARCHIVE_TEST", this.properties.getCollectionName());
    }
  }

}
