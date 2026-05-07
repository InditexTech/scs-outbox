package dev.inditex.scsoutbox.publish;

import java.util.List;
import java.util.Map;

import dev.inditex.scsoutbox.OutboxMessage;

public interface GroupingStrategy {
  Map<GroupingKey, List<OutboxMessage>> group(List<OutboxMessage> messages);
}
