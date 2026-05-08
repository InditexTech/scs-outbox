package dev.inditex.scsoutbox.jdbc;

import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL-specific implementation of JdbcOutboxMessageRepository.
 */
@Slf4j
public class PostgresqlJdbcOutboxMessageRepository extends JdbcOutboxMessageRepository {

  public PostgresqlJdbcOutboxMessageRepository(JdbcTemplate jdbcTemplate, Table table, OutboxMessageSerializer outboxMessageSerializer) {
    super(jdbcTemplate, table, outboxMessageSerializer);
  }

  @Override
  protected Query getEstimatedCountQuery() {
    // Create a parameterized query with table name and schemaName as parameters
    final String sql = "SELECT n_live_tup FROM pg_stat_all_tables WHERE relname = LOWER(?) AND schemaname = LOWER(?)";
    return Query.of(sql, this.getTableName(), this.getSchema());
  }
}
