package dev.inditex.scsoutbox.metrics;

import java.util.concurrent.atomic.AtomicLong;

import dev.inditex.scsoutbox.OutboxMessageRepository;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

@Aspect
@Slf4j
public class MessagesPendingMeter {

  private static final String PENDING_MESSAGES_METRIC = "outbox.messages.pending";

  private final OutboxMessageRepository outboxMessageRepository;

  private final AtomicLong meterValue;

  public MessagesPendingMeter(final MeterRegistry meterRegistry, OutboxMessageRepository outboxMessageRepository) {
    this.meterValue = meterRegistry.gauge(PENDING_MESSAGES_METRIC, new AtomicLong(0));
    this.outboxMessageRepository = outboxMessageRepository;
  }

  @Before("execution(* dev.inditex.scsoutbox.publish.OutboxPublishingTask.run())")
  public void meterPendingMessages() {
    final long numOfMessages = this.outboxMessageRepository.estimatedCount();
    log.debug("There are {} estimated messages in the outbox", numOfMessages);
    this.meterValue.set(numOfMessages);
  }

}
