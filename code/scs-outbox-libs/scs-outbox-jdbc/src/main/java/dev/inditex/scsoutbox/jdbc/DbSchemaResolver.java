package dev.inditex.scsoutbox.jdbc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the database schemaName to be used, prioritizing the configured schemaName over the detected default schemaName.
 */
@Slf4j
@RequiredArgsConstructor // Automatically creates constructor for final fields
public class DbSchemaResolver {

  private final DataSourceMetadata dataSourceMetadata;

  /**
   * Exception thrown when a schemaName cannot be resolved.
   */
  public static class SchemaResolutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SchemaResolutionException(String message) {
      super(message);
    }
  }

  /**
   * Resolves the schemaName to use.
   *
   * <p>It prioritizes the explicitly configured schemaName if available. If no schemaName is configured, it falls back to the default
   * schemaName detected from the database connection via JdbcDataSourceMetadata. If neither a configured schemaName nor a default
   * schemaName can be determined, an exception is thrown.</p>
   *
   * @param configuredSchema the schemaName configured in properties (can be null or empty).
   * @return the resolved schemaName to use.
   * @throws SchemaResolutionException if no schemaName can be resolved.
   */
  public String resolve(String configuredSchema) {
    if (configuredSchema != null && !configuredSchema.isEmpty()) {
      log.debug("Using configured schema: {}", configuredSchema);
      return configuredSchema;
    }

    // Get default schemaName from the metadata collaborator
    final String defaultSchema = this.dataSourceMetadata.getDefaultSchema();
    if (defaultSchema != null && !defaultSchema.isEmpty()) {
      log.debug("Using detected default schema: {}", defaultSchema);
      return defaultSchema; // Default schemaName from detection is assumed to be trimmed or valid
    }

    // If we can't determine a schemaName, throw an exception
    throw new SchemaResolutionException(
        "No schemaName configured and could not detect default schemaName from database connection");
  }
}
