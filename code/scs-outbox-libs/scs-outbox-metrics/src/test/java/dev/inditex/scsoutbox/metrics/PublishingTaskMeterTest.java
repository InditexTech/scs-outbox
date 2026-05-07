package dev.inditex.scsoutbox.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import dev.inditex.scsoutbox.publish.OutboxPublishingTask;
import dev.inditex.scsoutbox.publish.OutboxPublishingTaskReport;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class PublishingTaskMeterTest {

  private static final String PUBLISHING_MESSAGES_METRIC = "outbox.publishing.messages";

  private SimpleMeterRegistry meterRegistry;

  private OutboxPublishingTask outboxPublishingTask;

  private OutboxPublishingTask publishingTaskMock;

  @BeforeEach
  void setUp() {
    this.meterRegistry = new SimpleMeterRegistry();
    final PublishingTaskMeter publishingTaskMeter = new PublishingTaskMeter(this.meterRegistry);
    this.publishingTaskMock = mock(OutboxPublishingTask.class);
    final Instant start = Instant.now();
    when(this.publishingTaskMock.run()).thenReturn(OutboxPublishingTaskReport.of(
        start, start.plus(Duration.ofSeconds(1)), 100));
    final AspectJProxyFactory factory = new AspectJProxyFactory(this.publishingTaskMock);
    factory.addAspect(publishingTaskMeter);
    this.outboxPublishingTask = factory.getProxy();
  }

  @Test
  void published_messages_value_must_be_equal_than_value_of_report() {
    assertThat(
        this.meterRegistry.get(PUBLISHING_MESSAGES_METRIC).counter().count())
            .isZero();

    final OutboxPublishingTaskReport report = this.outboxPublishingTask.run();

    assertThat(
        this.meterRegistry.get(PUBLISHING_MESSAGES_METRIC).counter().count())
            .isEqualTo(report.getNumOfPublishedMessages());
  }

  @Test
  void on_publish_task_error_then_published_messages_metric_is_not_incremented() {
    when(this.publishingTaskMock.run()).thenThrow(new RuntimeException("Error"));
    assertThat(
        this.meterRegistry.get(PUBLISHING_MESSAGES_METRIC).counter().count())
            .isZero();

    try {
      this.outboxPublishingTask.run();
    } catch (final RuntimeException ignored) {
      // expected
    }

    assertThat(
        this.meterRegistry.get(PUBLISHING_MESSAGES_METRIC).counter().count())
            .isZero();
  }
}
