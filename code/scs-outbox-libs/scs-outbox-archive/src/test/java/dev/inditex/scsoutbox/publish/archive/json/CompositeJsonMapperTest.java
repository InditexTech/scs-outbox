package dev.inditex.scsoutbox.publish.archive.json;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompositeJsonMapperTest {

  private CompositeJsonMapper compositeJsonMapper;

  private DefaultJsonMapper defaultJsonMapper;

  private AvroToJsonMapper avroToJsonMapper;

  @BeforeEach
  void setUp() {
    this.defaultJsonMapper = mock(DefaultJsonMapper.class);
    when(this.defaultJsonMapper.getValueType()).thenCallRealMethod();
    this.avroToJsonMapper = mock(AvroToJsonMapper.class);
    when(this.avroToJsonMapper.getValueType()).thenCallRealMethod();
    this.compositeJsonMapper = new CompositeJsonMapper(
        this.defaultJsonMapper, List.of(this.avroToJsonMapper));
  }

  @Test
  void delegate_to_correct_json_mapper() {
    final BookCreatedMessage avroValue = BookCreatedMessage.newBuilder()
        .setBookId(UUID.fromString("e5e313ab-110e-4890-8da3-7547f119c281"))
        .build();

    this.compositeJsonMapper.writeValueAsString(avroValue);

    verify(this.avroToJsonMapper).writeValueAsString(avroValue);
  }

  @Test
  void delegate_to_default_json_mapper_when_no_mapper_available() {
    final Map<String, String> value = Map.of("key", "value");

    this.compositeJsonMapper.writeValueAsString(value);

    verify(this.defaultJsonMapper).writeValueAsString(value);
  }

}
