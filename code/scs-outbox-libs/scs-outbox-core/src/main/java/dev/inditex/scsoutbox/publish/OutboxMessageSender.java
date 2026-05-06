package dev.inditex.scsoutbox.publish;

import dev.inditex.scsoutbox.OutboxMessage;

public interface OutboxMessageSender {

  boolean send(final OutboxMessage outboxMessage);
}
