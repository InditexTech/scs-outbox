package dev.inditex.scsoutbox.publish.archive.mongodb;

import java.time.Instant;

import com.mongodb.DBObject;
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
public class ArchivedMessageDocument {

  @EqualsAndHashCode.Include
  @Id
  @NonNull
  private final String id;

  @NonNull
  private final String destination;

  @NonNull
  private final String contentType;

  private final byte @NonNull [] payload;

  @NonNull
  private final String headers;

  @NonNull
  private final Instant capturedAt;

  @NonNull
  private final Instant archivedAt;

  @NonNull
  private final String serialization;

  private final DBObject jsonPayload;
}
