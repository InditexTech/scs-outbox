package dev.inditex.scsoutbox.publish;

import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessage;
import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessageBuilder;

import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.config.PublishingProperties;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

class OutboxPublishingTaskTest extends AbstractOutboxPublishingTaskTest {

  @Test
  void when_the_number_of_unpublished_messages_is_greater_than_the_batch_size() {
    final int batchSize = 2;
    final PublishingProperties properties = new PublishingProperties(batchSize, "KAFKA_MESSAGE_KEY", Set.of(), false, false);
    this.task = new OutboxPublishingTask(this.repository, this.parallelPublisher,
        new KeyGroupingStrategy(new KafkaKeyGroupingKeyGenerator()), properties);
    final OutboxMessage firstMessage = anOutboxMessage();
    final OutboxMessage secondMessage = anOutboxMessage();
    final OutboxMessage thirdMessage = anOutboxMessage();
    this.repository.init(List.of(firstMessage, secondMessage, thirdMessage));

    this.task.run();

    this.assertPublishedMessages(List.of(firstMessage, secondMessage));
    this.assertPendingMessages(List.of(thirdMessage));
  }

  @Test
  void when_no_messages_do_nothing() {
    this.repository.init(List.of());

    this.task.run();

    this.assertPublishedMessages(List.of());
    this.assertPendingMessages(List.of());
  }

  @Test
  void when_the_number_of_unpublished_messages_are_less_or_equal_than_the_batch_size() {
    final int batchSize = 2;
    final PublishingProperties properties = new PublishingProperties(batchSize, "KAFKA_MESSAGE_KEY", Set.of(), false, false);
    this.task = new OutboxPublishingTask(this.repository, this.parallelPublisher,
        new KeyGroupingStrategy(new KafkaKeyGroupingKeyGenerator()), properties);
    final OutboxMessage firstMessage = anOutboxMessage();
    final OutboxMessage secondMessage = anOutboxMessage();
    this.repository.init(List.of(firstMessage, secondMessage));

    this.task.run();

    this.assertPendingMessages(List.of());
    this.assertPublishedMessages(List.of(firstMessage, secondMessage));
  }

  @Test
  void maintains_order_by_destination_using_destination_strategy() {
    final OutboxMessage msg3 = anOutboxMessageBuilder().destination("3").build();
    final OutboxMessage msg2A = anOutboxMessageBuilder().destination("2").build();
    final OutboxMessage msg2B = anOutboxMessageBuilder().destination("2").build();
    final OutboxMessage msg1A = anOutboxMessageBuilder().destination("1").build();
    final OutboxMessage msg1B = anOutboxMessageBuilder().destination("1").build();
    // this order is not important
    this.repository.init(List.of(msg3, msg2B, msg2A, msg1A, msg1B));

    this.task.run();

    this.assertPendingMessages(List.of());
    // just fail if the order in the same destination is not maintained
    this.assertPublishedMessages(List.of(msg1A, msg1B, msg2A, msg2B, msg3));
  }

  @Test
  void maintains_order_by_kafka_key_using_kafka_key_strategy() {
    final PublishingProperties properties = new PublishingProperties(DEFAULT_BATCH_SIZE, "KAFKA_MESSAGE_KEY",
        Set.of(), false, false);
    this.task = new OutboxPublishingTask(this.repository, this.parallelPublisher,
        new KeyGroupingStrategy(new KafkaKeyGroupingKeyGenerator()), properties);

    final OutboxMessage msg1A = anOutboxMessageBuilder().destination("1").headers(Map.of(KafkaHeaders.KEY, "1"))
        .build();
    final OutboxMessage msg1B = anOutboxMessageBuilder().destination("1").headers(Map.of(KafkaHeaders.KEY, "1"))
        .build();
    final OutboxMessage msg1C = anOutboxMessageBuilder().destination("1").headers(Map.of(KafkaHeaders.KEY, "2"))
        .build();
    final OutboxMessage msg2A = anOutboxMessageBuilder().destination("2").headers(Map.of(KafkaHeaders.KEY, "1"))
        .build();
    // this order is not important
    this.repository.init(List.of(msg1C, msg1A, msg1B, msg2A));

    this.task.run();

    this.assertPendingMessages(List.of());
    // just fail if the order in the same destination an kafka key is not maintained
    this.assertPublishedMessages(List.of(msg1A, msg1B, msg1C, msg2A));
  }

  @Test
  void failed_messages_block_subsequent_messages_with_same_destination() {
    final OutboxMessage msg1A = anOutboxMessageBuilder().destination("1").build();
    final OutboxMessage msg1B = anOutboxMessageBuilder().destination("1").build();
    final OutboxMessage msg1C = anOutboxMessageBuilder().destination("1").build();
    final OutboxMessage msg1D = anOutboxMessageBuilder().destination("1").build();
    final OutboxMessage msg2A = anOutboxMessageBuilder().destination("2").build();
    final OutboxMessage msg2B = anOutboxMessageBuilder().destination("2").build();

    this.repository.init(List.of(msg1A, msg1B, msg1C, msg1D, msg2A, msg2B));

    // simulate fail sending third message of destination 1 and second message of
    // destination 2
    this.configureFailingMessages(List.of(msg1C, msg2B));

    this.task.run();

    this.assertPublishedMessages(List.of(msg1A, msg1B, msg2A));
    this.assertPendingMessages(List.of(msg1C, msg1D, msg2B));
  }

  private void configureFailingMessages(final List<OutboxMessage> messages) {
    messages.forEach(message -> when(this.messageSender.send(message)).thenReturn(false));
  }

