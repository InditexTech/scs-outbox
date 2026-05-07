package dev.inditex.scsoutbox.publish.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.archive.config.ArchiveProperties;
import dev.inditex.scsoutbox.publish.archive.json.JsonMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageReconverter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.messaging.MessageHeaders;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {

  private ArchiveService archiveService;

  @Mock
  private ArchivedMessageRepository repository;

  @Mock
  private BindingServiceProperties bindingServiceProperties;

  @Mock
  private BindingProperties defaultBindingProperties;

  @Mock
  private JsonMapper jsonMapper;

  @Mock
  private ArchiveProperties properties;

  @Mock
  private OutboxMessageReconverter reconverter;

  @Captor
  private ArgumentCaptor<ArchivedMessage> archivedMessageCaptor;

  @BeforeEach
  void beforeEach() {
    when(this.defaultBindingProperties.getContentType()).thenReturn("application/x-binding-content-type");
    when(this.bindingServiceProperties.getBindingProperties(any())).thenReturn(this.defaultBindingProperties);
    this.archiveService = new ArchiveService(
        this.repository, this.bindingServiceProperties, this.jsonMapper, this.properties, this.reconverter);
  }

  @Nested
  class Archive {

    @Test
    void when_message_is_archived_expect_all_fields_are_mapped() {
      final Instant capturedAt = Instant.parse("2026-01-15T10:00:00Z");
      final UUID id = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
      final Object payload = "test-payload";
      final Map<String, Object> headers = Map.of(MessageHeaders.CONTENT_TYPE, "application/json");
      final OutboxMessage outboxMessage = OutboxMessage.builder()
          .id(id)
          .capturedAt(capturedAt)
          .destination("my-destination")
          .bindingName("my-binding")
          .payload(payload)
          .headers(headers)
          .build();

      ArchiveServiceTest.this.archiveService.archive(outboxMessage);

      verify(ArchiveServiceTest.this.repository).save(ArchiveServiceTest.this.archivedMessageCaptor.capture());
      final ArchivedMessage saved = ArchiveServiceTest.this.archivedMessageCaptor.getValue();
      assertThat(saved.getId()).isEqualTo(id);
      assertThat(saved.getCapturedAt()).isEqualTo(capturedAt);
      assertThat(saved.getDestination()).isEqualTo("my-destination");
      assertThat(saved.getPayload()).isEqualTo(payload);
      assertThat(saved.getHeaders()).isEqualTo(headers);
      assertThat(saved.getArchivedAt()).isNotNull();
      assertThat(saved.getJsonPayload()).isNull();
    }

    @Test
    void when_content_type_in_headers_expect_header_content_type_used() {
      final OutboxMessage outboxMessage = anOutboxMessageBuilder()
          .headers(Map.of(MessageHeaders.CONTENT_TYPE, "text/plain"))
          .build();

      ArchiveServiceTest.this.archiveService.archive(outboxMessage);

      verify(ArchiveServiceTest.this.repository).save(ArchiveServiceTest.this.archivedMessageCaptor.capture());
      assertThat(ArchiveServiceTest.this.archivedMessageCaptor.getValue().getContentType()).isEqualTo("text/plain");
    }

    @Test
    void when_no_content_type_in_headers_expect_binding_content_type_used() {
      final OutboxMessage outboxMessage = anOutboxMessageBuilder().headers(Map.of()).build();

      ArchiveServiceTest.this.archiveService.archive(outboxMessage);

      verify(ArchiveServiceTest.this.repository).save(ArchiveServiceTest.this.archivedMessageCaptor.capture());
      assertThat(ArchiveServiceTest.this.archivedMessageCaptor.getValue().getContentType())
          .isEqualTo("application/x-binding-content-type");
    }

    @Test
    void when_json_payload_disabled_expect_null_json_payload() {
      final OutboxMessage outboxMessage = anOutboxMessageBuilder()
          .headers(Map.of(MessageHeaders.CONTENT_TYPE, "application/json"))
          .build();

      ArchiveServiceTest.this.archiveService.archive(outboxMessage);

      verify(ArchiveServiceTest.this.repository).save(ArchiveServiceTest.this.archivedMessageCaptor.capture());
      assertThat(ArchiveServiceTest.this.archivedMessageCaptor.getValue().getJsonPayload()).isNull();
      verify(ArchiveServiceTest.this.reconverter, never()).reconvertPayload(any());
      verify(ArchiveServiceTest.this.jsonMapper, never()).writeValueAsString(any());
    }

    @Test
    void when_json_payload_enabled_and_object_payload_expect_json_payload_generated() {
      when(ArchiveServiceTest.this.properties.isJsonPayloadEnabled()).thenReturn(true);
      when(ArchiveServiceTest.this.jsonMapper.writeValueAsString("payload")).thenReturn("{\"value\":\"payload\"}");
      final OutboxMessage outboxMessage = anOutboxMessageBuilder()
          .headers(Map.of(MessageHeaders.CONTENT_TYPE, "application/json"))
          .build();

      ArchiveServiceTest.this.archiveService.archive(outboxMessage);

      verify(ArchiveServiceTest.this.repository).save(ArchiveServiceTest.this.archivedMessageCaptor.capture());
      assertThat(ArchiveServiceTest.this.archivedMessageCaptor.getValue().getJsonPayload()).isEqualTo("{\"value\":\"payload\"}");
      verify(ArchiveServiceTest.this.reconverter, never()).reconvertPayload(any());
    }

    @Test
    void when_json_payload_enabled_and_bytes_payload_expect_reconverter_invoked_and_result_serialized() {
      final byte[] bytes = new byte[]{1, 2, 3};
      final String reconverted = "reconverted-object";
      when(ArchiveServiceTest.this.properties.isJsonPayloadEnabled()).thenReturn(true);
      when(ArchiveServiceTest.this.reconverter.reconvertPayload(any())).thenReturn(reconverted);
      when(ArchiveServiceTest.this.jsonMapper.writeValueAsString(reconverted)).thenReturn("{\"reconverted\":true}");
      final OutboxMessage outboxMessage = anOutboxMessageBuilder()
          .payload(bytes)
          .headers(Map.of(MessageHeaders.CONTENT_TYPE, "application/json"))
          .build();

      ArchiveServiceTest.this.archiveService.archive(outboxMessage);

      verify(ArchiveServiceTest.this.reconverter).reconvertPayload(outboxMessage);
      verify(ArchiveServiceTest.this.repository).save(ArchiveServiceTest.this.archivedMessageCaptor.capture());
      assertThat(ArchiveServiceTest.this.archivedMessageCaptor.getValue().getJsonPayload()).isEqualTo("{\"reconverted\":true}");
    }

    @Test
    void when_json_payload_enabled_and_mapper_throws_expect_null_json_payload() {
      when(ArchiveServiceTest.this.properties.isJsonPayloadEnabled()).thenReturn(true);
      when(ArchiveServiceTest.this.jsonMapper.writeValueAsString(any())).thenThrow(new RuntimeException("mapping error"));
      final OutboxMessage outboxMessage = anOutboxMessageBuilder()
          .headers(Map.of(MessageHeaders.CONTENT_TYPE, "application/json"))
          .build();

      ArchiveServiceTest.this.archiveService.archive(outboxMessage);

      verify(ArchiveServiceTest.this.repository).save(ArchiveServiceTest.this.archivedMessageCaptor.capture());
      assertThat(ArchiveServiceTest.this.archivedMessageCaptor.getValue().getJsonPayload()).isNull();
    }
  }

  private static OutboxMessage.OutboxMessageBuilder anOutboxMessageBuilder() {
    return OutboxMessage.builder()
        .id(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        .capturedAt(Instant.parse("2026-01-15T10:00:00Z"))
        .destination("destination")
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of());
  }

}
