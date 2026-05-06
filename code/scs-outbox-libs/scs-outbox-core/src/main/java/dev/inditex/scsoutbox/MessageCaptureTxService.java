package dev.inditex.scsoutbox;

import java.time.Instant;
import java.util.UUID;

import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class MessageCaptureTxService {

  private final OutboxMessageRepository repository;

  private final OutboxServiceProperties outboxServiceProperties;

  @Timed("outbox.capture.time")
  @Transactional(propagation = Propagation.MANDATORY)
  public void capture(final String bindingName, final Message<?> msg) {
    final OutboxMessage message = OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.now())
        .destination(this.outboxServiceProperties.getDestination(bindingName))
        .bindingName(bindingName)
        .headers(msg.getHeaders())
        .payload(msg.getPayload())
        .build();
    this.repository.save(message);
  }
}
