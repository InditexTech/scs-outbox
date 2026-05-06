package dev.inditex.scsoutbox.publish.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import dev.inditex.scsoutbox.publish.config.PublishingProperties.GroupingMode;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublishingPropertiesTest {

  @Nested
  class Constructor {

    @Test
    void when_all_params_provided_expect_correct_values() {
      final var properties = new PublishingProperties(2, "KAFKA_MESSAGE_KEY", Set.of("destination1", "destination2"), true, true);

      assertThat(properties.getBatchSize()).isEqualTo(2);
      assertThat(properties.getGroupingStrategy()).isEqualTo(GroupingMode.KAFKA_MESSAGE_KEY);
      assertThat(properties.getPausedDestinations()).containsExactlyInAnyOrder("destination1", "destination2");
      assertThat(properties.isPaused()).isTrue();
      assertThat(properties.isAfterCommit()).isTrue();
    }

    @Test
    void when_null_params_expect_default_values() {
      final var properties = new PublishingProperties(null, null, null, null, null);

      assertThat(properties.getBatchSize()).isEqualTo(PublishingProperties.DEFAULT_BATCH_SIZE);
      assertThat(properties.getGroupingStrategy()).isEqualTo(GroupingMode.DESTINATION);
      assertThat(properties.getPausedDestinations()).isEmpty();
      assertThat(properties.isPaused()).isFalse();
      assertThat(properties.isAfterCommit()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void when_invalid_batch_size_expect_exception(final int batchSize) {
      final ThrowingCallable result = () -> new PublishingProperties(batchSize, null, null, null, null);

      assertThatThrownBy(result).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_invalid_grouping_strategy_expect_exception() {
      final ThrowingCallable result = () -> new PublishingProperties(null, "INVALID", null, null, null);

      assertThatThrownBy(result).isInstanceOf(IllegalArgumentException.class);
    }
  }

}
