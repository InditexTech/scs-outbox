package dev.inditex.scsoutbox.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class OutboxPublishingTaskReportTest {

  @Test
  void stop_must_be_after_start() {
    final Instant start = Instant.now();
    final Instant stop = start.minus(Duration.ofMillis(100));
    assertThatThrownBy(() -> OutboxPublishingTaskReport.of(start, stop, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stop must be after start");
  }

  @Test
  void duration_is_the_time_between_start_and_stop_report() {
    final Duration expectedDuration = Duration.ofMillis(100);
    final Instant start = Instant.now();
    final Instant stop = start.plus(expectedDuration);
    final OutboxPublishingTaskReport report = OutboxPublishingTaskReport.of(start, stop, 0);

    assertThat(report.getDuration()).isEqualTo(expectedDuration);
  }

  @Test
  void throughput_is_the_number_of_messages_by_second() {
    final Instant start = Instant.now();
    final Instant stop = start.plus(Duration.ofSeconds(1));
    final OutboxPublishingTaskReport report = OutboxPublishingTaskReport.of(start, stop, 100);

    assertThat(report.getThroughput()).isEqualTo(100);
  }
}
