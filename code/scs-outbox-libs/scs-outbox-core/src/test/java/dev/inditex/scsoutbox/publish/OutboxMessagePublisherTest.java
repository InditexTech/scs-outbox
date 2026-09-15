package dev.inditex.scsoutbox.publish;

import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessage;
import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import dev.inditex.scsoutbox.InMemoryOutboxMessageRepository;
import dev.inditex.scsoutbox.OutboxMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboxMessagePublisherTest {

  private InMemoryOutboxMessageRepository repository;

  private OutboxMessageSender messageSender;

  private OutboxMessagePublisher publisher;

  @BeforeEach
  void setUp() {
    this.messageSender = mock(OutboxMessageSender.class);
    when(this.messageSender.send(any(OutboxMessage.class))).thenReturn(true);
    this.repository = new InMemoryOutboxMessageRepository();
    this.publisher = new OutboxMessagePublisher(
        this.messageSender, this.repository, List.of());
  }

  @Nested
  class Publish {

    @Test
    void when_message_sender_cannot_send_message_expect_error() {
      when(OutboxMessagePublisherTest.this.messageSender.send(any(OutboxMessage.class))).thenReturn(false);

      assertThatThrownBy(() -> OutboxMessagePublisherTest.this.publisher.publish(anOutboxMessage()))
          .isInstanceOf(MessageNotPublishedException.class);
    }

    @Test
    void when_message_is_sent_expect_deleted_from_repository() {
      final OutboxMessage message = anOutboxMessage();
      OutboxMessagePublisherTest.this.repository.save(message);

      OutboxMessagePublisherTest.this.publisher.publish(message);

      assertThat(OutboxMessagePublisherTest.this.repository.findAllOrderByCapturedAt(UNLIMITED)).isEmpty();
    }

    @Test
    void when_postsend_interceptor_throws_expect_message_still_deleted_and_exception_not_propagated() {
      final OutboxMessagePublisherInterceptor failingInterceptor = mock(OutboxMessagePublisherInterceptor.class);
      doThrow(new RuntimeException("archiving failed")).when(failingInterceptor).postSend(any(OutboxMessage.class));
      final OutboxMessagePublisher publisherWithFailingInterceptor = new OutboxMessagePublisher(
          OutboxMessagePublisherTest.this.messageSender, OutboxMessagePublisherTest.this.repository, List.of(failingInterceptor));
      final OutboxMessage message = anOutboxMessage();
      OutboxMessagePublisherTest.this.repository.save(message);

      assertThatCode(() -> publisherWithFailingInterceptor.publish(message)).doesNotThrowAnyException();

      assertThat(OutboxMessagePublisherTest.this.repository.findAllOrderByCapturedAt(UNLIMITED)).isEmpty();
    }

    @Test
    void when_first_postsend_interceptor_throws_expect_second_interceptor_still_invoked() {
      final OutboxMessagePublisherInterceptor failingInterceptor = mock(OutboxMessagePublisherInterceptor.class);
      doThrow(new RuntimeException("archiving failed")).when(failingInterceptor).postSend(any(OutboxMessage.class));
      final OutboxMessagePublisherInterceptor secondInterceptor = mock(OutboxMessagePublisherInterceptor.class);
      final OutboxMessagePublisher publisherWithTwoInterceptors = new OutboxMessagePublisher(
          OutboxMessagePublisherTest.this.messageSender, OutboxMessagePublisherTest.this.repository,
          List.of(failingInterceptor, secondInterceptor));
      final OutboxMessage message = anOutboxMessage();
      OutboxMessagePublisherTest.this.repository.save(message);

      publisherWithTwoInterceptors.publish(message);

      verify(secondInterceptor).postSend(message);
    }
  }

}
