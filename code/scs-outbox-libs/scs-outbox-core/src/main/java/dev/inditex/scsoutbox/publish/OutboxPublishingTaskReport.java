package dev.inditex.scsoutbox.publish;

import java.time.Duration;
import java.time.Instant;

import lombok.Getter;

@Getter
public class OutboxPublishingTaskReport {

  private final Duration duration;

  private final int numOfPublishedMessages;

  private final double throughput;

  private OutboxPublishingTaskReport(Instant start, Instant stop, int numOfPublishedMessages) {
    if (stop.isBefore(start)) {
      throw new IllegalArgumentException(
          "stop must be after start. stop: " + stop + " start: " + start + " thread: " + Thread.currentThread().getName());
    }
    this.duration = Duration.between(start, stop);
    this.numOfPublishedMessages = numOfPublishedMessages;
    final double durationInSeconds = this.getDurationInSeconds();
    this.throughput = numOfPublishedMessages / durationInSeconds;
  }

  public static OutboxPublishingTaskReport of(Instant start, Instant stop, int numOfPublishedMessages) {
    return new OutboxPublishingTaskReport(start, stop, numOfPublishedMessages);
  }

  @Override
  public String toString() {
    return "OutboxPublishingTaskReport"
        + "{ duration(sec)=" + this.getDurationInSeconds()
        + ", numOfPublishedMessages=" + this.numOfPublishedMessages
        + ", throughput(msg/sec)=" + this.throughput
        + "}";
  }

  private double getDurationInSeconds() {
    return this.duration.toNanos() / 1e9;
  }
}
