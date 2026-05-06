package dev.inditex.scsoutbox.serialization;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@RequiredArgsConstructor
public class OutboxMessageSerializer {

  public static final String SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER = "scs-outbox-serialization-engine";

  public static final String NONE = "none";

  private final SerializationEngine serializationEngine;

  private final HeadersMapper headersMapper;

  public SerializedOutboxMessage serialize(OutboxMessage outboxMessage) {
    byte[] payload = null;
    final var headers = new HashMap<>(outboxMessage.getHeaders());
    if (outboxMessage.getPayload() instanceof byte[] rawPayload) {
      headers.put(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, NONE);
      payload = rawPayload;
    } else {
      headers.put(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, this.serializationEngine.getClass().getName());
      payload = this.serializationEngine.serialize(outboxMessage.getPayload());
    }
    return SerializedOutboxMessage.builder()
        .id(outboxMessage.getId())
        .bindingName(outboxMessage.getBindingName())
        .capturedAt(outboxMessage.getCapturedAt())
        .destination(outboxMessage.getDestination())
        .headers(this.headersMapper.write(headers))
        .payload(payload)
        .build();
  }

  public OutboxMessage deserialize(SerializedOutboxMessage serializedOutboxMessage) {
    final Map<String, Object> outboxHeaders = this.headersMapper.read(serializedOutboxMessage.getHeaders());
    final Object serializationEngineValue =
        outboxHeaders.getOrDefault(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER, this.serializationEngine.getClass().toString());
    Object outboxPayload = null;
    if (serializationEngineValue.toString().equals(NONE)) {
      outboxPayload = serializedOutboxMessage.getPayload();
    } else {
      outboxPayload = this.serializationEngine.deserialize(serializedOutboxMessage.getPayload());
    }
    outboxHeaders.remove(SCS_OUTBOX_SERIALIZATION_ENGINE_HEADER);
    return OutboxMessage.builder()
        .id(serializedOutboxMessage.getId())
        .bindingName(serializedOutboxMessage.getBindingName())
        .destination(serializedOutboxMessage.getDestination())
        .capturedAt(serializedOutboxMessage.getCapturedAt())
        .headers(outboxHeaders)
        .payload(outboxPayload)
        .build();
  }

  @Builder
  @Value
  public static class SerializedOutboxMessage {
    UUID id;

    String bindingName;

    Instant capturedAt;

    String destination;

    String headers;

    byte[] payload;
  }
}
