package dev.inditex.scsoutbox.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;

import dev.inditex.scsoutbox.jdbc.DataSourceMetadata.DatabaseAccessException;
import dev.inditex.scsoutbox.jdbc.DataSourceMetadata.JdbcDatabaseType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for JdbcDataSourceMetadata.
 */
class DataSourceMetadataTest {

  private DataSource dataSource;

  private Connection connection;

  private DatabaseMetaData databaseMetadata;

  @BeforeEach
  void setUp() throws SQLException {
    this.dataSource = mock(DataSource.class);
    this.connection = mock(Connection.class);
    this.databaseMetadata = mock(DatabaseMetaData.class);

    when(this.dataSource.getConnection()).thenReturn(this.connection);
    when(this.connection.getMetaData()).thenReturn(this.databaseMetadata);
  }

  @Test
  void detect_postgresql_database() throws SQLException {
    // Given
    when(this.databaseMetadata.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(this.connection.getSchema()).thenReturn("conn_schema");

    // When
    final DataSourceMetadata metadata = new DataSourceMetadata(this.dataSource);

    // Then
    assertEquals(JdbcDatabaseType.POSTGRESQL, metadata.getDatabaseType());
    assertEquals("conn_schema", metadata.getDefaultSchema());
  }

  @Test
  void detect_mariadb_database_with_connection_schema() throws SQLException {
    // Given
    when(this.databaseMetadata.getDatabaseProductName()).thenReturn("MariaDB");
    when(this.connection.getSchema()).thenReturn("conn_schema");

    // When
    final DataSourceMetadata metadata = new DataSourceMetadata(this.dataSource);

    // Then
    assertEquals(JdbcDatabaseType.MARIADB, metadata.getDatabaseType());
    assertEquals("conn_schema", metadata.getDefaultSchema());
  }

  @Test
  void detect_mariadb_database_with_catalog() throws SQLException {
    // Given
    when(this.databaseMetadata.getDatabaseProductName()).thenReturn("MariaDB");
    when(this.connection.getSchema()).thenReturn("");
    when(this.connection.getCatalog()).thenReturn("catalog_db");

    // When
    final DataSourceMetadata metadata = new DataSourceMetadata(this.dataSource);

    // Then
    assertEquals(JdbcDatabaseType.MARIADB, metadata.getDatabaseType());
    assertEquals("catalog_db", metadata.getDefaultSchema());
  }

  @Test
  void return_empty_schema_when_no_schema_can_be_detected() throws SQLException {
    // Given
    when(this.databaseMetadata.getDatabaseProductName()).thenReturn("OtherDB");
    when(this.connection.getSchema()).thenReturn(null);
    when(this.connection.getCatalog()).thenReturn(null);

    // When
    final DataSourceMetadata metadata = new DataSourceMetadata(this.dataSource);

    // Then
    assertEquals(JdbcDatabaseType.OTHER, metadata.getDatabaseType());
    assertEquals("", metadata.getDefaultSchema());
  }

  @Test
  void return_empty_schema_when_exception_occurs_during_schema_detection() throws SQLException {
    // Given
    when(this.databaseMetadata.getDatabaseProductName()).thenReturn("OtherDB");
    when(this.connection.getSchema()).thenThrow(new SQLException("Schema error"));
    when(this.connection.getCatalog()).thenThrow(new SQLException("Catalog error"));

    // When
    final DataSourceMetadata metadata = new DataSourceMetadata(this.dataSource);

    // Then
    assertEquals(JdbcDatabaseType.OTHER, metadata.getDatabaseType());
    assertEquals("", metadata.getDefaultSchema());
  }

  @Test
  void detect_other_database_type() throws SQLException {
    // Given
    when(this.databaseMetadata.getDatabaseProductName()).thenReturn("OtherDB");
    when(this.connection.getSchema()).thenReturn("some_schema");

    // When
    final DataSourceMetadata metadata = new DataSourceMetadata(this.dataSource);

    // Then
    assertEquals(JdbcDatabaseType.OTHER, metadata.getDatabaseType());
    assertEquals("some_schema", metadata.getDefaultSchema());
  }

  @Test
  void handle_sql_exception_when_getting_connection() throws SQLException {
    // Given
    when(this.dataSource.getConnection()).thenThrow(new SQLException("Connection error"));

    // When/Then
    assertThrows(DatabaseAccessException.class,
        () -> new DataSourceMetadata(this.dataSource));
  }
}
