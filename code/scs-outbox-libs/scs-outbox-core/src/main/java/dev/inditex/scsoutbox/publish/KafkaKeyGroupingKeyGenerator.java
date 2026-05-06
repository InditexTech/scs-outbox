package dev.inditex.scsoutbox.publish;

import org.jspecify.annotations.NullMarked;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * Generates a grouping key for Kafka messages by combining the destination and the Kafka message key. This implementation of
 * {@link GroupingKeyGenerator} is useful for partitioning or routing messages in Kafka based on both the destination and the message key,
 * ensuring consistent grouping.
 */
@NullMarked
public class KafkaKeyGroupingKeyGenerator implements GroupingKeyGenerator {

  @Override
  public GroupingKey generate(GroupingValues values) {
    return GroupingKey.of(
        values.getDestination() + "-" + values.getMessageHeaders().get(KafkaHeaders.KEY));
  }

}
