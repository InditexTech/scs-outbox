package dev.inditex.scsoutbox.publish;

import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import dev.inditex.scsoutbox.InMemoryOutboxMessageRepository;
import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.config.PublishingProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import org.mockito.Mockito;

abstract class AbstractOutboxPublishingTaskTest {

  // Shared constants
  protected static final int DEFAULT_BATCH_SIZE = 1000;

  protected static final int DEFAULT_SEND_DELAY = 30;

  protected static final Boolean DEFAULT_SEND_RESPONSE = true;

  protected static final int DEFAULT_POOL_SIZE = 5;

  // Common components
  protected InMemoryOutboxMessageRepository repository;

  protected OutboxMessageSender messageSender;

  protected OutboxMessagePublisher publisher;

  protected ExecutorService executorService;

  protected ParallelPublisher parallelPublisher;

  protected PublishingProperties publishingProperties;

  protected OutboxPublishingTask task;

  @BeforeEach
  public void setUp() {
    this.setupBasicComponents();
    this.setupDefaultTask();
  }

  protected void setupBasicComponents() {
    this.repository = new InMemoryOutboxMessageRepository();
    this.messageSender = mock(OutboxMessageSender.class);
    when(this.messageSender.send(any())).thenReturn(true);
    this.publisher = new OutboxMessagePublisher(this.messageSender, this.repository, List.of());
    this.executorService = Executors.newSingleThreadExecutor();
    this.parallelPublisher = new ParallelPublisher(this.executorService, this.publisher);
    this.publishingProperties = new PublishingProperties(DEFAULT_BATCH_SIZE, "DESTINATION", Set.of(), false, false);
  }

  protected void setupDefaultTask() {
    this.task = new OutboxPublishingTask(
        this.repository,
        this.parallelPublisher,
        new KeyGroupingStrategy(new DestinationGroupingKeyGenerator()),
        this.publishingProperties);
  }

  @AfterEach
  public void tearDown() {
    this.executorService.shutdownNow();
  }

  protected void assertPublishedMessages(final List<OutboxMessage> publishedMessages) {
    final List<OutboxMessage> unpublishedMessages = this.repository.findAllOrderByCapturedAt(UNLIMITED);
    final Map<String, List<OutboxMessage>> publishedMessagesByDestination = publishedMessages.stream()
        .collect(Collectors.groupingBy(OutboxMessage::getDestination));
    publishedMessagesByDestination.keySet().forEach(
        destination -> {
          final InOrder inOrder = Mockito.inOrder(this.messageSender);
          publishedMessagesByDestination.get(destination).forEach(
              publishedMessage -> {
                inOrder.verify(this.messageSender).send(publishedMessage);
                assertFalse(unpublishedMessages.contains(publishedMessage),
                    "expected message to be published but found it pending");
              });
        });
  }

  protected void assertPendingMessages(final List<OutboxMessage> outboxMessages) {
    assertEquals(this.repository.findAllOrderByCapturedAt(UNLIMITED), outboxMessages);
  }

}
