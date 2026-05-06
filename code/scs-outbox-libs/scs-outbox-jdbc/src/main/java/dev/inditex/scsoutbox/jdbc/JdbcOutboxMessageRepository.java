package dev.inditex.scsoutbox.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer.SerializedOutboxMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation of OutboxMessageRepository.
 */
@Slf4j
public class JdbcOutboxMessageRepository implements OutboxMessageRepository {

  private final JdbcTemplate jdbcTemplate;

  private final Table table;

  private final OutboxMessageSerializer serializer;

  /**
   * Creates a new instance of JdbcOutboxMessageRepository.
   *
   * @param jdbcTemplate the JDBC template to use
   * @param table the table location to use
   */
  public JdbcOutboxMessageRepository(JdbcTemplate jdbcTemplate, Table table, OutboxMessageSerializer outboxMessageSerializer) {
    this.jdbcTemplate = jdbcTemplate;
    this.table = table;
    this.serializer = outboxMessageSerializer;
    log.info("Using table: {}", table.getQualifiedTableName());
  }

  /**
   * Container class for SQL query and its parameters.
   */
  @Getter
  @RequiredArgsConstructor
  protected static class Query {
    private final String sql;

    private final Object[] params;

    /**
     * Creates a new instance with an empty parameter array.
     *
     * @param sql the SQL query
     * @return a new instance with the given SQL and empty params
     */
    public static Query of(String sql) {
      return new Query(sql, new Object[0]);
    }

    /**
     * Creates a new instance with the given SQL and params.
     *
     * @param sql the SQL query
     * @param params the query parameters
     * @return a new instance with the given SQL and params
     */
    public static Query of(String sql, Object... params) {
      return new Query(sql, params);
    }
  }

  @Override
  public List<OutboxMessage> findAllOrderByCapturedAt(int maxResults) {
    final Query query = this.buildFindAllQuery(null, maxResults);
    return this.queryWithDeserializationErrorHandling(query);
  }

  /**
   * Executes a query and deserializes messages one by one, stopping at the first deserialization error. Messages that were successfully
   * deserialized before the error are returned.
   *
   * @param query the query to execute
   * @return list of successfully deserialized messages (may be truncated if a deserialization error occurred)
   */
  private List<OutboxMessage> queryWithDeserializationErrorHandling(Query query) {
    final List<OutboxMessage> messages = new ArrayList<>();
    final AtomicBoolean errorOccurred = new AtomicBoolean(false);

    this.jdbcTemplate.query(query.getSql(), rs -> {
      if (errorOccurred.get()) {
        return;
      }
      try {
        final OutboxMessage message = this.mapRow(rs);
        messages.add(message);
      } catch (final Exception e) {
        errorOccurred.set(true);
        log.error("Deserialization failed for message [{}] at destination [{}]. "
            + "Returning {} successfully deserialized messages.",
            rs.getString("ID"),
            rs.getString("DESTINATION"),
            messages.size(), e);
      }
    }, query.getParams());

    return messages;
  }

  /**
   * Maps a single row from the ResultSet to an OutboxMessage.
   *
   * @param rs the ResultSet positioned at the current row
   * @return the mapped OutboxMessage
   * @throws SQLException if a database access error occurs
   */
  private OutboxMessage mapRow(ResultSet rs) throws SQLException {
    final SerializedOutboxMessage serializedOutboxMessage = SerializedOutboxMessage.builder()
        .id(UUID.fromString(rs.getObject("ID", String.class)))
        .bindingName(rs.getObject("BINDING_NAME", String.class))
        .capturedAt(rs.getObject("CAPTURED_AT", Timestamp.class).toInstant())
        .destination(rs.getObject("DESTINATION", String.class))
        .headers(rs.getObject("HEADERS", String.class))
        .payload(rs.getBytes("PAYLOAD"))
        .build();
    return this.serializer.deserialize(serializedOutboxMessage);
  }

  @Override
  public List<OutboxMessage> findAllOrderByCapturedAtExcludingDestinations(Set<String> excludedDestinations, int maxResults) {
    if (excludedDestinations == null || excludedDestinations.isEmpty()) {
      return this.findAllOrderByCapturedAt(maxResults);
    }

    final Query query = this.buildFindAllQuery(excludedDestinations, maxResults);
    return this.queryWithDeserializationErrorHandling(query);
  }

