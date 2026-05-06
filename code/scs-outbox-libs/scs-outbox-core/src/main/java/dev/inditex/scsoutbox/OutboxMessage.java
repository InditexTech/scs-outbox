package dev.inditex.scsoutbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxMessage {

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
  private final String bindingName;

}
