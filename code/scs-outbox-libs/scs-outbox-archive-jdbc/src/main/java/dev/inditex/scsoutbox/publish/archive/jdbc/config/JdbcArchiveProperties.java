package dev.inditex.scsoutbox.publish.archive.jdbc.config;

import dev.inditex.scsoutbox.jdbc.config.AbstractJdbcProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("scs-outbox.publishing.archive.jdbc")
public class JdbcArchiveProperties extends AbstractJdbcProperties {

  private static final String DEFAULT_TABLE_NAME = "SCS_OUTBOX_ARCHIVE";

  public JdbcArchiveProperties() {
    super(DEFAULT_TABLE_NAME, DEFAULT_SCHEMA_VALUE, DEFAULT_TABLE_NAME);
  }

  @ConstructorBinding
  public JdbcArchiveProperties(final String tableName, final String schema) {
    super(tableName, schema, DEFAULT_TABLE_NAME);
  }
}
