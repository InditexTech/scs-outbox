package dev.inditex.scsoutbox.metrics;

import java.time.Duration;
import java.time.Instant;

import dev.inditex.scsoutbox.OutboxMessage;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class PublishingDelayMeter {

  private static final String PUBLISHING_DELAY_METRIC = "outbox.publishing.delay";

  private final Timer timer;

  public PublishingDelayMeter(final MeterRegistry meterRegistry) {
    this.timer = meterRegistry.timer(PUBLISHING_DELAY_METRIC);
  }

  @After(
      value = "execution(* dev.inditex.scsoutbox.publish.OutboxMessagePublisher.publish(..)) && args(message)",
      argNames = "message")
  public void publishingDelay(final OutboxMessage message) {
    final Duration publishingDelay = Duration.between(message.getCapturedAt(), Instant.now());
    this.timer.record(publishingDelay);
  }

}
