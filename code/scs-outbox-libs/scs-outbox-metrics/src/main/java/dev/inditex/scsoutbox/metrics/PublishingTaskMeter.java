package dev.inditex.scsoutbox.metrics;

import dev.inditex.scsoutbox.publish.OutboxPublishingTaskReport;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@Slf4j
public class PublishingTaskMeter {

  private static final String PUBLISHING_MESSAGES_METRIC = "outbox.publishing.messages";

  private final Counter publishedMessagesCounter;

  public PublishingTaskMeter(final MeterRegistry meterRegistry) {
    this.publishedMessagesCounter = meterRegistry.counter(PUBLISHING_MESSAGES_METRIC);
  }

  @AfterReturning(
      value = "execution(* dev.inditex.scsoutbox.publish.OutboxPublishingTask.run())",
      returning = "report")
  public void countPublishedMessages(final OutboxPublishingTaskReport report) {
    this.publishedMessagesCounter.increment(report.getNumOfPublishedMessages());
  }

}
