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

  private void postSend(final OutboxMessage message) {
    this.interceptors.forEach(interceptor -> interceptor.postSend(message));
  }

}
