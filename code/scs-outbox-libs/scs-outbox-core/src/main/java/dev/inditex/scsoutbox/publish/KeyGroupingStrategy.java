package dev.inditex.scsoutbox.publish;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.inditex.scsoutbox.OutboxMessage;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;

@RequiredArgsConstructor
@NullMarked
public class KeyGroupingStrategy implements GroupingStrategy {

  private final GroupingKeyGenerator groupingKeyGenerator;

  @Override
  public Map<GroupingKey, List<OutboxMessage>> group(List<OutboxMessage> messages) {
    return messages.stream().collect(Collectors.groupingBy(
        message -> this.groupingKeyGenerator.generate(
            GroupingValues.of(
                message.getDestination(),
                message.getBindingName(),
                message.getHeaders()))));
  }
}
