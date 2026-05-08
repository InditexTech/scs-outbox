package dev.inditex.scsoutbox.publish.archive.json;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class DefaultJsonMapperTest {

  private DefaultJsonMapper defaultJsonMapper;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = mock(ObjectMapper.class);
    this.defaultJsonMapper = new DefaultJsonMapper(this.objectMapper);
  }

  @Test
  void delegate_to_json_mapper() throws JacksonException {
    final Map<String, String> object = Map.of("key", "value");
    this.defaultJsonMapper.writeValueAsString(object);
    verify(this.objectMapper).writeValueAsString(object);
  }

}
