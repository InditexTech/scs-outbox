package dev.inditex.scsoutbox.publish;

import dev.inditex.scsoutbox.OutboxMessage;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

@RequiredArgsConstructor
public class StreamBridgeOutboxMessageSender implements OutboxMessageSender {

  public static final String SCS_OUTBOX_PUBLISH_MARK_HEADER = "scs-outbox-publish-mark";

  private final StreamBridge bridge;

  private final BindingServiceProperties bindingServiceProperties;

  public boolean send(final OutboxMessage outboxMessage) {
    if (outboxMessage.getPayload() instanceof byte[]) {
      return this.sendRaw(outboxMessage);
    }
    return this.sendDefault(outboxMessage);
  }

  /**
   * Sends a message with a raw byte[] payload, bypassing Spring Cloud Stream re-serialization. The {@link OutboxMessage} is sent as the
   * message payload with the custom {@link OutboxMessageConverter#SCS_OUTBOX_MESSAGE_MIME_TYPE} so that only the
   * {@link OutboxMessageConverter} processes it — no other converter in the SCS chain recognizes this MIME type.
   *
   * <p>The publish mark header is set on the wrapper {@code Message} so the
   * {@link dev.inditex.scsoutbox.interceptor.OutboxChannelInterceptor} recognizes this as an outbox-published message and lets it through
   * (instead of capturing it again). The converter then handles the full conversion: extracting the raw bytes and copying the captured
   * headers (including the original {@code contentType}).
   */
  private boolean sendRaw(final OutboxMessage outboxMessage) {
    final Message<OutboxMessage> message = MessageBuilder
        .withPayload(outboxMessage)
        .setHeader(SCS_OUTBOX_PUBLISH_MARK_HEADER, "")
        .build();
    return this.bridge.send(outboxMessage.getBindingName(), message, OutboxMessageConverter.SCS_OUTBOX_MESSAGE_MIME_TYPE);
  }

  /**
   * Sends a message using the default Spring Cloud Stream pipeline, which will re-serialize the payload.
   */
  private boolean sendDefault(final OutboxMessage outboxMessage) {
    final MimeType contentType = this.getBindingContentType(outboxMessage.getBindingName());

    final Message<Object> message = MessageBuilder
        .withPayload(outboxMessage.getPayload())
        .copyHeaders(outboxMessage.getHeaders())
        .setHeader(SCS_OUTBOX_PUBLISH_MARK_HEADER, "")
        .build();
    return this.bridge.send(
        outboxMessage.getBindingName(),
        message,
        contentType);
  }

  private MimeType getBindingContentType(final String bindingName) {
    final String contentType = this.bindingServiceProperties.getBindingProperties(bindingName).getContentType();
    return MimeType.valueOf(contentType);
  }
}
