package dev.inditex.scsoutbox.metrics;

import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.OutboxMessagePublisher;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class PublishingDelayMeterTest {

  private SimpleMeterRegistry meterRegistry;

  private OutboxMessagePublisher publisher;

  @BeforeEach
  void setUp() {
    this.meterRegistry = new SimpleMeterRegistry();
    final OutboxMessagePublisher mock = mock(OutboxMessagePublisher.class);
    final AspectJProxyFactory factory = new AspectJProxyFactory(mock);
    factory.addAspect(new PublishingDelayMeter(this.meterRegistry));
    this.publisher = factory.getProxy();
  }

  @Test
  void update_metric_when_a_message_is_published() {
    assertThat(this.meterRegistry.get("outbox.publishing.delay").timer().count())
        .isZero();

    this.publisher.publish(anOutboxMessage());

    assertThat(this.meterRegistry.get("outbox.publishing.delay").timer().count())
        .isPositive();
  }

  @Test
  void publishing_delay_is_the_millis_between_captured_date_and_the_time_the_message_was_published() {
    final OutboxMessage message = anOutboxMessage();
    this.publisher.publish(message);
    assertThat(
        this.meterRegistry.get("outbox.publishing.delay").timer().totalTime(TimeUnit.NANOSECONDS))
            .isLessThanOrEqualTo(
                Duration.between(message.getCapturedAt(), Instant.now()).toNanos());
  }

}