  /**
   * Builds the SQL query for finding outbox messages with optional destination exclusions.
   *
   * @param excludedDestinations destinations to exclude, or null for no exclusions
   * @param maxResults maximum number of results, or <= 0 for unlimited
   * @return Query object containing SQL and parameters
   */
  private Query buildFindAllQuery(Set<String> excludedDestinations, int maxResults) {
    final StringBuilder sql = new StringBuilder("SELECT * FROM " + this.table.getQualifiedTableName());

    Object[] params = new Object[0];

    // Add WHERE clause if destinations need to be excluded
    if (excludedDestinations != null && !excludedDestinations.isEmpty()) {
      sql.append(" WHERE DESTINATION NOT IN (");
      for (int i = 0; i < excludedDestinations.size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        sql.append("?");
      }
      sql.append(")");
      params = excludedDestinations.toArray();
    }

    sql.append(" ORDER BY CAPTURED_AT ASC");

    // Add LIMIT clause if maxResults is specified
    if (maxResults > 0) {
      sql.append(" LIMIT ").append(maxResults);
    }

    return Query.of(sql.toString(), params);
  }

  protected JdbcTemplate getJdbcTemplate() {
    return this.jdbcTemplate;
  }

  /**
   * Gets the table name.
   *
   * @return the table name
   */
  protected String getTableName() {
    return this.table.tableName().value();
  }

  /**
   * Gets the schemaName.
   *
   * @return the schemaName
   */
  protected String getSchema() {
    return this.table.schemaName().value();
  }

  @Override
  public long count() {
    final Query countQuery = this.getCountQuery();
    return this.queryForLong(countQuery.getSql(), countQuery.getParams());
  }

  private Query getCountQuery() {
    return Query.of("SELECT COUNT(*) FROM " + this.table.getQualifiedTableName());
  }

  /**
   * Gets the estimated count query with its parameters. Override this method in database-specific implementations to provide a custom query
   * for retrieving the estimated count.
   *
   * @return a SqlQueryWithParams object containing the query and its parameters
   */
  protected Query getEstimatedCountQuery() {
    // By default, we use the count query for the estimated count for JDBC generic repositories.
    return this.getCountQuery();
  }

  @Override
  public long estimatedCount() {
    try {
      final Query queryWithParams = this.getEstimatedCountQuery();
      return this.queryForLong(queryWithParams.getSql(), queryWithParams.getParams());
    } catch (final Exception e) {
      log.warn("An estimated count cannot be obtained.", e);
      return this.count();
    }
  }

  /**
   * Executes a parameterized query that returns a single long value.
   *
   * @param sql the SQL query to execute
   * @param args the arguments to bind to the query
   * @return the long value returned by the query, or 0 if the result is null
   */
  private long queryForLong(String sql, Object[] args) {
    final Long result = this.getJdbcTemplate().queryForObject(sql, Long.class, args);
    return result != null ? result : 0;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void save(final OutboxMessage outboxMessage) {
    final SerializedOutboxMessage serializedOutboxMessage = this.serializer.serialize(outboxMessage);
    this.jdbcTemplate.update(
        "INSERT INTO "
            + this.table.getQualifiedTableName() + " (ID, BINDING_NAME, CAPTURED_AT, DESTINATION, HEADERS, PAYLOAD) "
            + "VALUES ( ?, ?, ?, ?, ?, ?)",
        serializedOutboxMessage.getId().toString(),
        serializedOutboxMessage.getBindingName(),
        Timestamp.from(serializedOutboxMessage.getCapturedAt()),
        serializedOutboxMessage.getDestination(),
        serializedOutboxMessage.getHeaders(),
        serializedOutboxMessage.getPayload());
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public void delete(final OutboxMessage outboxMessage) {
    this.jdbcTemplate.update("DELETE FROM " + this.table.getQualifiedTableName() + " WHERE ID = ?",
        outboxMessage.getId().toString());
  }
}
