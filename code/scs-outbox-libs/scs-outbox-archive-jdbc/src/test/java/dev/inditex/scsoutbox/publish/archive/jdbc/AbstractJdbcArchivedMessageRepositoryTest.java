package dev.inditex.scsoutbox.publish.archive.jdbc;

import static dev.inditex.scsoutbox.publish.archive.jdbc.ArchivedMessageMother.anArchivedMessage;

import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.jdbc.DataSourceMetadata;
import dev.inditex.scsoutbox.jdbc.DbSchemaResolver;
import dev.inditex.scsoutbox.jdbc.SchemaName;
import dev.inditex.scsoutbox.jdbc.Table;
import dev.inditex.scsoutbox.jdbc.TableName;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessage;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class AbstractJdbcArchivedMessageRepositoryTest {

  private JdbcArchivedMessageRepository repository;

  @BeforeEach
  public void setUp() {
    final JdbcTemplate jdbcTemplate = new JdbcTemplate(this.getDataSource());
    final DbSchemaResolver dbSchemaResolver = new DbSchemaResolver(new DataSourceMetadata(this.getDataSource()));
    final String schema = dbSchemaResolver.resolve(null);
    jdbcTemplate.execute("DELETE FROM SCS_OUTBOX_ARCHIVE");
    final ArchivedMessageSerializer serializer = new ArchivedMessageSerializer(new JavaSerialization(), new JsonHeadersMapper());
    this.repository = new JdbcArchivedMessageRepository(
        jdbcTemplate,
        serializer,
        new Table(new SchemaName(schema), new TableName("SCS_OUTBOX_ARCHIVE")));
  }

  public abstract DataSource getDataSource();

  @Test
  void save_message_with_same_id() {
    final ArchivedMessage archivedMessage = anArchivedMessage();
    this.repository.save(archivedMessage);

    assertThrows(DuplicateKeyException.class, () -> this.repository.save(archivedMessage));
  }

}
