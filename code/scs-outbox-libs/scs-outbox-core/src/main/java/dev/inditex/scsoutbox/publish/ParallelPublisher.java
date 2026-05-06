package dev.inditex.scsoutbox.publish;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import dev.inditex.scsoutbox.OutboxMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Class that handles parallel publishing of Outbox messages.
 */
@Slf4j
public class ParallelPublisher {

  private final ExecutorService executorService;

  private final OutboxMessagePublisher publisher;

  /**
   * ParallelPublisher constructor.
   *
   * @param executorService execution service to handle parallel tasks
   * @param publisher the Outbox message publisher
   */
  public ParallelPublisher(ExecutorService executorService, OutboxMessagePublisher publisher) {
    this.executorService = executorService;
    this.publisher = publisher;
  }

  /**
   * Publishes grouped messages in parallel and waits for all tasks to complete.
   *
   * @param groupedMessages a map of messages grouped by key
   * @return the total number of published messages
   */
  public int publish(Map<GroupingKey, List<OutboxMessage>> groupedMessages) {
    final List<Future<Integer>> futures = this.publishInParallel(groupedMessages);
    return this.waitForCompletion(futures);
  }

  /**
   * Publishes grouped messages in parallel.
   *
   * @param groupedMessages a map of messages grouped by key
   * @return a list of futures representing the publishing tasks
   */
  private List<Future<Integer>> publishInParallel(Map<GroupingKey, List<OutboxMessage>> groupedMessages) {
    return groupedMessages.values().stream()
        .map(this::sortAndPublish)
        .toList();
  }

  /**
   * Sorts and publishes a list of messages.
   *
   * @param messages the list of messages to publish
   * @return a future representing the publishing task
   */
  private Future<Integer> sortAndPublish(List<OutboxMessage> messages) {
    return this.executorService.submit(() -> {
      messages.sort(Comparator.comparing(OutboxMessage::getCapturedAt));
      final AtomicInteger publishedCount = new AtomicInteger();
      try {
        for (final OutboxMessage message : messages) {
          this.publisher.publish(message);
          publishedCount.incrementAndGet();
        }
      } catch (final Exception e) {
        log.warn("Unexpected error in publishing task", e);
      }
      return publishedCount.get();
    });
  }

  /**
   * Waits for all publishing tasks to complete.
   *
   * @param futures the list of futures representing the publishing tasks
   * @return the total number of published messages
   */
  private int waitForCompletion(List<Future<Integer>> futures) {
    final AtomicInteger publishedCount = new AtomicInteger();
    for (final Future<Integer> future : futures) {
      try {
        publishedCount.addAndGet(future.get());
      } catch (final InterruptedException e) {
        log.warn("InterruptedException caught!", e);
        Thread.currentThread().interrupt();
      } catch (final Exception e) {
        log.warn("Error retrieving future result", e);
      }
    }
    return publishedCount.get();
  }

}
