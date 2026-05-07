package dev.inditex.scsoutbox.interceptor;

import static dev.inditex.scsoutbox.publish.StreamBridgeOutboxMessageSender.SCS_OUTBOX_PUBLISH_MARK_HEADER;

import dev.inditex.scsoutbox.MessageCaptureTxService;
import dev.inditex.scsoutbox.OutboxServiceProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
@RequiredArgsConstructor
@NullMarked
public class OutboxChannelInterceptor implements ChannelInterceptor {

  private final MessageCaptureTxService messageCaptureTxService;

  private final MessageChannelAccessor messageChannelAccessor;

  private final OutboxServiceProperties outboxServiceProperties;

  @Override
  public @Nullable Message<?> preSend(final Message<?> message, final MessageChannel channel) {
    final String bindingName = this.messageChannelAccessor.getBindingName(channel);

    // ErrorMessages are not allowed to be processed by outbox.
    // ErrorMessages are system-level error handling messages from Spring Cloud Stream/Integration
    // and should not be part of the transactional outbox pattern.
    if (message instanceof ErrorMessage) {
      log.debug("Skipping ErrorMessage from outbox processing for channel: {}", bindingName);
      return message;
    }

    if (this.isMarked(message)) {
      return cleanMark(message);
    }
    if (this.outboxServiceProperties.isOutboxEnabledFor(bindingName)) {
      this.messageCaptureTxService.capture(bindingName, message);
      return null;
      // return null because we need to stop the publishing message flow.
    }
    return message;
  }

  private static Message<?> cleanMark(final Message<?> message) {
    return MessageBuilder
        .fromMessage(message)
        .removeHeader(SCS_OUTBOX_PUBLISH_MARK_HEADER)
        .build();
  }

  private boolean isMarked(final Message<?> message) {
    return message.getHeaders().containsKey(SCS_OUTBOX_PUBLISH_MARK_HEADER);
  }

}
