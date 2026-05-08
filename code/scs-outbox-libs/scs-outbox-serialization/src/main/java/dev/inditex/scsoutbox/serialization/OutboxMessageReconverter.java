package dev.inditex.scsoutbox.serialization;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.OutboxServiceProperties;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.support.MessageBuilder;

@RequiredArgsConstructor
public class OutboxMessageReconverter {

  private final OutboxServiceProperties outboxServiceProperties;

  private final CompositeMessageConverter compositeMessageConverter;

  public @Nullable Object reconvertPayload(OutboxMessage outboxMessage) {
    if (this.outboxServiceProperties.useNativeEncoding(outboxMessage.getBindingName())) {
      return outboxMessage.getPayload();
    }
    final Message<@NonNull Object> message =
        MessageBuilder.withPayload(outboxMessage.getPayload()).copyHeaders(outboxMessage.getHeaders()).build();
    return this.compositeMessageConverter.fromMessage(message, Object.class);
  }
}
