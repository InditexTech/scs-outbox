package dev.inditex.scsoutbox.publish;

import dev.inditex.scsoutbox.OutboxMessage;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

/**
 * A Spring {@link MessageConverter} that converts an {@link OutboxMessage} with a raw {@code byte[]} payload into a broker-ready
 * {@link Message} — extracting the raw bytes, copying the captured headers, and preserving the original content type.
 *
 * <p>This converter centralizes the conversion logic for the raw passthrough flow. The {@link StreamBridgeOutboxMessageSender} wraps the
 * {@code OutboxMessage} in a {@code Message<OutboxMessage>} (with the publish mark header for the interceptor) and delegates to
 * {@code StreamBridge.send()} with the custom MIME type. After the interceptor lets the message through, this converter handles the
 * transformation from domain object to wire-ready message.
 *
 * <p>This converter declares support exclusively for the custom {@link #SCS_OUTBOX_MESSAGE_MIME_TYPE}
 * ({@code application/x-scs-outbox-raw}). Because no other converter in the SCS {@code CompositeMessageConverter} chain recognizes this
 * MIME type, this converter is guaranteed to be the only one that processes the message — regardless of its position in the chain.
 *
 * <p>When converting, the converter: <ol> <li>Verifies the payload is an {@link OutboxMessage} with a {@code byte[]} payload</li>
 * <li>Extracts the raw bytes from the {@code OutboxMessage}</li> <li>Copies the captured message headers from the {@code OutboxMessage},
 * which already contain the original {@code contentType} properly resolved by the headers mapper</li> <li>Adds the
 * {@link StreamBridgeOutboxMessageSender#SCS_OUTBOX_PUBLISH_MARK_HEADER} so the
 * {@link dev.inditex.scsoutbox.interceptor.OutboxChannelInterceptor} recognizes the converted message as outbox-published and does not
 * capture it again</li> </ol>
 *
 * <p>This converter is only registered when {@code scs-outbox.use-scs-encoding=true}.
 */
@Slf4j
public class OutboxMessageConverter implements MessageConverter {

  /**
   * Custom MIME type used as a signal to route messages through this converter. No other SCS converter recognizes this type, so the
   * {@code CompositeMessageConverter} will skip all built-in converters and delegate to this one.
   */
  public static final MimeType SCS_OUTBOX_MESSAGE_MIME_TYPE = MimeType.valueOf("application/scs-outbox-message");

  @Override
  public @Nullable Object fromMessage(final Message<?> message, final Class<?> targetClass) {
    // This converter is outbound-only; inbound conversion is not supported
    return null;
  }

  @Override
  public @Nullable Message<?> toMessage(final Object payload, final @Nullable MessageHeaders headers) {
    if (payload instanceof OutboxMessage outboxMessage && outboxMessage.getPayload() instanceof byte[]) {
      log.debug("Raw passthrough: converting OutboxMessage [{}] with contentType [{}]",
          outboxMessage.getId(), outboxMessage.getHeaders().get(MessageHeaders.CONTENT_TYPE));
      return MessageBuilder
          .withPayload(outboxMessage.getPayload())
          // copy original message headers
          .copyHeaders(outboxMessage.getHeaders())
          // copy additional headers without overwriting, like, SCS_OUTBOX_PUBLISH_MARK_HEADER
          .copyHeadersIfAbsent(headers)
          // .setHeader(StreamBridgeOutboxMessageSender.SCS_OUTBOX_PUBLISH_MARK_HEADER, "")
          .build();
    }
    return null;
  }

}
