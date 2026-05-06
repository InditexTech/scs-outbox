package dev.inditex.scsoutbox.mongodb;

import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.mongodb.config.MongoDbProperties;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;
import dev.inditex.scsoutbox.test.ContainerImages;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@Testcontainers
class MongoDbOutboxMessageRepositoryIT {

  @Container
  public static MongoDBContainer mongoDBContainer =
      new MongoDBContainer(ContainerImages.MONGO);

  private static final Instant BASE_TIME = Instant.parse("2025-01-01T10:00:00Z");

  private static final String DATABASE_NAME = "default";

  private MongoClient mongoClient;

  private MongoDbOutboxMessageRepository repository;

  @BeforeAll
  static void startContainer() {
    mongoDBContainer.start();
  }

  @AfterAll
  static void stopContainer() {
    mongoDBContainer.stop();
  }

  @BeforeEach
  void setup() {
    final String uri = mongoDBContainer.getConnectionString() + "/" + DATABASE_NAME;
    final ConnectionString connectionString = new ConnectionString(uri);
    final MongoClientSettings settings = MongoClientSettings.builder()
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .applyConnectionString(connectionString)
        .build();
    this.mongoClient = MongoClients.create(settings);
    final MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(
        this.mongoClient, connectionString.getDatabase());
    final MongoTemplate mongoTemplate = new MongoTemplate(factory);

    this.repository = new MongoDbOutboxMessageRepository(
        mongoTemplate,
        new OutboxMessageSerializer(new JavaSerialization(), new JsonHeadersMapper()),
        new MongoDbProperties());
  }

  @AfterEach
  void closeMongoClient() {
    this.mongoClient.getDatabase(DATABASE_NAME).drop();
    this.mongoClient.close();
  }

  @Nested
  class Count {

