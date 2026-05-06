package dev.inditex.scsoutbox.mongodb;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("OUTBOX")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxMessageDocument {

  @EqualsAndHashCode.Include
  @Id
  @NonNull
  private final String id;

  @NonNull
  private final String destination;

  private final byte @NonNull [] payload;

  @NonNull
  private final String headers;

  @NonNull
  private final Instant capturedAt;

  @NonNull
  private final String bindingName;

}
