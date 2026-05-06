package dev.inditex.scsoutbox.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TableNameTest {

  @Test
  void shouldCreateTableNameWhenValueIsValid() {
    assertThatNoException().isThrownBy(() -> new TableName("mytable"));
    assertThat(new TableName("mytable").value()).isEqualTo("mytable");
    assertThatNoException().isThrownBy(() -> new TableName("my_table_123"));
    String maxLengthTable = "a".repeat(128);
    assertThatNoException().isThrownBy(() -> new TableName(maxLengthTable));
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueIsNull() {
    assertThatThrownBy(() -> new TableName(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TableName value cannot be null or empty");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueIsEmpty() {
    assertThatThrownBy(() -> new TableName(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TableName value cannot be null or empty");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueIsBlank() {
    assertThatThrownBy(() -> new TableName("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TableName value cannot be null or empty");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueExceedsMaxLength() {
    String longValue = "a".repeat(129);
    assertThatThrownBy(() -> new TableName(longValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TableName value cannot exceed 128 characters");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValueContainsInvalidCharacters() {
    assertThatThrownBy(() -> new TableName("my-table"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TableName value can only contain alphanumeric characters and underscores");
    assertThatThrownBy(() -> new TableName("my table"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TableName value can only contain alphanumeric characters and underscores");
    assertThatThrownBy(() -> new TableName("mytable."))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TableName value can only contain alphanumeric characters and underscores");
  }
}
