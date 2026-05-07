package dev.inditex.scsoutbox.publish;

import static dev.inditex.scsoutbox.publish.StreamBridgeOutboxMessageSender.SCS_OUTBOX_PUBLISH_MARK_HEADER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeType;

@ExtendWith(MockitoExtension.class)
class StreamBridgeOutboxMessageSenderTest {

  private StreamBridgeOutboxMessageSender sender;

  @Mock
  private StreamBridge bridge;

  @Mock
  private BindingServiceProperties bindingServiceProperties;

  @Captor
  private ArgumentCaptor<Message<?>> messageCaptor;

  @BeforeEach
  void beforeEach() {
    this.sender = new StreamBridgeOutboxMessageSender(this.bridge, this.bindingServiceProperties);
  }

  @Nested
  class SendDefault {

    @Test
    void when_payload_is_object_expect_default_send_with_content_type() {
      final BindingProperties bindingProps = new BindingProperties();
      bindingProps.setContentType("application/json");
      doReturn(bindingProps).when(StreamBridgeOutboxMessageSenderTest.this.bindingServiceProperties)
          .getBindingProperties("myBinding");
      doReturn(true).when(StreamBridgeOutboxMessageSenderTest.this.bridge)
          .send(any(String.class), any(Object.class), any(MimeType.class));
      final OutboxMessage outboxMessage = OutboxMessage.builder()
          .id(UUID.randomUUID())
          .capturedAt(Instant.parse("2025-10-22T18:45:00Z"))
          .destination("myDestination")
          .bindingName("myBinding")
          .payload("stringPayload")
          .headers(Map.of())
          .build();

      final boolean result = StreamBridgeOutboxMessageSenderTest.this.sender.send(outboxMessage);

      assertThat(result).isTrue();
      verify(StreamBridgeOutboxMessageSenderTest.this.bridge, times(1))
          .send(eq("myBinding"), StreamBridgeOutboxMessageSenderTest.this.messageCaptor.capture(),
              eq(MimeType.valueOf("application/json")));
      final Message<?> sentMessage = StreamBridgeOutboxMessageSenderTest.this.messageCaptor.getValue();
      assertThat(sentMessage.getPayload()).isEqualTo("stringPayload");
      assertThat(sentMessage.getHeaders()).containsKey(SCS_OUTBOX_PUBLISH_MARK_HEADER);
    }
  }

  @Nested
  class SendRaw {

    @Test
    void when_payload_is_byte_array_expect_outbox_message_sent_with_custom_mime_type() {
      doReturn(true).when(StreamBridgeOutboxMessageSenderTest.this.bridge)
          .send(any(String.class), any(Object.class), any(MimeType.class));
      final byte[] rawPayload = new byte[]{1, 2, 3, 4, 5};
      final OutboxMessage outboxMessage = OutboxMessage.builder()
          .id(UUID.randomUUID())
          .capturedAt(Instant.parse("2025-10-22T18:45:00Z"))
          .destination("myDestination")
          .bindingName("myBinding")
          .payload(rawPayload)
          .headers(Map.of(MessageHeaders.CONTENT_TYPE, MimeType.valueOf("application/*+avro")))
          .build();

      final boolean result = StreamBridgeOutboxMessageSenderTest.this.sender.send(outboxMessage);

      assertThat(result).isTrue();
      verify(StreamBridgeOutboxMessageSenderTest.this.bridge, times(1))
          .send(eq("myBinding"), StreamBridgeOutboxMessageSenderTest.this.messageCaptor.capture(),
              eq(OutboxMessageConverter.SCS_OUTBOX_MESSAGE_MIME_TYPE));
      final Message<?> sentMessage = StreamBridgeOutboxMessageSenderTest.this.messageCaptor.getValue();
      assertThat(sentMessage.getPayload()).isInstanceOf(OutboxMessage.class);
      assertThat(((OutboxMessage) sentMessage.getPayload()).getPayload()).isEqualTo(rawPayload);
    }

    @Test
    void when_payload_is_byte_array_expect_outbox_message_carries_original_headers() {
      doReturn(true).when(StreamBridgeOutboxMessageSenderTest.this.bridge)
          .send(any(String.class), any(Object.class), any(MimeType.class));
      final MimeType capturedContentType = MimeType.valueOf("application/json");
      final Map<String, Object> originalHeaders = Map.of(
          "custom-header", "custom-value",
          MessageHeaders.CONTENT_TYPE, capturedContentType);
      final OutboxMessage outboxMessage = OutboxMessage.builder()
          .id(UUID.randomUUID())
          .capturedAt(Instant.parse("2025-10-22T18:45:00Z"))
          .destination("myDestination")
          .bindingName("myBinding")
          .payload(new byte[]{1, 2, 3})
          .headers(originalHeaders)
          .build();

      StreamBridgeOutboxMessageSenderTest.this.sender.send(outboxMessage);

      verify(StreamBridgeOutboxMessageSenderTest.this.bridge, times(1))
          .send(eq("myBinding"), StreamBridgeOutboxMessageSenderTest.this.messageCaptor.capture(),
              eq(OutboxMessageConverter.SCS_OUTBOX_MESSAGE_MIME_TYPE));
      final Message<?> sentMessage = StreamBridgeOutboxMessageSenderTest.this.messageCaptor.getValue();
      final OutboxMessage sentOutboxMessage = (OutboxMessage) sentMessage.getPayload();
      assertThat(sentOutboxMessage.getHeaders()).containsEntry("custom-header", "custom-value");
      assertThat(sentOutboxMessage.getHeaders()).containsEntry(MessageHeaders.CONTENT_TYPE, capturedContentType);
    }

    @Test
    void when_payload_is_byte_array_expect_publish_mark_header_in_message_wrapper() {
      doReturn(true).when(StreamBridgeOutboxMessageSenderTest.this.bridge)
          .send(any(String.class), any(Object.class), any(MimeType.class));
      final OutboxMessage outboxMessage = OutboxMessage.builder()
          .id(UUID.randomUUID())
          .capturedAt(Instant.parse("2025-10-22T18:45:00Z"))
          .destination("myDestination")
          .bindingName("myBinding")
          .payload(new byte[]{1, 2, 3})
          .headers(Map.of(MessageHeaders.CONTENT_TYPE, MimeType.valueOf("application/json")))
          .build();

      StreamBridgeOutboxMessageSenderTest.this.sender.send(outboxMessage);

      verify(StreamBridgeOutboxMessageSenderTest.this.bridge, times(1))
          .send(eq("myBinding"), StreamBridgeOutboxMessageSenderTest.this.messageCaptor.capture(),
              eq(OutboxMessageConverter.SCS_OUTBOX_MESSAGE_MIME_TYPE));
      final Message<?> sentMessage = StreamBridgeOutboxMessageSenderTest.this.messageCaptor.getValue();
      // The wrapper Message must carry the publish mark header so the OutboxChannelInterceptor
      // recognizes it as an outbox-published message and lets it through
      assertThat(sentMessage.getHeaders()).containsKey(SCS_OUTBOX_PUBLISH_MARK_HEADER);
    }
  }
}
