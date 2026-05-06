package dev.inditex.scsoutbox.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SchemaTest {

  @Test
  void shouldCreateSchemaWhenValueIsValid() {
    assertThatNoException().isThrownBy(() -> new SchemaName("myschema"));
    assertThat(new SchemaName("myschema").value()).isEqualTo("myschema");
    assertThatNoException().isThrownBy(() -> new SchemaName("my_schema_123"));
    final String maxLengthSchema = "a".repeat(128);
    assertThatNoException().isThrownBy(() -> new SchemaName(maxLengthSchema));
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueIsNull() {
    assertThatThrownBy(() -> new SchemaName(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SchemaName value cannot be null or empty");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueIsEmpty() {
    assertThatThrownBy(() -> new SchemaName(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SchemaName value cannot be null or empty");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueIsBlank() {
    assertThatThrownBy(() -> new SchemaName("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SchemaName value cannot be null or empty");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueExceedsMaxLength() {
    final String longValue = "a".repeat(129);
    assertThatThrownBy(() -> new SchemaName(longValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SchemaName value cannot exceed 128 characters");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueContainsInvalidCharacters() {
    assertThatThrownBy(() -> new SchemaName("my-schemaName"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SchemaName value can only contain alphanumeric characters and underscores");
    assertThatThrownBy(() -> new SchemaName("my schemaName"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SchemaName value can only contain alphanumeric characters and underscores");
    assertThatThrownBy(() -> new SchemaName("myschema."))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SchemaName value can only contain alphanumeric characters and underscores");
  }
}
