package dev.inditex.scsoutbox.jdbc;

import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Factory for creating {@link JdbcOutboxMessageRepository} instances based on the detected database type. This factory expects pre-resolved
 * definition provided via {@link Table}.
 */
@Slf4j
public final class JdbcOutboxMessageRepositoryFactory {

  private JdbcOutboxMessageRepositoryFactory() {
    // Prevent instantiation
  }

  /**
   * Creates a new {@link JdbcOutboxMessageRepository} instance based on the detected database type using pre-resolved Table, with raw
   * passthrough support.
   *
   * @param jdbcTemplate The {@link JdbcTemplate} to use.
   * @param datasourceMetadata The detected database metadata.
   * @param table The pre-resolved JDBC repository Table (schemaName, tableName).
   * @return A new {@link JdbcOutboxMessageRepository} instance suitable for the detected database.
   */
  public static JdbcOutboxMessageRepository create(
      JdbcTemplate jdbcTemplate,
      DataSourceMetadata datasourceMetadata,
      Table table,
      OutboxMessageSerializer outboxMessageSerializer) {

    // Create appropriate repository implementation based on the detected database type
    switch (datasourceMetadata.getDatabaseType()) {
      case POSTGRESQL:
        return new PostgresqlJdbcOutboxMessageRepository(
            jdbcTemplate, table, outboxMessageSerializer);
      case MARIADB:
        return new MariadbJdbcOutboxMessageRepository(
            jdbcTemplate, table, outboxMessageSerializer);
      default:
        log.warn("Database type {} not specifically handled, using default JDBC repository implementation.",
            datasourceMetadata.getDatabaseType());
        return new JdbcOutboxMessageRepository(
            jdbcTemplate, table, outboxMessageSerializer);
    }
  }
}
