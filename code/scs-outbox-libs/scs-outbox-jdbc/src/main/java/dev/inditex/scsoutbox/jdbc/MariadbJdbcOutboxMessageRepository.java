package dev.inditex.scsoutbox.jdbc;

import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MariaDB/MySQL-specific implementation of JdbcOutboxMessageRepository.
 */
@Slf4j
public class MariadbJdbcOutboxMessageRepository extends JdbcOutboxMessageRepository {

  public MariadbJdbcOutboxMessageRepository(JdbcTemplate jdbcTemplate, Table table, OutboxMessageSerializer outboxMessageSerializer) {
    super(jdbcTemplate, table, outboxMessageSerializer);
  }

  @Override
  protected Query getEstimatedCountQuery() {
    // Create a parameterized query with table name and schemaName as parameters
    final String sql = "SELECT table_rows FROM information_schema.tables WHERE table_name = ? AND table_schema = ?";
    return Query.of(sql, this.getTableName(), this.getSchema());
  }
}
