package dev.inditex.scsoutbox.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger.MessageCaptured;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTriggerTest {

  private AfterCommitTrigger afterCommitTrigger;

  private ApplicationEventPublisher applicationEventPublisher;

  private OutboxScheduledService outboxScheduledService;

  @BeforeEach
  void setUp() {
    this.applicationEventPublisher = mock(ApplicationEventPublisher.class);
    this.outboxScheduledService = mock(OutboxScheduledService.class);
    this.afterCommitTrigger = new AfterCommitTrigger(this.applicationEventPublisher, this.outboxScheduledService);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void publish_message_capture_event() {
    this.afterCommitTrigger.publishMessageCapturedEvent();

    verify(this.applicationEventPublisher).publishEvent(any(MessageCaptured.class));
  }

  @Test
  void publish_message_capture_event_only_once_per_transaction() {
    TransactionSynchronizationManager.initSynchronization();

    this.afterCommitTrigger.publishMessageCapturedEvent();
    this.afterCommitTrigger.publishMessageCapturedEvent();
    this.afterCommitTrigger.publishMessageCapturedEvent();

    verify(this.applicationEventPublisher, times(1)).publishEvent(any(MessageCaptured.class));
  }

  @Test
  void publish_message_capture_event_again_for_a_new_transaction() {
    TransactionSynchronizationManager.initSynchronization();
    this.afterCommitTrigger.publishMessageCapturedEvent();
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    TransactionSynchronizationManager.clearSynchronization();

    TransactionSynchronizationManager.initSynchronization();
    this.afterCommitTrigger.publishMessageCapturedEvent();

    verify(this.applicationEventPublisher, times(2)).publishEvent(any(MessageCaptured.class));
  }

  @Test
  void unbinds_resource_after_transaction_completes_with_rollback() {
    TransactionSynchronizationManager.initSynchronization();
    this.afterCommitTrigger.publishMessageCapturedEvent();

    assertThat(TransactionSynchronizationManager.hasResource(this.afterCommitTrigger)).isTrue();

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertThat(TransactionSynchronizationManager.hasResource(this.afterCommitTrigger)).isFalse();
  }

  @Test
  void execute_outbox_publishing_task() {
    this.afterCommitTrigger.afterCommit(new MessageCaptured() {});

    verify(this.outboxScheduledService).outboxPublishingTask();
  }

}
