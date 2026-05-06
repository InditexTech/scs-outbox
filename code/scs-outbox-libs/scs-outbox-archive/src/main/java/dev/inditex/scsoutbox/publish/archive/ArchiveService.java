package dev.inditex.scsoutbox.publish.archive;

import java.time.Instant;
import java.util.Objects;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.archive.config.ArchiveProperties;
import dev.inditex.scsoutbox.publish.archive.json.JsonMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageReconverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.messaging.MessageHeaders;

@Slf4j
@RequiredArgsConstructor
public class ArchiveService {

  private final ArchivedMessageRepository repository;

  private final BindingServiceProperties bindingServiceProperties;

  @SuppressWarnings("rawtypes")
  private final JsonMapper jsonMapper;

  private final ArchiveProperties properties;

  private final OutboxMessageReconverter reconverter;

  public void archive(final OutboxMessage outboxMessage) {
    final ArchivedMessage archivedMessage = ArchivedMessage.builder()
        .id(outboxMessage.getId())
        .archivedAt(Instant.now())
        .capturedAt(outboxMessage.getCapturedAt())
        .destination(outboxMessage.getDestination())
        .contentType(this.resolveContentType(outboxMessage))
        .headers(outboxMessage.getHeaders())
        .payload(outboxMessage.getPayload())
        .jsonPayload(this.generateJsonPayload(outboxMessage))
        .build();
    this.repository.save(archivedMessage);
  }

  private String resolveContentType(OutboxMessage outboxMessage) {
    return Objects.requireNonNullElse(
        outboxMessage.getHeaders().get(MessageHeaders.CONTENT_TYPE),
        this.bindingServiceProperties.getBindingProperties(outboxMessage.getBindingName()).getContentType()).toString();
  }

  @SuppressWarnings("unchecked")
  private String generateJsonPayload(final OutboxMessage outboxMessage) {
    String jsonPayload = null;
    Object payload = outboxMessage.getPayload();
    if (this.properties.isJsonPayloadEnabled()) {
      if (payload instanceof byte[]) {
        // need reconvert payload because payload has been passed by converter chain
        payload = this.reconverter.reconvertPayload(outboxMessage);
      }
      try {
        jsonPayload = this.jsonMapper.writeValueAsString(payload);
      } catch (final Exception e) {
        log.warn("Unexpected error mapping payload to json format", e);
      }
    }
    return jsonPayload;
  }
}
