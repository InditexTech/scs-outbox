package dev.inditex.scsoutbox.jdbc;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TableTest {

  private SchemaName schemaName;

  private TableName tableName;

  @BeforeEach
  void setup() {
    this.schemaName = new SchemaName("myschema");
    this.tableName = new TableName("mytable");
  }

  @Test
  void shouldCreateTableWhenSchemaAndTableNameAreValid() {
    assertThatNoException().isThrownBy(() -> new Table(this.schemaName, this.tableName));
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenTableNameIsNull() {
    assertThatThrownBy(() -> new Table(this.schemaName, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tableName cannot be null");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenSchemaIsNull() {
    assertThatThrownBy(() -> new Table(null, this.tableName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("schemaName cannot be null");
  }

  @Test
  void shouldReturnQualifiedTableName() {
    final Table table = new Table(this.schemaName, this.tableName);
    final String expectedQualifiedName = this.schemaName.value() + "." + this.tableName.value();
    assertEquals(expectedQualifiedName, table.getQualifiedTableName());
  }
}
