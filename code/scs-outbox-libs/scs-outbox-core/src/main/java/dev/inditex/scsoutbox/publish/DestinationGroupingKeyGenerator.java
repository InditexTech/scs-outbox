package dev.inditex.scsoutbox.publish;

import org.jspecify.annotations.NullMarked;

/**
 * Generates a grouping key based on the destination value from the provided {@link GroupingValues}. This implementation of
 * {@link GroupingKeyGenerator} uses the destination as the grouping key, which can be useful for partitioning or routing messages by their
 * destination.
 */
@NullMarked
public class DestinationGroupingKeyGenerator implements GroupingKeyGenerator {

  @Override
  public GroupingKey generate(GroupingValues values) {
    return GroupingKey.of(values.getDestination());
  }
}
