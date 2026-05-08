package dev.inditex.scsoutbox.publish.archive;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;

import org.junit.jupiter.api.Test;

class ArchiveOutboxMessagePublisherInterceptorTest {

  @Test
  void delegate_to_archive_service() {
    final ArchiveService service = mock(ArchiveService.class);

    final ArchiveOutboxMessagePublisherInterceptor interceptor =
        new ArchiveOutboxMessagePublisherInterceptor(service);
    final OutboxMessage outboxMessage = OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.now().minus(1, ChronoUnit.MINUTES))
        .bindingName("bindingName")
        .destination("destination")
        .payload("payload")
        .headers(Map.of())
        .build();
    interceptor.postSend(outboxMessage);

    verify(service).archive(outboxMessage);
  }
}
