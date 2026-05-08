package dev.inditex.scsoutbox.publish.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer.SerializedArchivedMessage;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ArchivedMessageSerializerTest {

  private final ArchivedMessageSerializer serializer =
      new ArchivedMessageSerializer(new JavaSerialization(), new JsonHeadersMapper());

  @Nested
  class Serialize {

    @Test
    void when_payload_is_not_bytes_expect_serialization_engine_used() {
      final ArchivedMessage message = anArchivedMessage("hello");

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);

      assertThat(serialized.getPayload()).isNotNull();
      assertThat(serialized.getSerialization()).isEqualTo(JavaSerialization.class.getName());
    }

    @Test
    void when_payload_is_byte_array_expect_raw_bytes_stored() {
      final byte[] raw = {1, 2, 3};
      final ArchivedMessage message = anArchivedMessage(raw);

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);

      assertThat(serialized.getPayload()).isEqualTo(raw);
      assertThat(serialized.getSerialization()).isEqualTo(ArchivedMessageSerializer.NONE);
    }

    @Test
    void when_serialized_expect_headers_written_as_string() {
      final ArchivedMessage message = anArchivedMessage("payload");

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);

      assertThat(serialized.getHeaders()).isNotBlank();
    }

    @Test
    void when_json_payload_present_expect_preserved() {
      final ArchivedMessage message = anArchivedMessage("payload");

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);

      assertThat(serialized.getJsonPayload()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void when_json_payload_absent_expect_null_in_serialized_message() {
      final ArchivedMessage message = anArchivedMessageWithoutJsonPayload("payload");

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);

      assertThat(serialized.getJsonPayload()).isNull();
    }

    @Test
    void when_serialized_expect_all_metadata_fields_preserved() {
      final ArchivedMessage message = anArchivedMessage("payload");

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);

      assertThat(serialized.getId()).isEqualTo(message.getId());
      assertThat(serialized.getDestination()).isEqualTo(message.getDestination());
      assertThat(serialized.getContentType()).isEqualTo(message.getContentType());
      assertThat(serialized.getCapturedAt()).isEqualTo(message.getCapturedAt());
      assertThat(serialized.getArchivedAt()).isEqualTo(message.getArchivedAt());
    }
  }

  @Nested
  class Deserialize {

    @Test
    void when_round_trip_with_non_bytes_payload_expect_all_fields_preserved() {
      final ArchivedMessage message = anArchivedMessage("payload");

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);
      final ArchivedMessage deserialized = ArchivedMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat(deserialized.getId()).isEqualTo(message.getId());
      assertThat(deserialized.getDestination()).isEqualTo(message.getDestination());
      assertThat(deserialized.getContentType()).isEqualTo(message.getContentType());
      assertThat(deserialized.getPayload()).isEqualTo(message.getPayload());
      assertThat(deserialized.getHeaders()).isEqualTo(message.getHeaders());
      assertThat(deserialized.getCapturedAt()).isEqualTo(message.getCapturedAt());
      assertThat(deserialized.getArchivedAt()).isEqualTo(message.getArchivedAt());
      assertThat(deserialized.getJsonPayload()).isEqualTo(message.getJsonPayload());
    }

    @Test
    void when_round_trip_with_byte_array_payload_expect_raw_bytes_preserved() {
      final byte[] raw = {10, 20, 30};
      final ArchivedMessage message = anArchivedMessage(raw);

      final SerializedArchivedMessage serialized = ArchivedMessageSerializerTest.this.serializer.serialize(message);
      final ArchivedMessage deserialized = ArchivedMessageSerializerTest.this.serializer.deserialize(serialized);

      assertThat((byte[]) deserialized.getPayload()).isEqualTo(raw);
    }
  }

  private static ArchivedMessage anArchivedMessage(final Object payload) {
    return ArchivedMessage.builder()
        .id(UUID.randomUUID())
        .destination("test-destination")
        .contentType("application/json")
        .payload(payload)
        .headers(Map.of("header-key", "header-value"))
        .capturedAt(Instant.parse("2026-03-23T12:00:00Z"))
        .archivedAt(Instant.parse("2026-03-23T12:01:00Z"))
        .jsonPayload("{\"key\":\"value\"}")
        .build();
  }

  private static ArchivedMessage anArchivedMessageWithoutJsonPayload(final Object payload) {
    return ArchivedMessage.builder()
        .id(UUID.randomUUID())
        .destination("test-destination")
        .contentType("application/json")
        .payload(payload)
        .headers(Map.of())
        .capturedAt(Instant.parse("2026-03-23T12:00:00Z"))
        .archivedAt(Instant.parse("2026-03-23T12:01:00Z"))
        .build();
  }
}
