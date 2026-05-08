package dev.inditex.scsoutbox.publish.archive;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ArchivedMessage {

  @EqualsAndHashCode.Include
  @NonNull
  private final UUID id;

  @NonNull
  private final String destination;

  @NonNull
  private final Object payload;

  @NonNull
  private final Map<String, Object> headers;

  @NonNull
  private final Instant capturedAt;

  @NonNull
  private final String contentType;

  @NonNull
  private Instant archivedAt;

  private String jsonPayload;
}
