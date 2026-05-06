package dev.inditex.scsoutbox.mongodb;

import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.mongodb.config.MongoDbProperties;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer.SerializedOutboxMessage;
import dev.inditex.scsoutbox.serialization.SerializationEngine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class MongoDbOutboxMessageRepositoryTest {

  private MongoDbOutboxMessageRepository repository;

  @Mock
  private MongoTemplate mongoTemplate;

  @Mock
  private OutboxMessageSerializer serializer;

  @Captor
  private ArgumentCaptor<OutboxMessageDocument> documentCaptor;

  @Captor
  private ArgumentCaptor<Query> queryCaptor;

  @Captor
  private ArgumentCaptor<SerializedOutboxMessage> serializedMessageCaptor;

  @BeforeEach
  void beforeEach() {
    this.repository = new MongoDbOutboxMessageRepository(this.mongoTemplate, this.serializer, new MongoDbProperties());
  }

  @Nested
  class Save {

    @Test
    void when_message_saved_expect_serializer_called_and_document_inserted() {
      final OutboxMessage message = aMessage();
      final SerializedOutboxMessage serialized = aSerializedMessage(message);
      when(MongoDbOutboxMessageRepositoryTest.this.serializer.serialize(message)).thenReturn(serialized);

      MongoDbOutboxMessageRepositoryTest.this.repository.save(message);

      verify(MongoDbOutboxMessageRepositoryTest.this.serializer, times(1)).serialize(message);
      verify(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate, times(1))
          .insert(MongoDbOutboxMessageRepositoryTest.this.documentCaptor.capture(), eq("SCS_OUTBOX"));
      final OutboxMessageDocument inserted = MongoDbOutboxMessageRepositoryTest.this.documentCaptor.getValue();
      assertThat(inserted.getId()).isEqualTo(serialized.getId().toString());
      assertThat(inserted.getDestination()).isEqualTo(serialized.getDestination());
      assertThat(inserted.getBindingName()).isEqualTo(serialized.getBindingName());
      assertThat(inserted.getCapturedAt()).isEqualTo(serialized.getCapturedAt());
      assertThat(inserted.getHeaders()).isEqualTo(serialized.getHeaders());
      assertThat(inserted.getPayload()).isEqualTo(serialized.getPayload());
    }

    @Test
    void when_message_saved_with_custom_collection_expect_insert_uses_custom_name() {
      final OutboxMessage message = aMessage();
      when(MongoDbOutboxMessageRepositoryTest.this.serializer.serialize(any())).thenReturn(aSerializedMessage(message));
      final MongoDbOutboxMessageRepository customRepository = new MongoDbOutboxMessageRepository(
          MongoDbOutboxMessageRepositoryTest.this.mongoTemplate,
          MongoDbOutboxMessageRepositoryTest.this.serializer,
          new MongoDbProperties("CUSTOM_OUTBOX"));

      customRepository.save(message);

      verify(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate, times(1))
          .insert(any(OutboxMessageDocument.class), eq("CUSTOM_OUTBOX"));
    }
  }

  @Nested
  class Delete {

    @Test
    void when_message_deleted_expect_remove_with_id_query_on_correct_collection() {
      final OutboxMessage message = aMessage();

      MongoDbOutboxMessageRepositoryTest.this.repository.delete(message);

      verify(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate, times(1))
          .remove(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.capture(), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX"));
      assertThat(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.getValue().getQueryObject().getString("id"))
          .isEqualTo(message.getId().toString());
    }
  }

  @Nested
  class Count {

    @Test
    void when_count_called_expect_delegates_to_mongo_template() {
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .count(any(Query.class), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX"))).thenReturn(5L);

      assertThat(MongoDbOutboxMessageRepositoryTest.this.repository.count()).isEqualTo(5L);
      verify(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate, times(1))
          .count(any(Query.class), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX"));
    }
  }

  @Nested
  class EstimatedCount {

    @Test
    void when_estimated_count_called_expect_delegates_to_mongo_template() {
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate.estimatedCount("SCS_OUTBOX")).thenReturn(42L);

      assertThat(MongoDbOutboxMessageRepositoryTest.this.repository.estimatedCount()).isEqualTo(42L);
      verify(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate, times(1)).estimatedCount("SCS_OUTBOX");
    }
  }

  @Nested
  class FindAllOrderByCapturedAt {

    @Test
    void when_no_documents_expect_empty_list() {
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(any(Query.class), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX"))).thenReturn(List.of());

      assertThat(MongoDbOutboxMessageRepositoryTest.this.repository.findAllOrderByCapturedAt(UNLIMITED)).isEmpty();
    }

    @Test
    void when_documents_exist_expect_deserialized_messages_returned() {
      final OutboxMessage msg1 = aMessage();
      final OutboxMessage msg2 = aMessage();
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(any(Query.class), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX")))
              .thenReturn(List.of(aDocumentFor(msg1), aDocumentFor(msg2)));
      when(MongoDbOutboxMessageRepositoryTest.this.serializer.deserialize(any())).thenReturn(msg1, msg2);

      final List<OutboxMessage> result =
          MongoDbOutboxMessageRepositoryTest.this.repository.findAllOrderByCapturedAt(UNLIMITED);

      assertThat(result).hasSize(2).containsExactly(msg1, msg2);
      verify(MongoDbOutboxMessageRepositoryTest.this.serializer, times(2)).deserialize(any());
    }

    @Test
    void when_max_results_specified_expect_query_has_limit() {
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.capture(), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX")))
              .thenReturn(List.of());

      MongoDbOutboxMessageRepositoryTest.this.repository.findAllOrderByCapturedAt(5);

      assertThat(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.getValue().getLimit()).isEqualTo(5);
    }

    @Test
    void when_first_message_fails_deserialization_expect_empty_list() {
      final List<OutboxMessageDocument> documents = List.of(
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:30Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:20Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:10Z")));
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(any(Query.class), eq(OutboxMessageDocument.class), any(String.class))).thenReturn(documents);
      final MongoDbOutboxMessageRepository failingRepository =
          MongoDbOutboxMessageRepositoryTest.this.createRepositoryWithFailingDeserialization(1);

      final List<OutboxMessage> result = failingRepository.findAllOrderByCapturedAt(UNLIMITED);

      assertThat(result).isEmpty();
    }

    @Test
    void when_middle_message_fails_deserialization_expect_first_messages_only() {
      final List<OutboxMessageDocument> documents = List.of(
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:50Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:40Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:30Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:20Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:10Z")));
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(any(Query.class), eq(OutboxMessageDocument.class), any(String.class))).thenReturn(documents);
      final MongoDbOutboxMessageRepository failingRepository =
          MongoDbOutboxMessageRepositoryTest.this.createRepositoryWithFailingDeserialization(3);

      final List<OutboxMessage> result = failingRepository.findAllOrderByCapturedAt(UNLIMITED);

      assertThat(result).hasSize(2);
    }

    @Test
    void when_last_message_fails_deserialization_expect_all_but_last() {
      final List<OutboxMessageDocument> documents = List.of(
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:30Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:20Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:10Z")));
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(any(Query.class), eq(OutboxMessageDocument.class), any(String.class))).thenReturn(documents);
      final MongoDbOutboxMessageRepository failingRepository =
          MongoDbOutboxMessageRepositoryTest.this.createRepositoryWithFailingDeserialization(3);

      final List<OutboxMessage> result = failingRepository.findAllOrderByCapturedAt(UNLIMITED);

      assertThat(result).hasSize(2);
    }

    @Test
    void when_all_deserializations_succeed_expect_all_messages() {
      final List<OutboxMessageDocument> documents = List.of(
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:30Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:20Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:10Z")));
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(any(Query.class), eq(OutboxMessageDocument.class), any(String.class))).thenReturn(documents);
      final MongoDbOutboxMessageRepository failingRepository =
          MongoDbOutboxMessageRepositoryTest.this.createRepositoryWithFailingDeserialization(Integer.MAX_VALUE);

      final List<OutboxMessage> result = failingRepository.findAllOrderByCapturedAt(UNLIMITED);

      assertThat(result).hasSize(3);
    }
  }

  @Nested
  class FindAllOrderByCapturedAtExcludingDestinations {

    @Test
    void when_exclusions_provided_expect_query_contains_destination_criteria() {
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.capture(), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX")))
              .thenReturn(List.of());

      MongoDbOutboxMessageRepositoryTest.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(Set.of("excluded-dest"), UNLIMITED);

      assertThat(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.getValue().getQueryObject())
          .containsKey("destination");
    }

    @Test
    void when_null_exclusions_expect_fallback_to_find_all_without_destination_criteria() {
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.capture(), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX")))
              .thenReturn(List.of());

      MongoDbOutboxMessageRepositoryTest.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(null, UNLIMITED);

      assertThat(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.getValue()
          .getQueryObject().containsKey("destination")).isFalse();
    }

    @Test
    void when_empty_exclusions_expect_fallback_to_find_all_without_destination_criteria() {
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.capture(), eq(OutboxMessageDocument.class), eq("SCS_OUTBOX")))
              .thenReturn(List.of());

      MongoDbOutboxMessageRepositoryTest.this.repository
          .findAllOrderByCapturedAtExcludingDestinations(Set.of(), UNLIMITED);

      assertThat(MongoDbOutboxMessageRepositoryTest.this.queryCaptor.getValue()
          .getQueryObject().containsKey("destination")).isFalse();
    }

    @Test
    void when_middle_message_fails_deserialization_expect_first_messages_only() {
      final List<OutboxMessageDocument> documents = List.of(
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:50Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:40Z")),
          aDocumentWithFailingEngine("destination1", Instant.parse("2025-01-01T10:00:30Z")));
      when(MongoDbOutboxMessageRepositoryTest.this.mongoTemplate
          .find(any(Query.class), eq(OutboxMessageDocument.class), any(String.class))).thenReturn(documents);
      final MongoDbOutboxMessageRepository failingRepository =
          MongoDbOutboxMessageRepositoryTest.this.createRepositoryWithFailingDeserialization(2);

      final List<OutboxMessage> result =
          failingRepository.findAllOrderByCapturedAtExcludingDestinations(Set.of("excluded-dest"), UNLIMITED);

      assertThat(result).hasSize(1);
    }
  }

  @Nested
  class MapOutboxMessage {

    @Test
    void when_message_mapped_expect_serializer_called_and_all_document_fields_transferred() {
      final OutboxMessage message = aMessage();
      final SerializedOutboxMessage serialized = aSerializedMessage(message);
      when(MongoDbOutboxMessageRepositoryTest.this.serializer.serialize(message)).thenReturn(serialized);

      final OutboxMessageDocument document = MongoDbOutboxMessageRepositoryTest.this.repository.map(message);

      verify(MongoDbOutboxMessageRepositoryTest.this.serializer, times(1)).serialize(message);
      assertThat(document.getId()).isEqualTo(serialized.getId().toString());
      assertThat(document.getDestination()).isEqualTo(serialized.getDestination());
      assertThat(document.getBindingName()).isEqualTo(serialized.getBindingName());
      assertThat(document.getCapturedAt()).isEqualTo(serialized.getCapturedAt());
      assertThat(document.getHeaders()).isEqualTo(serialized.getHeaders());
      assertThat(document.getPayload()).isEqualTo(serialized.getPayload());
    }
  }

  @Nested
  class MapOutboxMessageDocument {

    @Test
    void when_document_mapped_expect_serializer_deserialize_called_with_all_fields() {
      final OutboxMessage expectedMessage = aMessage();
      final OutboxMessageDocument document = aDocumentFor(expectedMessage);
      when(MongoDbOutboxMessageRepositoryTest.this.serializer
          .deserialize(MongoDbOutboxMessageRepositoryTest.this.serializedMessageCaptor.capture()))
              .thenReturn(expectedMessage);

      final OutboxMessage result = MongoDbOutboxMessageRepositoryTest.this.repository.map(document);

      assertThat(result).isEqualTo(expectedMessage);
      final SerializedOutboxMessage captured = MongoDbOutboxMessageRepositoryTest.this.serializedMessageCaptor.getValue();
      assertThat(captured.getId()).isEqualTo(UUID.fromString(document.getId()));
      assertThat(captured.getDestination()).isEqualTo(document.getDestination());
      assertThat(captured.getBindingName()).isEqualTo(document.getBindingName());
      assertThat(captured.getCapturedAt()).isEqualTo(document.getCapturedAt());
      assertThat(captured.getHeaders()).isEqualTo(document.getHeaders());
      assertThat(captured.getPayload()).isEqualTo(document.getPayload());
    }
  }

  private MongoDbOutboxMessageRepository createRepositoryWithFailingDeserialization(int failOnCall) {
    final SerializationEngine failingEngine = new FailingSerializationEngine(failOnCall);
    final OutboxMessageSerializer failingSerializer =
        new OutboxMessageSerializer(failingEngine, new JsonHeadersMapper());
    return new MongoDbOutboxMessageRepository(this.mongoTemplate, failingSerializer, new MongoDbProperties());
  }

  private static OutboxMessage aMessage() {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.parse("2025-01-01T10:00:00Z"))
        .destination("destination")
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of("contentType", "application/json"))
        .build();
  }

  private static SerializedOutboxMessage aSerializedMessage(OutboxMessage message) {
    return SerializedOutboxMessage.builder()
        .id(message.getId())
        .capturedAt(message.getCapturedAt())
        .destination(message.getDestination())
        .bindingName(message.getBindingName())
        .headers("{\"contentType\":\"application/json\"}")
        .payload(new byte[]{1, 2, 3})
        .build();
  }

  private static OutboxMessageDocument aDocumentFor(OutboxMessage message) {
    return OutboxMessageDocument.builder()
        .id(message.getId().toString())
        .capturedAt(message.getCapturedAt())
        .destination(message.getDestination())
        .bindingName(message.getBindingName())
        .headers("{\"contentType\":\"application/json\"}")
        .payload(new byte[]{1, 2, 3})
        .build();
  }

  private static OutboxMessageDocument aDocumentWithFailingEngine(String destination, Instant capturedAt) {
    return OutboxMessageDocument.builder()
        .id(UUID.randomUUID().toString())
        .destination(destination)
        .bindingName("bindingName")
        .capturedAt(capturedAt)
        .headers("{\"scs-outbox-serialization-engine\":\"" + FailingSerializationEngine.class.getName() + "\"}")
        .payload(new byte[]{1, 2, 3})
        .build();
  }

  private static class FailingSerializationEngine implements SerializationEngine {

    private final int failOnCall;

    private final AtomicInteger callCount = new AtomicInteger(0);

    FailingSerializationEngine(int failOnCall) {
      this.failOnCall = failOnCall;
    }

    @Override
    public Object deserialize(byte[] bytes) {
      if (this.callCount.incrementAndGet() >= this.failOnCall) {
        throw new RuntimeException("Simulated deserialization error on call " + this.callCount.get());
      }
      return "payload";
    }

    @Override
    public byte[] serialize(Object object) {
      return new byte[]{1, 2, 3};
    }
  }
}
