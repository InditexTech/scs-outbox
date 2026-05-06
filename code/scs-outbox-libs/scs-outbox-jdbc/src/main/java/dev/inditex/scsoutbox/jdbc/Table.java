package dev.inditex.scsoutbox.jdbc;

/**
 * Value object representing a qualified database table name.
 *
 * @param schemaName table schema name
 * @param tableName table name
 */
public record Table(SchemaName schemaName, TableName tableName) {

  /**
   * Creates a table value object with non-null schema and table names.
   */
  public Table {
    if (schemaName == null) {
      throw new IllegalArgumentException("schemaName cannot be null");
    }
    if (tableName == null) {
      throw new IllegalArgumentException("tableName cannot be null");
    }
  }

  /**
   * Returns the qualified table name in {@code schema.table} format.
   *
   * @return the qualified table name
   */
  public String getQualifiedTableName() {
    return this.schemaName.value() + "." + this.tableName.value();
  }
}
