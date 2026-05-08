package dev.inditex.scsoutbox.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.util.MimeType;

class JsonHeadersMapperTest {

  private final JsonHeadersMapper mapper = new JsonHeadersMapper();

  @Test
  void when_write_with_mime_type_expect_serialized_as_string() {
    final Map<String, Object> headers = new HashMap<>();
    headers.put("contentType", MimeType.valueOf("application/json"));
    headers.put("custom-header", "value");

    final String json = this.mapper.write(headers);

    assertThat(json)
        .contains("\"contentType\":\"application/json\"")
        .contains("\"custom-header\":\"value\"");
  }

  @Test
  void when_write_with_mime_type_with_params_expect_serialized_as_string() {
    final Map<String, Object> headers = new HashMap<>();
    headers.put("contentType", MimeType.valueOf("text/*;charset=UTF-8"));

    final String json = this.mapper.write(headers);

    assertThat(json).contains("\"contentType\":\"text/*;charset=UTF-8\"");
  }

  @Test
  void when_read_with_string_content_type_expect_string_preserved() {
    final String json = "{\"contentType\":\"application/json\",\"key\":\"val\"}";

    final Map<String, Object> result = this.mapper.read(json);

    assertThat(result)
        .containsEntry("contentType", MimeType.valueOf("application/json").toString())
        .containsEntry("key", "val");
  }

  @Test
  void when_read_legacy_map_content_type_expect_rebuilt_as_mime_type() {
    // Simulates legacy format stored in database before MimeTypeStringSerializer
    final String json = "{\"contentType\":{\"type\":\"text\",\"subtype\":\"*\",\"parameters\":{\"charset\":\"UTF-8\"},"
        + "\"wildcardType\":false,\"wildcardSubtype\":true,\"concrete\":false,\"charset\":\"UTF-8\",\"subtypeSuffix\":null}}";

    final Map<String, Object> result = this.mapper.read(json);

    assertThat(result.get("contentType")).isInstanceOf(MimeType.class);
    final MimeType mimeType = (MimeType) result.get("contentType");
    assertThat(mimeType.getType()).isEqualTo("text");
    assertThat(mimeType.getSubtype()).isEqualTo("*");
    assertThat(mimeType.getParameter("charset")).isEqualTo("UTF-8");
  }

  @Test
  void when_read_legacy_map_content_type_without_params_expect_rebuilt_as_mime_type() {
    final String json = "{\"contentType\":{\"type\":\"application\",\"subtype\":\"json\"}}";

    final Map<String, Object> result = this.mapper.read(json);

    assertThat(result.get("contentType")).isInstanceOf(MimeType.class);
    final MimeType mimeType = (MimeType) result.get("contentType");
    assertThat(mimeType).hasToString("application/json");
  }

  @Test
  void when_read_without_content_type_expect_no_error() {
    final String json = "{\"kafka_messageKey\":\"my-key\"}";

    final Map<String, Object> result = this.mapper.read(json);

    assertThat(result)
        .containsEntry("kafka_messageKey", "my-key")
        .doesNotContainKey("contentType");
  }

  @Test
  void when_roundtrip_with_mime_type_expect_string_content_type() {
    final Map<String, Object> original = new HashMap<>();
    original.put("contentType", MimeType.valueOf("application/json"));
    original.put("custom", "value");

    final String json = this.mapper.write(original);
    final Map<String, Object> restored = this.mapper.read(json);

    assertThat(restored)
        .containsEntry("contentType", MimeType.valueOf("application/json").toString())
        .containsEntry("custom", "value");
  }
}
