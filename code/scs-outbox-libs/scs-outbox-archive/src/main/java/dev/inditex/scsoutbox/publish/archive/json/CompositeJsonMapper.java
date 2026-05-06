package dev.inditex.scsoutbox.publish.archive.json;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompositeJsonMapper implements JsonMapper<Object> {

  private final JsonMapper<Object> defaultJsonMapper;

  private final List<JsonMapper<?>> mappers;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public String writeValueAsString(final Object value) {
    final JsonMapper mapper = this.mappers.stream()
        .filter(jsonMapper -> jsonMapper.getValueType().isInstance(value))
        .findFirst()
        .orElse(this.defaultJsonMapper);
    return mapper.writeValueAsString(mapper.getValueType().cast(value));
  }
}
