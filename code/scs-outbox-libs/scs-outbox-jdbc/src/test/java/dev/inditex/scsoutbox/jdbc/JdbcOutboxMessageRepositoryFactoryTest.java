package dev.inditex.scsoutbox.jdbc;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.inditex.scsoutbox.jdbc.DataSourceMetadata.JdbcDatabaseType;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for {@link JdbcOutboxMessageRepositoryFactory}.
 */
class JdbcOutboxMessageRepositoryFactoryTest {

  private DataSourceMetadata postgresMetadata;

  private DataSourceMetadata mariadbMetadata;

  private DataSourceMetadata otherMetadata;

  // Rename config to definition
  private Table definition;

  private JdbcTemplate jdbcTemplate;

  private OutboxMessageSerializer outboxMessageSerializer;

  @BeforeEach
  void setUp() {
    // Mock metadata for different DB types
    this.postgresMetadata = mock(DataSourceMetadata.class);
    when(this.postgresMetadata.getDatabaseType()).thenReturn(JdbcDatabaseType.POSTGRESQL);

    this.mariadbMetadata = mock(DataSourceMetadata.class);
    when(this.mariadbMetadata.getDatabaseType()).thenReturn(JdbcDatabaseType.MARIADB);

    this.otherMetadata = mock(DataSourceMetadata.class);
    when(this.otherMetadata.getDatabaseType()).thenReturn(JdbcDatabaseType.OTHER);

    // Use real instances or mocks for dependencies as appropriate
    this.outboxMessageSerializer = new OutboxMessageSerializer(new JavaSerialization(), new JsonHeadersMapper());
    this.jdbcTemplate = mock(JdbcTemplate.class);

    // Create a standard definition object for tests (without jdbcTemplate)
    this.definition = new Table(new SchemaName("resolved_schema"), new TableName("outbox_table"));
  }

  @Test
  void create_shouldReturnPostgresqlRepository_whenDbTypeIsPostgresql() {
    // When
    // Pass jdbcTemplate separately
    final JdbcOutboxMessageRepository repository = JdbcOutboxMessageRepositoryFactory.create(
        this.jdbcTemplate, this.postgresMetadata, this.definition, this.outboxMessageSerializer);

    // Then
    assertInstanceOf(PostgresqlJdbcOutboxMessageRepository.class, repository);
  }

  @Test
  void create_shouldReturnMariadbRepository_whenDbTypeIsMariadb() {
    // When
    // Pass jdbcTemplate separately
    final JdbcOutboxMessageRepository repository = JdbcOutboxMessageRepositoryFactory.create(
        this.jdbcTemplate, this.mariadbMetadata, this.definition, this.outboxMessageSerializer);

    // Then
    assertInstanceOf(MariadbJdbcOutboxMessageRepository.class, repository);
  }

  @Test
  void create_shouldReturnDefaultRepository_whenDbTypeIsOther() {
    // When
    // Pass jdbcTemplate separately
    final JdbcOutboxMessageRepository repository = JdbcOutboxMessageRepositoryFactory.create(
        this.jdbcTemplate, this.otherMetadata, this.definition, this.outboxMessageSerializer);

    // Then
    assertInstanceOf(JdbcOutboxMessageRepository.class, repository);
  }

}
