package dev.inditex.scsoutbox.publish;

import dev.inditex.scsoutbox.OutboxMessage;

public interface OutboxMessagePublisherInterceptor {

  void postSend(OutboxMessage outboxMessage);

}
