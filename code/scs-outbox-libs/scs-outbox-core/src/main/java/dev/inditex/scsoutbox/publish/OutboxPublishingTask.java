package dev.inditex.scsoutbox.publish;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.publish.config.PublishingProperties;

import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OutboxPublishingTask {

  private final OutboxMessageRepository outboxMessageRepository;

  private final ParallelPublisher parallelPublisher;

  private final GroupingStrategy groupingStrategy;

  private final PublishingProperties publishingProperties;

  public OutboxPublishingTask(final OutboxMessageRepository repository, final ParallelPublisher parallelPublisher,
      final GroupingStrategy groupingStrategy,
      final PublishingProperties publishingProperties) {
    this.outboxMessageRepository = repository;
    this.parallelPublisher = parallelPublisher;
    this.groupingStrategy = groupingStrategy;
    this.publishingProperties = publishingProperties;
  }

  @Timed("outbox.publishing.time")
  public OutboxPublishingTaskReport run() {
    final Instant start = Instant.now();

    // Check global pause first to avoid unnecessary processing
    if (this.publishingProperties.isPaused()) {
      log.warn("Outbox publishing is globally paused. No messages will be processed. "
          + "Set scs-outbox.publishing.paused=false to resume publishing.");
      return this.createReport(start, 0);
    }

    final List<OutboxMessage> messages = this.fetchPendingMessages();
    final Map<GroupingKey, List<OutboxMessage>> group = this.groupingStrategy.group(messages);
    final int publishedCount = this.parallelPublisher.publish(group);
    return this.createReport(start, publishedCount);
  }

  private OutboxPublishingTaskReport createReport(final Instant start, final int publishedCount) {
    return OutboxPublishingTaskReport.of(start, Instant.now(), publishedCount);
  }

  private List<OutboxMessage> fetchPendingMessages() {
    final Set<String> pausedDestinations = this.publishingProperties.getPausedDestinations();
    final int batchSize = this.publishingProperties.getBatchSize();

    if (pausedDestinations.isEmpty()) {
      return this.outboxMessageRepository.findAllOrderByCapturedAt(batchSize);
    } else {
      log.debug("Fetching pending messages excluding paused destinations: {}", pausedDestinations);
      return this.outboxMessageRepository.findAllOrderByCapturedAtExcludingDestinations(pausedDestinations, batchSize);
    }
  }

}
