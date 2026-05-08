package dev.inditex.scsoutbox.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger.MessageCaptured;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

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

  @Test
  void publish_message_capture_event() {
    this.afterCommitTrigger.publishMessageCapturedEvent();

    verify(this.applicationEventPublisher).publishEvent(any(MessageCaptured.class));
  }

  @Test
  void execute_outbox_publishing_task() {
    this.afterCommitTrigger.afterCommit(new MessageCaptured() {});

    verify(this.outboxScheduledService).outboxPublishingTask();
  }

}
