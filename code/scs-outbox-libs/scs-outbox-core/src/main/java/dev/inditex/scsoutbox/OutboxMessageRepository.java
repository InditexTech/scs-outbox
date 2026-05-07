package dev.inditex.scsoutbox;

import java.util.List;
import java.util.Set;

public interface OutboxMessageRepository {

  int UNLIMITED = 0;

  /**
   * Find all messages ordered by capturedAt.
   *
   * @param maxResults Maximum number of messages to return. use {@link #UNLIMITED} to unlimited messages
   */
  List<OutboxMessage> findAllOrderByCapturedAt(final int maxResults);

  /**
   * Find all messages ordered by capturedAt, excluding specified destinations.
   *
   * @param excludedDestinations Set of destinations to exclude from the results
   * @param maxResults Maximum number of messages to return. use {@link #UNLIMITED} to unlimited messages
   */
  List<OutboxMessage> findAllOrderByCapturedAtExcludingDestinations(final Set<String> excludedDestinations, final int maxResults);

  /**
   * Returns the number of messages in the outbox.
   *
   * @return the number of messages in the outbox
   */
  long count();

  /**
   * Returns an estimated count of the messages in the outbox or exactly count if it is not possible.
   *
   * @return the estimated count of the messages in the outbox
   */
  long estimatedCount();

  void save(final OutboxMessage outboxMessage);

  void delete(final OutboxMessage outboxMessage);

}
