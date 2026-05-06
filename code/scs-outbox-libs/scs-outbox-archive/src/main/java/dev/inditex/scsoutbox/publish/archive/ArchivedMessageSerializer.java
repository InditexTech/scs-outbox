package dev.inditex.scsoutbox.publish.archive;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.serialization.HeadersMapper;
import dev.inditex.scsoutbox.serialization.SerializationEngine;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@RequiredArgsConstructor
public class ArchivedMessageSerializer {

  public static final String NONE = "none";

  private final SerializationEngine serializationEngine;

  private final HeadersMapper headersMapper;

  public SerializedArchivedMessage serialize(final ArchivedMessage archivedMessage) {
    final boolean isRaw = archivedMessage.getPayload() instanceof byte[];
    return SerializedArchivedMessage.builder()
        .id(archivedMessage.getId())
        .destination(archivedMessage.getDestination())
        .contentType(archivedMessage.getContentType())
        .payload(this.resolvePayloadForStorage(archivedMessage.getPayload()))
        .headers(this.headersMapper.write(archivedMessage.getHeaders()))
        .capturedAt(archivedMessage.getCapturedAt())
        .archivedAt(archivedMessage.getArchivedAt())
        .jsonPayload(archivedMessage.getJsonPayload())
        .serialization(isRaw ? NONE : this.serializationEngine.getClass().getName())
        .build();
  }

  public ArchivedMessage deserialize(final SerializedArchivedMessage serialized) {
    final Map<String, Object> headers = this.headersMapper.read(serialized.getHeaders());
    final Object payload = NONE.equals(serialized.getSerialization())
        ? serialized.getPayload()
        : this.serializationEngine.deserialize(serialized.getPayload());
    return ArchivedMessage.builder()
        .id(serialized.getId())
        .destination(serialized.getDestination())
        .contentType(serialized.getContentType())
        .payload(payload)
        .headers(headers)
        .capturedAt(serialized.getCapturedAt())
        .archivedAt(serialized.getArchivedAt())
        .jsonPayload(serialized.getJsonPayload())
        .build();
  }

  /**
   * Resolves the payload for storage. If the payload is already raw bytes (from raw passthrough mode), it is stored directly. Otherwise,
   * the serialization engine is used to serialize the payload.
   */
  private byte[] resolvePayloadForStorage(final Object payload) {
    if (payload instanceof byte[] raw) {
      return raw;
    }
    return this.serializationEngine.serialize(payload);
  }

  @Builder
  @Value
  public static class SerializedArchivedMessage {

    UUID id;

    String destination;

    String contentType;

    byte[] payload;

    String headers;

    Instant capturedAt;

    Instant archivedAt;

    String jsonPayload;

    String serialization;
  }
}
