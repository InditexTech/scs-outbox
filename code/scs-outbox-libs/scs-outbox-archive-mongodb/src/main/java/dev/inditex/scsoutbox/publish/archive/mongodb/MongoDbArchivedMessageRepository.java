package dev.inditex.scsoutbox.publish.archive.mongodb;

import dev.inditex.scsoutbox.publish.archive.ArchivedMessage;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer.SerializedArchivedMessage;
import dev.inditex.scsoutbox.publish.archive.mongodb.config.MongoDbArchiveProperties;

import com.mongodb.BasicDBObject;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;

@RequiredArgsConstructor
public class MongoDbArchivedMessageRepository implements ArchivedMessageRepository {

  private final MongoTemplate mongoTemplate;

  private final ArchivedMessageSerializer serializer;

  private final MongoDbArchiveProperties mongoDbProperties;

  @Override
  public void save(final ArchivedMessage archivedMessage) {
    this.mongoTemplate.insert(this.map(archivedMessage), this.mongoDbProperties.getCollectionName());
  }

  private ArchivedMessageDocument map(final ArchivedMessage archivedMessage) {
    final SerializedArchivedMessage serialized = this.serializer.serialize(archivedMessage);
    return ArchivedMessageDocument.builder()
        .id(serialized.getId().toString())
        .archivedAt(serialized.getArchivedAt())
        .capturedAt(serialized.getCapturedAt())
        .destination(serialized.getDestination())
        .contentType(serialized.getContentType())
        .headers(serialized.getHeaders())
        .payload(serialized.getPayload())
        .serialization(serialized.getSerialization())
        .jsonPayload(
            serialized.getJsonPayload() != null ? BasicDBObject.parse(serialized.getJsonPayload()) : null)
        .build();
  }
}
