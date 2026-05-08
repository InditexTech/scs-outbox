package dev.inditex.scsoutbox.publish;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

class OutboxMessageConverterTest {

  private final OutboxMessageConverter converter = new OutboxMessageConverter();

  @Nested
  class ToMessage {

    @Test
    void when_outbox_message_with_byte_array_expect_same_bytes_returned() {
      final byte[] data = new byte[]{1, 2, 3, 4, 5};
      final OutboxMessage outboxMessage = OutboxMessageConverterTest.this.buildOutboxMessage(data,
          Map.of(MessageHeaders.CONTENT_TYPE, MimeType.valueOf("application/json")));
      final MessageHeaders headers = MessageBuilder.withPayload(outboxMessage).build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(outboxMessage, headers);

      assertThat(result).isNotNull();
      assertThat(result.getPayload()).isEqualTo(data);
    }

    @Test
    void when_outbox_message_converted_expect_original_content_type_preserved() {
      final MimeType originalContentType = MimeType.valueOf("application/*+avro");
      final OutboxMessage outboxMessage = OutboxMessageConverterTest.this.buildOutboxMessage(new byte[]{10, 20},
          Map.of(MessageHeaders.CONTENT_TYPE, originalContentType));
      final MessageHeaders headers = MessageBuilder.withPayload(outboxMessage).build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(outboxMessage, headers);

      assertThat(result).isNotNull();
      assertThat(result.getHeaders()).containsEntry(MessageHeaders.CONTENT_TYPE, originalContentType);
    }

    @Test
    void when_outbox_message_converted_expect_captured_headers_preserved() {
      final Map<String, Object> capturedHeaders = Map.of(
          MessageHeaders.CONTENT_TYPE, MimeType.valueOf("application/json"),
          "kafka_messageKey", "my-key",
          "custom-header", "custom-value");
      final OutboxMessage outboxMessage = OutboxMessageConverterTest.this.buildOutboxMessage(new byte[]{1}, capturedHeaders);
      final MessageHeaders headers = MessageBuilder.withPayload(outboxMessage).build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(outboxMessage, headers);

      assertThat(result).isNotNull();
      assertThat(result.getHeaders()).containsEntry("kafka_messageKey", "my-key");
      assertThat(result.getHeaders()).containsEntry("custom-header", "custom-value");
    }

    @Test
    void when_outbox_message_has_no_content_type_header_expect_message_without_content_type() {
      final OutboxMessage outboxMessage = OutboxMessageConverterTest.this.buildOutboxMessage(new byte[]{1, 2}, Map.of());
      final MessageHeaders headers = MessageBuilder.withPayload(outboxMessage).build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(outboxMessage, headers);

      assertThat(result).isNotNull();
      assertThat(result.getPayload()).isEqualTo(new byte[]{1, 2});
    }

    @Test
    void when_outer_headers_have_key_absent_in_outbox_headers_expect_it_is_added_to_result() {
      final OutboxMessage outboxMessage = OutboxMessageConverterTest.this.buildOutboxMessage(new byte[]{1},
          Map.of(MessageHeaders.CONTENT_TYPE, MimeType.valueOf("application/json")));
      final MessageHeaders outerHeaders = MessageBuilder.withPayload(new byte[0])
          .setHeader("outer-only-header", "outer-only-value")
          .build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(outboxMessage, outerHeaders);

      assertThat(result).isNotNull();
      assertThat(result.getHeaders()).containsEntry("outer-only-header", "outer-only-value");
    }

    @Test
    void when_conflicting_header_in_outer_and_outbox_expect_outbox_message_header_takes_precedence() {
      final Map<String, Object> outboxHeaders = Map.of(
          "shared-header", "outbox-value",
          MessageHeaders.CONTENT_TYPE, MimeType.valueOf("application/json"));
      final OutboxMessage outboxMessage = OutboxMessageConverterTest.this.buildOutboxMessage(new byte[]{1}, outboxHeaders);
      final MessageHeaders outerHeaders = MessageBuilder.withPayload(new byte[0])
          .setHeader("shared-header", "outer-value")
          .build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(outboxMessage, outerHeaders);

      assertThat(result).isNotNull();
      assertThat(result.getHeaders()).containsEntry("shared-header", "outbox-value");
    }

    @Test
    void when_payload_is_not_outbox_message_expect_null_returned() {
      final MessageHeaders headers = MessageBuilder.withPayload("not an OutboxMessage").build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage("not an OutboxMessage", headers);

      assertThat(result).isNull();
    }

    @Test
    void when_outbox_message_payload_is_not_byte_array_expect_null_returned() {
      final OutboxMessage outboxMessage = OutboxMessageConverterTest.this.buildOutboxMessage("string payload",
          Map.of(MessageHeaders.CONTENT_TYPE, MimeType.valueOf("application/json")));
      final MessageHeaders headers = MessageBuilder.withPayload(outboxMessage).build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(outboxMessage, headers);

      assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("NullableProblems")
    void when_null_payload_expect_null_returned() {
      final MessageHeaders headers = MessageBuilder.withPayload("dummy").build().getHeaders();

      final Message<?> result = OutboxMessageConverterTest.this.converter.toMessage(null, headers);

      assertThat(result).isNull();
    }
  }

  @Nested
  class FromMessage {

    @Test
    void when_from_message_called_expect_null_returned() {
      final Message<byte[]> message = MessageBuilder.withPayload(new byte[]{1, 2, 3}).build();

      final Object result = OutboxMessageConverterTest.this.converter.fromMessage(message, byte[].class);

      assertThat(result).isNull();
    }
  }

  private OutboxMessage buildOutboxMessage(final Object payload, final Map<String, Object> headers) {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.parse("2026-03-23T12:00:00Z"))
        .destination("test-destination")
        .bindingName("test-binding")
        .payload(payload)
        .headers(headers)
        .build();
  }
}
