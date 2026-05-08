package dev.inditex.scsoutbox.mongodb;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.mongodb.config.MongoDbProperties;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer.SerializedOutboxMessage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class MongoDbOutboxMessageRepository implements OutboxMessageRepository {

  private final MongoTemplate mongoTemplate;

  private final OutboxMessageSerializer serializer;

  private final MongoDbProperties mongoDbProperties;

  public MongoDbOutboxMessageRepository(MongoTemplate mongoTemplate, OutboxMessageSerializer serializer,
      MongoDbProperties mongoDbProperties) {
    this.mongoTemplate = mongoTemplate;
    this.serializer = serializer;
    this.mongoDbProperties = mongoDbProperties;
  }

  @Override
  public List<OutboxMessage> findAllOrderByCapturedAt(int maxResults) {
    final Query query = new Query()
        .with(Sort.by("capturedAt").ascending())
        .limit(maxResults);

    final List<OutboxMessageDocument> documents =
        this.mongoTemplate.find(query, OutboxMessageDocument.class, this.getCollectionName());

    return this.mapWithDeserializationErrorHandling(documents);
  }

  /**
   * Maps documents to OutboxMessages one by one, stopping at the first deserialization error. Documents that were successfully deserialized
   * before the error are returned.
   *
   * @param documents the documents to map
   * @return list of successfully deserialized messages (may be truncated if a deserialization error occurred)
   */
  private List<OutboxMessage> mapWithDeserializationErrorHandling(List<OutboxMessageDocument> documents) {
    final List<OutboxMessage> messages = new ArrayList<>();
    for (final OutboxMessageDocument document : documents) {
      try {
        messages.add(this.map(document));
      } catch (final Exception e) {
        log.error("Deserialization failed for message [{}] at destination [{}]. "
            + "Returning {} successfully deserialized messages.",
            document.getId(),
            document.getDestination(),
            messages.size(), e);
        break;
      }
    }
    return messages;
  }

  @Override
  public List<OutboxMessage> findAllOrderByCapturedAtExcludingDestinations(Set<String> excludedDestinations, int maxResults) {
    if (excludedDestinations == null || excludedDestinations.isEmpty()) {
      return this.findAllOrderByCapturedAt(maxResults);
    }

    final Query query = new Query()
        .addCriteria(Criteria.where("destination").nin(excludedDestinations))
        .with(Sort.by("capturedAt").ascending())
        .limit(maxResults);

    final List<OutboxMessageDocument> documents =
        this.mongoTemplate.find(query, OutboxMessageDocument.class, this.getCollectionName());

    return this.mapWithDeserializationErrorHandling(documents);
  }

  @Override
  public long count() {
    return this.mongoTemplate.count(new Query(), OutboxMessageDocument.class, this.getCollectionName());
  }

  @Override
  public long estimatedCount() {
    return this.mongoTemplate.estimatedCount(this.getCollectionName());
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void save(final OutboxMessage outboxMessage) {
    this.mongoTemplate.insert(this.map(outboxMessage), this.getCollectionName());
  }

  private String getCollectionName() {
    return this.mongoDbProperties.getCollectionName();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void delete(final OutboxMessage outboxMessage) {
    final Query searchQuery = new Query(Criteria.where("id").is(outboxMessage.getId().toString()));
    this.mongoTemplate.remove(searchQuery, OutboxMessageDocument.class, this.getCollectionName());
  }

  public OutboxMessageDocument map(final OutboxMessage outboxMessage) {
    final SerializedOutboxMessage serialized = this.serializer.serialize(outboxMessage);
    return OutboxMessageDocument.builder()
        .id(serialized.getId().toString())
        .capturedAt(serialized.getCapturedAt())
        .destination(serialized.getDestination())
        .bindingName(serialized.getBindingName())
        .headers(serialized.getHeaders())
        .payload(serialized.getPayload())
        .build();
  }

  public OutboxMessage map(final OutboxMessageDocument document) {
    final SerializedOutboxMessage serialized = SerializedOutboxMessage.builder()
        .id(UUID.fromString(document.getId()))
        .capturedAt(document.getCapturedAt())
        .destination(document.getDestination())
        .bindingName(document.getBindingName())
        .headers(document.getHeaders())
        .payload(document.getPayload())
        .build();
    return this.serializer.deserialize(serialized);
  }
}
