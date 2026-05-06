package dev.inditex.scsoutbox;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryOutboxMessageRepository implements OutboxMessageRepository {

  private final Map<UUID, OutboxMessage> messages = new ConcurrentHashMap<UUID, OutboxMessage>();

  private final List<OutboxMessage> removedMessages = new CopyOnWriteArrayList<>();

  public void init(final List<OutboxMessage> outboxMessageList) {
    this.messages.clear();
    outboxMessageList.forEach(
        unpublishedMessage -> this.messages.put(unpublishedMessage.getId(), unpublishedMessage));
  }

  @Override
  public void save(final OutboxMessage entity) {
    this.messages.put(entity.getId(), entity);
  }

  private List<OutboxMessage> findAllOrderByCapturedAt() {
    final List<OutboxMessage> values = new LinkedList<>(this.messages.values());
    values.sort((m1, m2) -> m1.getCapturedAt().compareTo(m2.getCapturedAt()));
    return List.copyOf(values);
  }

  @Override
  public List<OutboxMessage> findAllOrderByCapturedAt(int limit) {
    if (limit <= 0) {
      return this.findAllOrderByCapturedAt();
    }
    return this.findAllOrderByCapturedAt().stream().limit(limit).toList();
  }

  @Override
  public List<OutboxMessage> findAllOrderByCapturedAtExcludingDestinations(Set<String> excludedDestinations, int limit) {
    return this.findAllOrderByCapturedAt().stream()
        .filter(msg -> !excludedDestinations.contains(msg.getDestination()))
        .limit(limit > 0 ? limit : Long.MAX_VALUE)
        .toList();
  }

  @Override
  public long count() {
    return this.messages.size();
  }

  @Override
  public long estimatedCount() {
    return this.count();
  }

  @Override
  public void delete(final OutboxMessage outboxMessage) {
    this.messages.remove(outboxMessage.getId());
    this.removedMessages.add(outboxMessage);
  }

  public List<OutboxMessage> getDeleted() {
    return List.copyOf(this.removedMessages);
  }

}
