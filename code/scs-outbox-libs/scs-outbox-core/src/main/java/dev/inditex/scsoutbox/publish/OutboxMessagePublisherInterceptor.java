package dev.inditex.scsoutbox.publish;

import dev.inditex.scsoutbox.OutboxMessage;

/**
 * Extension point invoked after an outbox message has been successfully published to the broker.
 *
 * <p>Implementations are treated as best-effort side effects (e.g. archiving): {@link OutboxMessagePublisher} catches and logs any
 * exception thrown from {@link #postSend(OutboxMessage)}, so a failure here never prevents the message from being removed from the outbox,
 * nor causes it to be republished. Callers that need the outcome of this step to be reliable must implement their own compensation/alerting
 * logic; this contract only guarantees that the outbox's own delivery guarantees (at-least-once, ordering) are never affected by a failure
 * in an interceptor.
 */
public interface OutboxMessagePublisherInterceptor {

  /**
   * Called after the message has been successfully sent to the broker.
   *
   * @param outboxMessage the message that was just published
   */
  void postSend(OutboxMessage outboxMessage);

}
