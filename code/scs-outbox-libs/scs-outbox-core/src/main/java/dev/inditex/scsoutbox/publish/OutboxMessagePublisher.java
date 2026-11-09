package dev.inditex.scsoutbox.publish;

import java.util.List;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.OutboxMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
public class OutboxMessagePublisher {

  private final OutboxMessageSender messageSender;

  private final OutboxMessageRepository outboxMessageRepository;

  private final List<OutboxMessagePublisherInterceptor> interceptors;

  @Transactional
  public void publish(final OutboxMessage message) {
    final boolean sent = this.messageSender.send(message);
    if (!sent) {
      throw new MessageNotPublishedException(
          "message [" + message.getId() + "] not published.");
    }
    this.postSend(message);
    this.outboxMessageRepository.delete(message);
    log.info("message [" + message.getId() + "] published. " + message);
  }

  /**
   * Runs every registered {@link OutboxMessagePublisherInterceptor} for the given message. Interceptors are treated as best-effort side
   * effects: an exception thrown by one interceptor is caught and logged, and never prevents the remaining interceptors from running, nor
   * the outbox message from being deleted afterwards.
   *
   * @param message the message that was just published to the broker
   */
  private void postSend(final OutboxMessage message) {
    this.interceptors.forEach(interceptor -> {
      try {
        interceptor.postSend(message);
      } catch (final Exception e) {
        log.error("Post-send interceptor [{}] failed for message [{}]; the message was already published and will still be removed "
            + "from the outbox.", interceptor.getClass().getName(), message.getId(), e);
      }
    });
  }

}
