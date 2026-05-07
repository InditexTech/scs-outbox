package dev.inditex.scsoutbox.publish.archive.jdbc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.publish.archive.ArchivedMessage;

public abstract class ArchivedMessageMother {

  public static ArchivedMessage anArchivedMessage() {
    return ArchivedMessage.builder()
        .id(UUID.randomUUID())
        .archivedAt(Instant.now())
        .capturedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
        .destination("destination")
        .contentType("application/json")
        .payload("payload")
        .headers(Map.of())
        .jsonPayload("{ \"key\": \"value\" }")
        .build();
  }

}
