package dev.inditex.scsoutbox.publish.archive.jdbc;

import java.sql.Timestamp;

import dev.inditex.scsoutbox.jdbc.Table;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessage;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer.SerializedArchivedMessage;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public class JdbcArchivedMessageRepository implements ArchivedMessageRepository {

  private final JdbcTemplate jdbcTemplate;

  private final ArchivedMessageSerializer serializer;

  private final String jsonPayloadColumnType;

  private final Table table;

  @SneakyThrows
  public JdbcArchivedMessageRepository(final JdbcTemplate jdbcTemplate, final ArchivedMessageSerializer serializer, final Table table) {
    this.jdbcTemplate = jdbcTemplate;
    this.serializer = serializer;
    this.table = table;
    log.info("Using table: {}", table.getQualifiedTableName());
    this.jsonPayloadColumnType = this.getColumnDataType("JSON_PAYLOAD");
  }

  private String getColumnDataType(final String columnName) {
    try {
      // Build the basic SQL query
      final String sql = "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
          + "WHERE UPPER(TABLE_NAME) = UPPER('" + this.table.tableName().value() + "') "
          + "AND UPPER(COLUMN_NAME) = UPPER('" + columnName + "') "
          + "AND UPPER(TABLE_SCHEMA) = UPPER('" + this.table.schemaName().value() + "')";

      return this.jdbcTemplate.queryForObject(sql, String.class);
    } catch (final EmptyResultDataAccessException e) {
      return "text";
    }
  }

  @Override
  public void save(final ArchivedMessage archivedMessage) {
    final SerializedArchivedMessage serialized = this.serializer.serialize(archivedMessage);
    this.jdbcTemplate.update(
        "INSERT INTO "
            + this.table.getQualifiedTableName() + " "
            + "(ID, ARCHIVED_AT, CAPTURED_AT, DESTINATION, CONTENT_TYPE, HEADERS, PAYLOAD, SERIALIZATION, JSON_PAYLOAD) "
            + "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?," + this.getSQLJsonPayloadValue() + ")",
        serialized.getId(),
        Timestamp.from(serialized.getArchivedAt()),
        Timestamp.from(serialized.getCapturedAt()),
        serialized.getDestination(),
        serialized.getContentType(),
        serialized.getHeaders(),
        serialized.getPayload(),
        serialized.getSerialization(),
        serialized.getJsonPayload());
  }

  private String getSQLJsonPayloadValue() {
    if (this.jsonPayloadColumnType.startsWith("json")) {
      return "cast(? as json)";
    }
    return "?";
  }

}
