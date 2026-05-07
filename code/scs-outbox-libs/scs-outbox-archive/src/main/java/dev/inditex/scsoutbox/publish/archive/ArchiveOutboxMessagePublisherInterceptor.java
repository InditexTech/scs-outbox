package dev.inditex.scsoutbox.publish.archive;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.OutboxMessagePublisherInterceptor;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ArchiveOutboxMessagePublisherInterceptor implements OutboxMessagePublisherInterceptor {

  private final ArchiveService archiveService;

  @Override
  public void postSend(final OutboxMessage outboxMessage) {
    this.archiveService.archive(outboxMessage);
  }
}
