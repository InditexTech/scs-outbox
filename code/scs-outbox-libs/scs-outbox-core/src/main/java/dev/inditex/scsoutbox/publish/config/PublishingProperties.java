package dev.inditex.scsoutbox.publish.config;

import java.util.Objects;
import java.util.Set;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Getter
@ConfigurationProperties("scs-outbox.publishing")
@RefreshScope
public class PublishingProperties {

  public static final int DEFAULT_BATCH_SIZE = 1000;

  private final int batchSize;

  private final GroupingMode groupingStrategy;

  private final Set<String> pausedDestinations;

  private final boolean paused;

  private final boolean afterCommit;

  public enum GroupingMode {
    DESTINATION,
    KAFKA_MESSAGE_KEY,
    CUSTOM_GROUPING_KEY
  }

  @ConstructorBinding
  public PublishingProperties(final Integer batchSize, final String groupingStrategy,
      final Set<String> pausedDestinations, final Boolean paused, final Boolean afterCommit) {
    if (batchSize != null && batchSize <= 0) {
      throw new IllegalArgumentException("Batch size must be greater than 0");
    }
    this.batchSize = Objects.requireNonNullElse(batchSize, DEFAULT_BATCH_SIZE);
    this.groupingStrategy = groupingStrategy != null ? GroupingMode.valueOf(groupingStrategy) : GroupingMode.DESTINATION;
    this.pausedDestinations = Objects.requireNonNullElse(pausedDestinations, Set.of());
    this.paused = Objects.requireNonNullElse(paused, false);
    this.afterCommit = Objects.requireNonNullElse(afterCommit, false);
  }
}
