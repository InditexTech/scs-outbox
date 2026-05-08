package dev.inditex.scsoutbox.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Class that encapsulates database metadata detection functionality. This class handles detection of database type and default schemaName.
 */
@Slf4j
public class DataSourceMetadata {

  /**
   * Enumeration of supported database types.
   */
  public enum JdbcDatabaseType {
    POSTGRESQL, MARIADB, OTHER
  }

  @Getter
  private final JdbcDatabaseType databaseType;

  @Getter
  private final String defaultSchema;

  /**
   * Exception thrown when there is a database access error.
   */
  public static class DatabaseAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DatabaseAccessException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Creates a new JdbcDataSourceMetadata instance and detects both database type and default schemaName.
   *
   * @param dataSource The data source to extract metadata from
   */
  public DataSourceMetadata(DataSource dataSource) {
    try (final Connection connection = dataSource.getConnection()) {
      this.databaseType = this.detectDatabaseType(connection);
      this.defaultSchema = this.detectDefaultSchema(connection);
      log.info("Detected database type: {}, default schema: {}", this.databaseType,
          this.defaultSchema.isEmpty() ? "<empty>" : this.defaultSchema);
    } catch (final SQLException e) {
      throw new DatabaseAccessException("Error accessing database metadata", e);
    }
  }

  /**
   * Detects the database type from the connection metadata.
   *
   * @param connection The database connection
   * @return The detected database type
   * @throws SQLException If there is an error accessing the database metadata
   */
  private JdbcDatabaseType detectDatabaseType(Connection connection) throws SQLException {
    final String databaseProductName = connection.getMetaData().getDatabaseProductName();
    if (databaseProductName.toLowerCase().contains("postgresql")) {
      return JdbcDatabaseType.POSTGRESQL;
    } else if (databaseProductName.toLowerCase().contains("mariadb")) {
      return JdbcDatabaseType.MARIADB;
    } else {
      return JdbcDatabaseType.OTHER;
    }
  }

  /**
   * Detects the default schemaName from the connection. If schemaName cannot be detected, returns an empty string instead of throwing an
   * exception.
   *
   * @param connection The database connection
   * @return The detected default schemaName or empty string if not detected
   */
  private String detectDefaultSchema(Connection connection) {
    try {
      // Try to get the current schemaName from the connection
      final String currentSchema = connection.getSchema();
      if (this.isNotEmpty(currentSchema)) {
        return currentSchema;
      }

      // If no schemaName is available, try using the catalog name
      final String catalog = connection.getCatalog();
      if (this.isNotEmpty(catalog)) {
        return catalog;
      }
    } catch (final SQLException e) {
      log.debug("Error detecting default schemaName: {}", e.getMessage());
    }

    // Return empty string if schemaName cannot be detected
    log.debug("Could not detect default schemaName from database connection");
    return "";
  }

  /**
   * Checks if a string is not empty, meaning it is not null and not an empty string.
   *
   * @param string The string to check
   * @return true if the string is not null and not empty, false otherwise
   */
  private boolean isNotEmpty(final String string) {
    return string != null && !string.isEmpty();
  }
}
