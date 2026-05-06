package dev.inditex.scsoutbox.jdbc.config;

import lombok.Getter;

/**
 * Base JDBC configuration properties for outbox-related table and schema names.
 */
public abstract class AbstractJdbcProperties {

  /**
   * Default schema value used when no schema is configured.
   */
  protected static final String DEFAULT_SCHEMA_VALUE = "";

  @Getter
  private final String tableName;

  @Getter
  private final String schema;

  /**
   * Creates validated JDBC properties for table and schema names.
   *
   * @param inputTableName configured table name
   * @param inputSchema configured schema name
   * @param defaultTableName default table name used when the configured one is empty
   */
  protected AbstractJdbcProperties(final String inputTableName, final String inputSchema, final String defaultTableName) {
    if (inputTableName == null || inputTableName.isEmpty()) {
      this.tableName = defaultTableName;
    } else if (inputTableName.contains(" ")) {
      throw new IllegalArgumentException("Table name cannot contain spaces");
    } else {
      this.tableName = inputTableName;
    }

    if (inputSchema == null || inputSchema.isEmpty()) {
      this.schema = DEFAULT_SCHEMA_VALUE;
    } else if (inputSchema.contains(" ")) {
      throw new IllegalArgumentException("Schema name cannot contain spaces");
    } else {
      this.schema = inputSchema;
    }
  }
}
