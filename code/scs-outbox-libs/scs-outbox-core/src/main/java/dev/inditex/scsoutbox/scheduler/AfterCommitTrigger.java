package dev.inditex.scsoutbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Aspect
@RequiredArgsConstructor
@Slf4j
public class AfterCommitTrigger {

  private final ApplicationEventPublisher applicationEventPublisher;

  private final OutboxScheduledService outboxScheduledService;

  @After(
      value = "execution(* dev.inditex.scsoutbox.MessageCaptureTxService.capture(..))")
  public void publishMessageCapturedEvent() {
    log.debug("Message captured");
    this.applicationEventPublisher.publishEvent(new MessageCaptured() {});
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void afterCommit(final MessageCaptured event) {
    log.debug("Triggering outbox publishing task after commit. on event: " + event.getClass().getSimpleName());
    this.outboxScheduledService.outboxPublishingTask();
  }

  public interface MessageCaptured {

  }
}
