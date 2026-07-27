package dev.inditex.scsoutbox.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@RequiredArgsConstructor
@Slf4j
public class AfterCommitTrigger {

  /**
   * Bean name of the dedicated {@link java.util.concurrent.Executor} used to run {@link #afterCommit(MessageCaptured)}. Registered as the
   * default bean under this same name by {@code OutboxAutoConfiguration}, which references this constant to avoid duplicating the literal
   * (a mismatch would make {@code @Async} silently resolve a different executor at runtime).
   */
  public static final String OUTBOX_AFTER_COMMIT_EXECUTOR_BEAN_NAME = "outboxAfterCommitExecutor";

  private final ApplicationEventPublisher applicationEventPublisher;

  private final OutboxScheduledService outboxScheduledService;

  /**
   * Publishes a {@link MessageCaptured} event when at least one message is captured in the current transaction.
   *
   * <p>{@code capture(..)} is {@code Propagation.MANDATORY}, so it always runs inside an already active transaction. When multiple messages
   * are captured within the same (larger) transaction, this method is invoked multiple times but only publishes a single event for that
   * transaction: subsequent invocations detect that an event has already been scheduled and skip publishing. This avoids registering one
   * {@code AFTER_COMMIT} listener (and submitting one async task) per captured message, and instead triggers {@code outboxPublishingTask()}
   * exactly once per transaction.
   */
  @After(
      value = "execution(* dev.inditex.scsoutbox.MessageCaptureTxService.capture(..))")
  public void publishMessageCapturedEvent() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      if (TransactionSynchronizationManager.hasResource(this)) {
        log.trace("Message captured, publishing event already scheduled for this transaction");
        return;
      }
      TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

        @Override
        public void afterCompletion(final int status) {
          TransactionSynchronizationManager.unbindResourceIfPossible(AfterCommitTrigger.this);
        }
      });
    }
    log.debug("Message captured");
    this.applicationEventPublisher.publishEvent(new MessageCaptured() {});
  }

  /**
   * Triggers the outbox publishing task after a transaction commits. Runs on the dedicated {@code outboxAfterCommitExecutor} executor,
   * decoupled from the application's default {@code @Async} executor and from {@code outboxExecutorService} (used by the publishing task
   * itself) to avoid task rejection on bulk transactions and thread starvation between the two executors.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async(OUTBOX_AFTER_COMMIT_EXECUTOR_BEAN_NAME)
  public void afterCommit(final MessageCaptured event) {
    log.debug("Triggering outbox publishing task after commit. on event: {}", event.getClass().getName());
    this.outboxScheduledService.outboxPublishingTask();
  }

  public interface MessageCaptured {

  }
}
