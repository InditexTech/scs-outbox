package dev.inditex.scsoutbox.jdbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * JDBC configuration properties for the outbox table location.
 */
@ConfigurationProperties("scs-outbox.jdbc")
public class JdbcProperties extends AbstractJdbcProperties {

  /**
   * Default JDBC outbox table name.
   */
  private static final String DEFAULT_TABLE_NAME = "SCS_OUTBOX";

  /**
   * Creates JDBC properties with default table and schema values.
   */
  public JdbcProperties() {
    super(DEFAULT_TABLE_NAME, DEFAULT_SCHEMA_VALUE, DEFAULT_TABLE_NAME);
  }

  /**
   * Creates JDBC properties with the configured table and schema values.
   *
   * @param tableName configured outbox table name
   * @param schema configured schema name
   */
  @ConstructorBinding
  public JdbcProperties(final String tableName, final String schema) {
    super(tableName, schema, DEFAULT_TABLE_NAME);
  }

}
