package dev.inditex.scsoutbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage.OutboxMessageBuilder;

public abstract class OutboxMessageMother {

  public static OutboxMessage anOutboxMessage() {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.now())
        .destination("destination")
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of())
        .build();
  }

  public static OutboxMessageBuilder anOutboxMessageBuilder() {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.now())
        .destination("destination")
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of());
  }

}
