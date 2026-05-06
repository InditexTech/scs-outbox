package dev.inditex.scsoutbox.serialization;

import static dev.inditex.scsoutbox.serialization.OutboxMessageSerializer.NONE;
import static dev.inditex.scsoutbox.serialization.OutboxMessageSerializer.SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer.SerializedOutboxMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxMessageSerializerTest {

  private OutboxMessageSerializer serializer;

  @Mock
  private SerializationEngine serializationEngine;

  private final JsonHeadersMapper headersMapper = new JsonHeadersMapper();

  @BeforeEach
  void beforeEach() {
    this.serializer = new OutboxMessageSerializer(
        this.serializationEngine, this.headersMapper);
  }

  @Nested
  class Serialize {

    @Test
    void when_payload_is_byte_array_expect_raw_bytes_stored() {
      final byte[] data = {1, 2, 3};
      final OutboxMessage message = buildOutboxMessage(data, Map.of());

      final SerializedOutboxMessage result = OutboxMessageSerializerTest.this.serializer.serialize(message);

      assertThat(result.getPayload()).isEqualTo(data);
      verifyNoInteractions(OutboxMessageSerializerTest.this.serializationEngine);
    }

    @Test
    void when_payload_is_byte_array_expect_none_serialization_header_added() {
      final OutboxMessage message = buildOutboxMessage(new byte[]{1, 2}, Map.of());

      final SerializedOutboxMessage result = OutboxMessageSerializerTest.this.serializer.serialize(message);

      final Map<String, Object> headers = OutboxMessageSerializerTest.this.headersMapper.read(result.getHeaders());
      assertThat(headers).containsEntry(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, NONE);
    }

    @Test
    void when_payload_is_not_byte_array_expect_engine_used_to_serialize() {
      final byte[] serializedBytes = {10, 20, 30};
      doReturn(serializedBytes).when(OutboxMessageSerializerTest.this.serializationEngine).serialize("hello");
      final OutboxMessage message = buildOutboxMessage("hello", Map.of());

      final SerializedOutboxMessage result = OutboxMessageSerializerTest.this.serializer.serialize(message);

      assertThat(result.getPayload()).isEqualTo(serializedBytes);
      verify(OutboxMessageSerializerTest.this.serializationEngine).serialize("hello");
    }

    @Test
    void when_payload_is_not_byte_array_expect_engine_class_name_in_serialization_header() {
      doReturn(new byte[0]).when(OutboxMessageSerializerTest.this.serializationEngine).serialize(any());
      final OutboxMessage message = buildOutboxMessage("hello", Map.of());

      final SerializedOutboxMessage result = OutboxMessageSerializerTest.this.serializer.serialize(message);

      final Map<String, Object> headers = OutboxMessageSerializerTest.this.headersMapper.read(result.getHeaders());
      assertThat(headers).containsEntry(
          SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER,
          OutboxMessageSerializerTest.this.serializationEngine.getClass().getName());
    }

    @Test
    void when_serialized_expect_original_headers_preserved() {
      doReturn(new byte[0]).when(OutboxMessageSerializerTest.this.serializationEngine).serialize(any());
      final OutboxMessage message = buildOutboxMessage("hello", Map.of("kafka_messageKey", "order-123"));

      final SerializedOutboxMessage result = OutboxMessageSerializerTest.this.serializer.serialize(message);

      final Map<String, Object> headers = OutboxMessageSerializerTest.this.headersMapper.read(result.getHeaders());
      assertThat(headers).containsEntry("kafka_messageKey", "order-123");
    }

    @Test
    void when_serialized_expect_metadata_fields_preserved() {
      final OutboxMessage message = buildOutboxMessage(new byte[]{1}, Map.of());

      final SerializedOutboxMessage result = OutboxMessageSerializerTest.this.serializer.serialize(message);

      assertThat(result.getId()).isEqualTo(message.getId());
      assertThat(result.getBindingName()).isEqualTo(message.getBindingName());
      assertThat(result.getDestination()).isEqualTo(message.getDestination());
      assertThat(result.getCapturedAt()).isEqualTo(message.getCapturedAt());
    }

  }

  @Nested
  class Deserialize {

    @Test
    void when_serialization_header_is_none_expect_raw_bytes_returned_as_payload() {
      final byte[] rawBytes = {10, 20, 30};
      final SerializedOutboxMessage serialized = buildSerializedOutboxMessage(rawBytes,
          Map.<String, Object>of(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, NONE));

      final OutboxMessage result = OutboxMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat(result.getPayload()).isEqualTo(rawBytes);
      verifyNoInteractions(OutboxMessageSerializerTest.this.serializationEngine);
    }

    @Test
    void when_serialization_header_is_not_none_expect_engine_used_to_deserialize() {
      final Object deserializedPayload = "deserialized-payload";
      final byte[] rawBytes = {1, 2, 3};
      doReturn(deserializedPayload).when(OutboxMessageSerializerTest.this.serializationEngine).deserialize(rawBytes);
      final SerializedOutboxMessage serialized = buildSerializedOutboxMessage(rawBytes,
          Map.<String, Object>of(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, JavaSerialization.class.getName()));

      final OutboxMessage result = OutboxMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat(result.getPayload()).isEqualTo(deserializedPayload);
      verify(OutboxMessageSerializerTest.this.serializationEngine).deserialize(rawBytes);
    }

    @Test
    void when_serialization_header_absent_expect_engine_used_as_fallback_to_deserialize() {
      final Object deserializedPayload = "fallback-deserialized";
      final byte[] rawBytes = {5, 6, 7};
      doReturn(deserializedPayload).when(OutboxMessageSerializerTest.this.serializationEngine).deserialize(rawBytes);
      // no SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER in headers → getOrDefault uses engine class as fallback
      final SerializedOutboxMessage serialized = buildSerializedOutboxMessage(rawBytes, Map.of());

      final OutboxMessage result = OutboxMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat(result.getPayload()).isEqualTo(deserializedPayload);
      verify(OutboxMessageSerializerTest.this.serializationEngine).deserialize(rawBytes);
    }

    @Test
    void when_deserialized_expect_serialization_engine_header_removed_from_outbox_headers() {
      final SerializedOutboxMessage serialized = buildSerializedOutboxMessage(new byte[]{1},
          Map.<String, Object>of(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, NONE, "custom-header", "custom-value"));

      final OutboxMessage result = OutboxMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat(result.getHeaders()).doesNotContainKey(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER);
    }

    @Test
    void when_deserialized_expect_original_headers_preserved() {
      final SerializedOutboxMessage serialized = buildSerializedOutboxMessage(new byte[]{1},
          Map.<String, Object>of(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, NONE, "kafka_messageKey", "order-456"));

      final OutboxMessage result = OutboxMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat(result.getHeaders()).containsEntry("kafka_messageKey", "order-456");
    }

    @Test
    void when_deserialized_expect_metadata_fields_preserved() {
      final UUID id = UUID.randomUUID();
      final Instant capturedAt = Instant.parse("2026-03-23T12:00:00Z");
      final SerializedOutboxMessage serialized = SerializedOutboxMessage.builder()
          .id(id)
          .bindingName("test-binding")
          .destination("test-destination")
          .capturedAt(capturedAt)
          .headers(new JsonHeadersMapper().write(Map.<String, Object>of(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, NONE)))
          .payload(new byte[]{1})
          .build();

      final OutboxMessage result = OutboxMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat(result.getId()).isEqualTo(id);
      assertThat(result.getBindingName()).isEqualTo("test-binding");
      assertThat(result.getDestination()).isEqualTo("test-destination");
      assertThat(result.getCapturedAt()).isEqualTo(capturedAt);
    }
  }

  private static OutboxMessage buildOutboxMessage(final Object payload, final Map<String, Object> headers) {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.parse("2026-03-23T12:00:00Z"))
        .destination("test-destination")
        .bindingName("test-binding")
        .payload(payload)
        .headers(headers)
        .build();
  }

  private static SerializedOutboxMessage buildSerializedOutboxMessage(
      final byte[] payload, final Map<String, Object> headers) {
    return SerializedOutboxMessage.builder()
        .id(UUID.randomUUID())
        .bindingName("test-binding")
        .destination("test-destination")
        .capturedAt(Instant.parse("2026-03-23T12:00:00Z"))
        .headers(new JsonHeadersMapper().write(headers))
        .payload(payload)
        .build();
  }
}
