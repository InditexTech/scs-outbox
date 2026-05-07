package dev.inditex.scsoutbox.interceptor;

import static dev.inditex.scsoutbox.publish.StreamBridgeOutboxMessageSender.SCS_OUTBOX_PUBLISH_MARK_HEADER;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import dev.inditex.scsoutbox.MessageCaptureTxService;
import dev.inditex.scsoutbox.OutboxServiceProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

class OutboxChannelInterceptorTest {

  private OutboxChannelInterceptor interceptor;

  private AbstractMessageChannel channel;

  private MessageCaptureTxService messageCaptureTxService;

  private OutboxServiceProperties outboxServiceProperties;

  @BeforeEach
  void setUp() {
    this.messageCaptureTxService = mock(MessageCaptureTxService.class);
    final MessageChannelAccessor messageChannelAccessor = mock(MessageChannelAccessor.class);
    this.outboxServiceProperties = mock(OutboxServiceProperties.class);
    this.interceptor = new OutboxChannelInterceptor(
        this.messageCaptureTxService, messageChannelAccessor, this.outboxServiceProperties);
    this.channel = mock(AbstractMessageChannel.class);
  }

  @Test
  void when_outbox_is_disable_for_binding_name_then_return_original_message() {
    final Message<?> message = MessageBuilder.createMessage("payload", new MessageHeaders(Map.of()));
    when(this.outboxServiceProperties.isOutboxEnabledFor(any())).thenReturn(false);

    final Message<?> result = this.interceptor.preSend(message, this.channel);

    assertEquals(message, result);
  }

  @Test
  void when_outbox_is_enabled_and_message_is_marked_then_return_message_without_mark() {
    when(this.outboxServiceProperties.isOutboxEnabledFor(any())).thenReturn(true);
    final Message<?> message = MessageBuilder.createMessage(
        "payload", new MessageHeaders(Map.of(
            SCS_OUTBOX_PUBLISH_MARK_HEADER, "")));

    final Message<?> result = this.interceptor.preSend(message, this.channel);

    assertEquals(message.getPayload(), result.getPayload());
    assertFalse(result.getHeaders().containsKey(SCS_OUTBOX_PUBLISH_MARK_HEADER));
  }

  @Test
  void when_outbox_is_enabled_then_retain_message_and_return_null() {
    when(this.outboxServiceProperties.isOutboxEnabledFor(any())).thenReturn(true);

    final Message<?> message = MessageBuilder.createMessage(
        "payload", new MessageHeaders(Map.of()));

    final Message<?> result = this.interceptor.preSend(message, this.channel);

    verify(this.messageCaptureTxService).capture(any(), any());
    assertNull(result);
  }

  @Test
  void when_message_is_error_message_then_skip_outbox_processing() {
    when(this.outboxServiceProperties.isOutboxEnabledFor(any())).thenReturn(true);

    final Message<?> message = new ErrorMessage(new RuntimeException("Test error"));

    final Message<?> result = this.interceptor.preSend(message, this.channel);

    verify(this.messageCaptureTxService, never()).capture(any(), any());
    assertEquals(message, result);
  }

}
