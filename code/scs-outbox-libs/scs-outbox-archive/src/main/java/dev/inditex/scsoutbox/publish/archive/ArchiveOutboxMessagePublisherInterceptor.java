package dev.inditex.scsoutbox.publish.archive;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.OutboxMessagePublisherInterceptor;

import lombok.RequiredArgsConstructor;

/**
 * Archives a message after it has been published, as a best-effort side effect (see {@link OutboxMessagePublisherInterceptor}). If
 * archiving fails for a given message, that message simply won't have an archive record; publishing is not affected.
 */
@RequiredArgsConstructor
public class ArchiveOutboxMessagePublisherInterceptor implements OutboxMessagePublisherInterceptor {

  private final ArchiveService archiveService;

  @Override
  public void postSend(final OutboxMessage outboxMessage) {
    this.archiveService.archive(outboxMessage);
  }
}
