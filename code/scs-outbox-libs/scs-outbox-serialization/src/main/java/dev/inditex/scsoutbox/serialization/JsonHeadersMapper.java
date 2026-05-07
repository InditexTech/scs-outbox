package dev.inditex.scsoutbox.serialization;

import java.util.Map;

import org.springframework.util.MimeType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

public class JsonHeadersMapper implements HeadersMapper {

  static final String CONTENT_TYPE_HEADER = "contentType";

  private final ObjectMapper mapper;

  public JsonHeadersMapper() {
    final SimpleModule module = new SimpleModule();
    module.addSerializer(MimeType.class, new MimeTypeStringSerializer());
    this.mapper = JsonMapper.builder()
        .addModule(module)
        .build();
  }

  @Override
  public Map<String, Object> read(final String headers) {
    final Map<String, Object> map = this.mapper.readValue(headers, Map.class);
    final Object contentType = map.get(CONTENT_TYPE_HEADER);
    if (contentType instanceof Map<?, ?> ctMap) {
      map.put(CONTENT_TYPE_HEADER, rebuildMimeTypeFromMap(ctMap));
    }
    return map;
  }

  @Override
  public String write(final Map<String, Object> headers) {
    return this.mapper.writeValueAsString(headers);
  }

  /**
   * Rebuilds a {@link MimeType} from a {@link Map} representation. This handles backward compatibility for records stored in the database
   * before the {@link MimeTypeStringSerializer} was introduced — where the {@code contentType} was serialized as a JSON object with
   * {@code type}, {@code subtype}, and {@code parameters} properties instead of a simple string.
   */
  @SuppressWarnings("unchecked")
  static MimeType rebuildMimeTypeFromMap(final Map<?, ?> map) {
    final String type = String.valueOf(map.get("type"));
    final String subtype = String.valueOf(map.get("subtype"));
    final Object params = map.get("parameters");
    if (params instanceof Map<?, ?> paramsMap) {
      return new MimeType(type, subtype, (Map<String, String>) paramsMap);
    }
    return new MimeType(type, subtype);
  }

  /**
   * Jackson serializer that writes {@link MimeType} values as their string representation (e.g., {@code "text/*;charset=UTF-8"}) instead of
   * the default Jackson object serialization which expands all MimeType properties into a JSON object. This produces more compact JSON and
   * avoids deserialization issues where the MimeType comes back as a {@code LinkedHashMap}.
   */
  static class MimeTypeStringSerializer extends ValueSerializer<MimeType> {

    MimeTypeStringSerializer() {
    }

    @Override
    public void serialize(final MimeType value, final JsonGenerator gen, final SerializationContext ctx) {
      gen.writeString(value.toString());
    }
  }
}
