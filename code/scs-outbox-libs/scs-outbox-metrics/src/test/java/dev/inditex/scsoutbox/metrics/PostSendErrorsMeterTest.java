package dev.inditex.scsoutbox.metrics;

import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessage;
import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.OutboxMessagePublisherInterceptor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class PostSendErrorsMeterTest {

  private static final String POSTSEND_ERRORS_METRIC = "outbox.publishing.postsend.errors";

  private static final String INTERCEPTOR_TAG = "interceptor";

  private static final String DESTINATION_TAG = "destination";

  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    this.meterRegistry = new SimpleMeterRegistry();
  }

  private OutboxMessagePublisherInterceptor proxiedInterceptor(final OutboxMessagePublisherInterceptor target) {
    final AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.addAspect(new PostSendErrorsMeter(this.meterRegistry));
    return factory.getProxy();
  }

  @Test
  void when_postsend_succeeds_expect_no_error_counted() {
    final OutboxMessagePublisherInterceptor interceptor = this.proxiedInterceptor(new SucceedingInterceptor());

    interceptor.postSend(anOutboxMessage());

    assertThat(this.meterRegistry.find(POSTSEND_ERRORS_METRIC).counter()).isNull();
  }

  @Test
  void when_postsend_throws_expect_error_counted_once() {
    final OutboxMessagePublisherInterceptor interceptor = this.proxiedInterceptor(new FailingInterceptor());
    final OutboxMessage message = anOutboxMessage();

    assertThatThrownBy(() -> interceptor.postSend(message)).isInstanceOf(RuntimeException.class);

    assertThat(
        this.meterRegistry.get(POSTSEND_ERRORS_METRIC)
            .tag(INTERCEPTOR_TAG, FailingInterceptor.class.getName())
            .tag(DESTINATION_TAG, message.getDestination())
            .counter()
            .count())
                .isEqualTo(1);
  }

  @Test
  void when_postsend_throws_expect_exception_still_propagated() {
    final OutboxMessagePublisherInterceptor interceptor = this.proxiedInterceptor(new FailingInterceptor());

    assertThatThrownBy(() -> interceptor.postSend(anOutboxMessage()))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("archiving failed");
  }

  @Test
  void when_different_interceptors_fail_for_same_destination_expect_separate_series() {
    final OutboxMessagePublisherInterceptor first = this.proxiedInterceptor(new FailingInterceptor());
    final OutboxMessagePublisherInterceptor second = this.proxiedInterceptor(new AnotherFailingInterceptor());
    final OutboxMessage message = anOutboxMessage();

    assertThatThrownBy(() -> first.postSend(message)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> second.postSend(message)).isInstanceOf(RuntimeException.class);

    assertThat(
        this.meterRegistry.get(POSTSEND_ERRORS_METRIC)
            .tag(INTERCEPTOR_TAG, FailingInterceptor.class.getName())
            .tag(DESTINATION_TAG, message.getDestination())
            .counter()
            .count())
                .isEqualTo(1);
    assertThat(
        this.meterRegistry.get(POSTSEND_ERRORS_METRIC)
            .tag(INTERCEPTOR_TAG, AnotherFailingInterceptor.class.getName())
            .tag(DESTINATION_TAG, message.getDestination())
            .counter()
            .count())
                .isEqualTo(1);
  }

  @Test
  void when_same_interceptor_fails_for_different_destinations_expect_separate_series() {
    final OutboxMessagePublisherInterceptor interceptor = this.proxiedInterceptor(new FailingInterceptor());
    final OutboxMessage firstMessage = anOutboxMessage();
    final OutboxMessage secondMessage = anOutboxMessageBuilder().destination("other-destination").build();

    assertThatThrownBy(() -> interceptor.postSend(firstMessage)).isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> interceptor.postSend(secondMessage)).isInstanceOf(RuntimeException.class);

    assertThat(
        this.meterRegistry.get(POSTSEND_ERRORS_METRIC)
            .tag(INTERCEPTOR_TAG, FailingInterceptor.class.getName())
            .tag(DESTINATION_TAG, firstMessage.getDestination())
            .counter()
            .count())
                .isEqualTo(1);
    assertThat(
        this.meterRegistry.get(POSTSEND_ERRORS_METRIC)
            .tag(INTERCEPTOR_TAG, FailingInterceptor.class.getName())
            .tag(DESTINATION_TAG, secondMessage.getDestination())
            .counter()
            .count())
                .isEqualTo(1);
  }

  private static class SucceedingInterceptor implements OutboxMessagePublisherInterceptor {

    @Override
    public void postSend(final OutboxMessage outboxMessage) {
      // no-op: simulates a successful post-send side effect
    }

  }

  private static class FailingInterceptor implements OutboxMessagePublisherInterceptor {

    @Override
    public void postSend(final OutboxMessage outboxMessage) {
      throw new RuntimeException("archiving failed");
    }

  }

  private static class AnotherFailingInterceptor implements OutboxMessagePublisherInterceptor {

    @Override
    public void postSend(final OutboxMessage outboxMessage) {
      throw new RuntimeException("archiving failed too");
    }

  }

}
