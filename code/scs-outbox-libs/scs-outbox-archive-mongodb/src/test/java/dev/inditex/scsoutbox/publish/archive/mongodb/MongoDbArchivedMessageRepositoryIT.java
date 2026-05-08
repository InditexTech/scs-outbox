package dev.inditex.scsoutbox.publish.archive.mongodb;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.publish.archive.ArchivedMessage;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.mongodb.config.MongoDbArchiveProperties;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.test.ContainerImages;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class MongoDbArchivedMessageRepositoryIT {
  @Container
  public MongoDBContainer mongoDBContainer =
      new MongoDBContainer(ContainerImages.MONGO);

  private MongoDbArchivedMessageRepository repository;

  private MongoTemplate mongoTemplate;

  private MongoDbArchiveProperties mongoDbProperties;

  private MongoClient mongoClient;

  @BeforeEach
  void setup() {
    this.mongoDBContainer.start();
    final String databaseName = "default";
    final String uri = this.mongoDBContainer.getConnectionString() + "/" + databaseName;
    final ConnectionString connectionString = new ConnectionString(uri);
    final MongoClientSettings settings = MongoClientSettings.builder()
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .applyConnectionString(connectionString)
        .build();
    this.mongoClient = MongoClients.create(settings);
    final MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(
        this.mongoClient, connectionString.getDatabase());
    this.mongoTemplate = new MongoTemplate(factory);
    this.mongoDbProperties = new MongoDbArchiveProperties(null);
    this.repository = new MongoDbArchivedMessageRepository(
        this.mongoTemplate, new ArchivedMessageSerializer(new JavaSerialization(), new JsonHeadersMapper()), this.mongoDbProperties);
  }

  @AfterEach
  void stopContainer() {
    this.mongoDBContainer.stop();
  }

  @AfterEach
  void closeMongoClient() {
    this.mongoClient.close();
  }

  @Test
  void save() {
    final ArchivedMessage archivedMessage = anArchivedMessage();
    this.repository.save(archivedMessage);
    final ArchivedMessageDocument found =
        this.mongoTemplate.findById(
            archivedMessage.getId().toString(),
            ArchivedMessageDocument.class,
            this.mongoDbProperties.getCollectionName());
    assertNotNull(found);
  }

  public static ArchivedMessage anArchivedMessage() {
    return ArchivedMessage.builder()
        .id(UUID.randomUUID())
        .archivedAt(Instant.now())
        .capturedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
        .destination("destination")
        .contentType("application/json")
        .payload("payload")
        .headers(Map.of())
        .build();
  }
}
