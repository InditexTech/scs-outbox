package dev.inditex.scsoutbox.publish;

/**
 * Strategy interface for generating a grouping key based on provided grouping values. Implementations of this interface define how to
 * create a {@link GroupingKey} from the given {@link GroupingValues}, which can be used for partitioning, routing, or grouping messages in
 * messaging systems.
 *
 * <p>See also: <ul> <li>{@link DestinationGroupingKeyGenerator}</li> <li>{@link KafkaKeyGroupingKeyGenerator}</li> </ul>
 */
public interface GroupingKeyGenerator {

  GroupingKey generate(GroupingValues values);

}
