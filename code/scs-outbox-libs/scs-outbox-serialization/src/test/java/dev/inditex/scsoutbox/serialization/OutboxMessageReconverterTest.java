package dev.inditex.scsoutbox.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.OutboxServiceProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.CompositeMessageConverter;

@ExtendWith(MockitoExtension.class)
class OutboxMessageReconverterTest {

  private OutboxMessageReconverter reconverter;

  @Mock
  private OutboxServiceProperties outboxServiceProperties;

  @Mock
  private CompositeMessageConverter compositeMessageConverter;

  @Captor
  private ArgumentCaptor<Message<?>> messageCaptor;

  @BeforeEach
  void beforeEach() {
    this.reconverter = new OutboxMessageReconverter(
        this.outboxServiceProperties, this.compositeMessageConverter);
  }

  @Nested
  class ReconvertPayload {

    @Test
    void when_native_encoding_is_enabled_expect_original_payload_returned_without_converter_call() {
      final Object payload = "original-payload";
      final OutboxMessage outboxMessage = buildOutboxMessage(payload, Map.of());
      doReturn(true).when(OutboxMessageReconverterTest.this.outboxServiceProperties)
          .useNativeEncoding("test-binding");

      final Object result = OutboxMessageReconverterTest.this.reconverter.reconvertPayload(outboxMessage);

      assertThat(result).isSameAs(payload);
      verifyNoInteractions(OutboxMessageReconverterTest.this.compositeMessageConverter);
    }

    @Test
    void when_native_encoding_is_disabled_expect_converted_payload_returned() {
      final Object convertedPayload = "converted-payload";
      final OutboxMessage outboxMessage = buildOutboxMessage("original-payload", Map.of());
      doReturn(false).when(OutboxMessageReconverterTest.this.outboxServiceProperties)
          .useNativeEncoding("test-binding");
      doReturn(convertedPayload).when(OutboxMessageReconverterTest.this.compositeMessageConverter)
          .fromMessage(any(), eq(Object.class));

      final Object result = OutboxMessageReconverterTest.this.reconverter.reconvertPayload(outboxMessage);

      assertThat(result).isSameAs(convertedPayload);
    }

    @Test
    void when_native_encoding_is_disabled_expect_converter_called_with_message_built_from_outbox_payload_and_headers() {
      final Object originalPayload = "original-payload";
      final Map<String, Object> headers = Map.of("kafka_messageKey", "my-key");
      final OutboxMessage outboxMessage = buildOutboxMessage(originalPayload, headers);
      doReturn(false).when(OutboxMessageReconverterTest.this.outboxServiceProperties)
          .useNativeEncoding("test-binding");
      doReturn(null).when(OutboxMessageReconverterTest.this.compositeMessageConverter)
          .fromMessage(any(), eq(Object.class));

      OutboxMessageReconverterTest.this.reconverter.reconvertPayload(outboxMessage);

      verify(OutboxMessageReconverterTest.this.compositeMessageConverter)
          .fromMessage(OutboxMessageReconverterTest.this.messageCaptor.capture(), eq(Object.class));
      final Message<?> capturedMessage = OutboxMessageReconverterTest.this.messageCaptor.getValue();
      assertThat(capturedMessage.getPayload()).isEqualTo(originalPayload);
      assertThat(capturedMessage.getHeaders()).containsEntry("kafka_messageKey", "my-key");
    }

    @Test
    void when_native_encoding_is_disabled_and_converter_returns_null_expect_null_returned() {
      final OutboxMessage outboxMessage = buildOutboxMessage("original-payload", Map.of());
      doReturn(false).when(OutboxMessageReconverterTest.this.outboxServiceProperties)
          .useNativeEncoding("test-binding");
      doReturn(null).when(OutboxMessageReconverterTest.this.compositeMessageConverter)
          .fromMessage(any(), eq(Object.class));

      final Object result = OutboxMessageReconverterTest.this.reconverter.reconvertPayload(outboxMessage);

      assertThat(result).isNull();
    }
  }

  private static OutboxMessage buildOutboxMessage(final Object payload, final Map<String, Object> headers) {
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
