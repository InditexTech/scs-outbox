package dev.inditex.scsoutbox;

import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class MessageCaptureTxServiceTest {

  private MessageCaptureTxService messageCaptureTxService;

  private InMemoryOutboxMessageRepository repository;

  private OutboxServiceProperties outboxServiceProperties;

  private final String destination = "topicName";

  private final String bindingName = "bindingName";

  @BeforeEach
  void setUp() {
    this.repository = new InMemoryOutboxMessageRepository();
    this.outboxServiceProperties = mock(OutboxServiceProperties.class);
    when(this.outboxServiceProperties.getDestination(this.bindingName)).thenReturn(this.destination);
    this.messageCaptureTxService = new MessageCaptureTxService(
        this.repository, this.outboxServiceProperties);
  }

  @Test
  void when_message_is_captured_then_message_is_saved_in_repository() {
    final Message<Object> capturedMessage = MessageBuilder.withPayload(new Object()).build();

    this.messageCaptureTxService.capture(this.bindingName, capturedMessage);

    final OutboxMessage outboxMessage = this.repository.findAllOrderByCapturedAt(UNLIMITED).stream().findFirst().get();
    assertThat(outboxMessage.getPayload()).isEqualTo(capturedMessage.getPayload());
    assertThat(outboxMessage.getHeaders()).isEqualTo(capturedMessage.getHeaders());
    assertThat(outboxMessage.getDestination()).isEqualTo(this.destination);
    assertThat(outboxMessage.getBindingName()).isEqualTo(this.bindingName);
  }

  @Test
  void when_native_encoding_is_enabled_then_original_payload_is_saved() {
    when(this.outboxServiceProperties.useNativeEncoding(this.bindingName)).thenReturn(true);
    final Message<Object> message = MessageBuilder.withPayload(new Object()).build();

    this.messageCaptureTxService.capture(this.bindingName, message);

    final OutboxMessage outboxMessage = this.repository.findAllOrderByCapturedAt(UNLIMITED).stream().findFirst().get();
    assertThat(outboxMessage.getPayload()).isEqualTo(message.getPayload());
    assertThat(outboxMessage.getHeaders()).isEqualTo(message.getHeaders());
    assertThat(outboxMessage.getDestination()).isEqualTo(this.destination);
    assertThat(outboxMessage.getBindingName()).isEqualTo(this.bindingName);
  }

  @Test
  void when_scs_encoding_is_enabled_then_original_payload_is_saved() {
    final Message<Object> message = MessageBuilder.withPayload(new Object()).build();

    this.messageCaptureTxService.capture(this.bindingName, message);

    final OutboxMessage outboxMessage = this.repository.findAllOrderByCapturedAt(UNLIMITED).stream().findFirst().get();
    assertThat(outboxMessage.getPayload()).isEqualTo(message.getPayload());
    assertThat(outboxMessage.getHeaders()).isEqualTo(message.getHeaders());
    assertThat(outboxMessage.getDestination()).isEqualTo(this.destination);
    assertThat(outboxMessage.getBindingName()).isEqualTo(this.bindingName);
  }
}