  @Test
  void paused_destinations_are_filtered_out_and_not_processed() {
    final Set<String> pausedDestinations = Set.of("paused-1", "paused-2");
    final PublishingProperties properties = new PublishingProperties(DEFAULT_BATCH_SIZE, "DESTINATION",
        pausedDestinations, false, false);
    this.task = new OutboxPublishingTask(this.repository, this.parallelPublisher,
        new KeyGroupingStrategy(new DestinationGroupingKeyGenerator()), properties);

    final OutboxMessage enabledMessage = anOutboxMessageBuilder().destination("enabled").build();
    final OutboxMessage paused1Message = anOutboxMessageBuilder().destination("paused-1").build();
    final OutboxMessage paused2Message = anOutboxMessageBuilder().destination("paused-2").build();
    final OutboxMessage anotherEnabledMessage = anOutboxMessageBuilder().destination("another-enabled").build();

    this.repository.init(List.of(enabledMessage, paused1Message, paused2Message, anotherEnabledMessage));

    this.task.run();

    // Only enabled destinations should be processed
    this.assertPublishedMessages(List.of(enabledMessage, anotherEnabledMessage));
    // paused destination messages should remain pending (never retrieved from DB)
    this.assertPendingMessages(List.of(paused1Message, paused2Message));
  }

  @Test
  void paused_destinations_do_not_count_towards_batch_size_limit() {
    final int batchSize = 2;
    final Set<String> pausedDestinations = Set.of("paused-dest");
    final PublishingProperties properties = new PublishingProperties(batchSize, "DESTINATION", pausedDestinations,
        false, false);
    this.task = new OutboxPublishingTask(this.repository, this.parallelPublisher,
        new KeyGroupingStrategy(new DestinationGroupingKeyGenerator()), properties);

    // Create messages with controlled capturedAt times to ensure predictable
    // ordering
    final Instant baseTime = Instant.parse("2023-01-01T10:00:00Z");

    // First in time: paused message (should be filtered out, not count towards
    // batch)
    final OutboxMessage pausedMessage1 = anOutboxMessageBuilder()
        .destination("paused-dest")
        .capturedAt(baseTime)
        .build();

    // Second in time: enabled message (should be processed - 1st in batch)
    final OutboxMessage enabledMessage1 = anOutboxMessageBuilder()
        .destination("enabled-dest")
        .capturedAt(baseTime.plusSeconds(1))
        .build();

    // Third in time: another paused message (should be filtered out, not count
    // towards batch)
    final OutboxMessage pausedMessage2 = anOutboxMessageBuilder()
        .destination("paused-dest")
        .capturedAt(baseTime.plusSeconds(2))
        .build();

    // Fourth in time: enabled message (should be processed - 2nd in batch)
    final OutboxMessage enabledMessage2 = anOutboxMessageBuilder()
        .destination("enabled-dest")
        .capturedAt(baseTime.plusSeconds(3))
        .build();

    // Fifth in time: enabled message (should NOT be processed - exceeds batch size)
    final OutboxMessage enabledMessage3 = anOutboxMessageBuilder()
        .destination("enabled-dest")
        .capturedAt(baseTime.plusSeconds(4))
        .build();

    this.repository.init(List.of(pausedMessage1, enabledMessage1, pausedMessage2, enabledMessage2, enabledMessage3));

    this.task.run();

    // Key test: Only 2 enabled messages processed despite paused messages being
    // interleaved
    // This proves paused messages don't count towards batch size since they're
    // filtered at query level
    this.assertPublishedMessages(List.of(enabledMessage1, enabledMessage2));

    // Third enabled message should remain pending (batch size exceeded)
    // All paused messages should remain pending (never retrieved from DB)
    this.assertPendingMessages(List.of(pausedMessage1, pausedMessage2, enabledMessage3));
  }

  @Test
  void globally_paused_publishing_processes_no_messages() {
    final PublishingProperties properties = new PublishingProperties(DEFAULT_BATCH_SIZE, "DESTINATION", Set.of(),
        true, false);
    this.task = new OutboxPublishingTask(this.repository, this.parallelPublisher,
        new KeyGroupingStrategy(new DestinationGroupingKeyGenerator()), properties);

    final OutboxMessage message1 = anOutboxMessage();
    final OutboxMessage message2 = anOutboxMessage();
    this.repository.init(List.of(message1, message2));

    this.task.run();

    // When globally paused, no messages should be processed
    this.assertPublishedMessages(List.of());
    // All messages should remain pending
    this.assertPendingMessages(List.of(message1, message2));
  }

  @Test
  void globally_paused_publishing_ignores_paused_destinations() {
    final Set<String> pausedDestinations = Set.of("destination1");
    final PublishingProperties properties = new PublishingProperties(DEFAULT_BATCH_SIZE, "DESTINATION",
        pausedDestinations, true, false);
    this.task = new OutboxPublishingTask(this.repository, this.parallelPublisher,
        new KeyGroupingStrategy(new DestinationGroupingKeyGenerator()), properties);

    final OutboxMessage message1 = anOutboxMessageBuilder().destination("destination1").build();
    final OutboxMessage message2 = anOutboxMessageBuilder().destination("destination2").build();
    this.repository.init(List.of(message1, message2));

    this.task.run();

    // When globally paused, NO messages should be processed, regardless of paused
    // destinations
    this.assertPublishedMessages(List.of());
    // All messages should remain pending
    this.assertPendingMessages(List.of(message1, message2));
  }

}
