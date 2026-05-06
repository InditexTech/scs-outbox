package dev.inditex.scsoutbox.publish.archive.json;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class DefaultJsonMapper implements JsonMapper<Object> {

  private final ObjectMapper mapper;

  public String writeValueAsString(final Object value) {
    return this.mapper.writeValueAsString(value);
  }

}