    @Test
    void when_messages_saved_expect_correct_count() {
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessage(BASE_TIME));
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessage(BASE_TIME.plusSeconds(1)));
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessage(BASE_TIME.plusSeconds(2)));

      assertThat(MongoDbOutboxMessageRepositoryIT.this.repository.count()).isEqualTo(3L);
    }
  }

  @Nested
  class EstimatedCount {

    @Test
    void when_messages_saved_expect_estimated_count_not_zero() {
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessage(BASE_TIME));
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessage(BASE_TIME.plusSeconds(1)));
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessage(BASE_TIME.plusSeconds(2)));

      assertThat(MongoDbOutboxMessageRepositoryIT.this.repository.estimatedCount()).isEqualTo(3L);
    }
  }

  @Nested
  class FindAllOrderByCapturedAt {

    @Test
    void when_no_messages_saved_expect_empty_list() {
      assertThat(MongoDbOutboxMessageRepositoryIT.this.repository.findAllOrderByCapturedAt(UNLIMITED)).isEmpty();
    }

    @Test
    void when_messages_saved_expect_all_returned_ordered_by_captured_at() {
      final OutboxMessage message1 = anOutboxMessage(BASE_TIME);
      final OutboxMessage message2 = anOutboxMessage(BASE_TIME.plusSeconds(1));
      MongoDbOutboxMessageRepositoryIT.this.repository.save(message1);
      MongoDbOutboxMessageRepositoryIT.this.repository.save(message2);

      final List<OutboxMessage> result =
          MongoDbOutboxMessageRepositoryIT.this.repository.findAllOrderByCapturedAt(UNLIMITED);

      assertThat(result).hasSize(2).extracting(OutboxMessage::getId)
          .containsExactly(message1.getId(), message2.getId());
    }

    @Test
    void when_max_results_specified_expect_limited_results_returned() {
      for (int i = 0; i < 5; i++) {
        MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessage(BASE_TIME.plusSeconds(i)));
      }

      final List<OutboxMessage> result =
          MongoDbOutboxMessageRepositoryIT.this.repository.findAllOrderByCapturedAt(3);

      assertThat(result).hasSize(3);
    }
  }

  @Nested
  class Save {

    @Test
    void when_message_saved_expect_roundtrip_successful() {
      final OutboxMessage outboxMessage = anOutboxMessage(BASE_TIME);

      MongoDbOutboxMessageRepositoryIT.this.repository.save(outboxMessage);

      final List<OutboxMessage> result =
          MongoDbOutboxMessageRepositoryIT.this.repository.findAllOrderByCapturedAt(UNLIMITED);
      assertThat(result).hasSize(1);
      assertThat(result.get(0)).isEqualTo(outboxMessage);
    }
  }

  @Nested
  class Delete {

    @Test
    void when_message_deleted_expect_not_found_in_repository() {
      final OutboxMessage outboxMessage = anOutboxMessage(BASE_TIME);
      MongoDbOutboxMessageRepositoryIT.this.repository.save(outboxMessage);

      MongoDbOutboxMessageRepositoryIT.this.repository.delete(outboxMessage);

      assertThat(MongoDbOutboxMessageRepositoryIT.this.repository.findAllOrderByCapturedAt(UNLIMITED)).isEmpty();
    }
  }

  @Nested
  class FindAllOrderByCapturedAtExcludingDestinations {

    @Test
    void when_specific_destinations_excluded_expect_only_remaining_returned() {
      final OutboxMessage message1 = anOutboxMessageWithDestination("destination1", BASE_TIME);
      final OutboxMessage message2 = anOutboxMessageWithDestination("destination2", BASE_TIME.plusSeconds(1));
      final OutboxMessage message3 = anOutboxMessageWithDestination("destination3", BASE_TIME.plusSeconds(2));
      final OutboxMessage message4 = anOutboxMessageWithDestination("destination4", BASE_TIME.plusSeconds(3));
      MongoDbOutboxMessageRepositoryIT.this.repository.save(message1);
      MongoDbOutboxMessageRepositoryIT.this.repository.save(message2);
      MongoDbOutboxMessageRepositoryIT.this.repository.save(message3);
      MongoDbOutboxMessageRepositoryIT.this.repository.save(message4);

      final List<OutboxMessage> result = MongoDbOutboxMessageRepositoryIT.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(Set.of("destination1", "destination2"), UNLIMITED);

      assertThat(result).hasSize(2)
          .extracting(OutboxMessage::getDestination)
          .containsExactlyInAnyOrder("destination3", "destination4");
    }

    @Test
    void when_max_results_specified_expect_limited_results_returned() {
      for (int i = 1; i <= 5; i++) {
        MongoDbOutboxMessageRepositoryIT.this.repository
            .save(anOutboxMessageWithDestination("destination" + i, BASE_TIME.plusSeconds(i)));
      }

      final List<OutboxMessage> result = MongoDbOutboxMessageRepositoryIT.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(Set.of("destination6"), 3);

      assertThat(result).hasSize(3);
    }

    @Test
    void when_no_limit_specified_expect_all_non_excluded_returned() {
      for (int i = 1; i <= 5; i++) {
        MongoDbOutboxMessageRepositoryIT.this.repository
            .save(anOutboxMessageWithDestination("destination" + i, BASE_TIME.plusSeconds(i)));
      }

      final List<OutboxMessage> result = MongoDbOutboxMessageRepositoryIT.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(Set.of("destination6"), UNLIMITED);

      assertThat(result).hasSize(5);
    }

    @Test
    void when_empty_exclusions_expect_all_messages_returned() {
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessageWithDestination("destination1", BASE_TIME));
      MongoDbOutboxMessageRepositoryIT.this.repository
          .save(anOutboxMessageWithDestination("destination2", BASE_TIME.plusSeconds(1)));

      final List<OutboxMessage> result = MongoDbOutboxMessageRepositoryIT.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(Set.of(), UNLIMITED);

      assertThat(result).hasSize(2);
    }

    @Test
    void when_null_exclusions_expect_all_messages_returned() {
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessageWithDestination("destination1", BASE_TIME));
      MongoDbOutboxMessageRepositoryIT.this.repository
          .save(anOutboxMessageWithDestination("destination2", BASE_TIME.plusSeconds(1)));

      final List<OutboxMessage> result = MongoDbOutboxMessageRepositoryIT.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(null, UNLIMITED);

      assertThat(result).hasSize(2);
    }

    @Test
    void when_all_destinations_excluded_expect_empty_list() {
      MongoDbOutboxMessageRepositoryIT.this.repository.save(anOutboxMessageWithDestination("destination1", BASE_TIME));
      MongoDbOutboxMessageRepositoryIT.this.repository
          .save(anOutboxMessageWithDestination("destination2", BASE_TIME.plusSeconds(1)));

      final List<OutboxMessage> result = MongoDbOutboxMessageRepositoryIT.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(Set.of("destination1", "destination2"), UNLIMITED);

      assertThat(result).isEmpty();
    }
  }

  private static OutboxMessage anOutboxMessage(Instant capturedAt) {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(capturedAt)
        .destination("destination")
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of("contentType", "value"))
        .build();
  }

  private static OutboxMessage anOutboxMessageWithDestination(String destination, Instant capturedAt) {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(capturedAt)
        .destination(destination)
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of("contentType", "value"))
        .build();
  }
}
