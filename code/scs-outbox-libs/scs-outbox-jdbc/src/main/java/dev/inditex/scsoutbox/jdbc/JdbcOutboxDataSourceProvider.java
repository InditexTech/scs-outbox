package dev.inditex.scsoutbox.jdbc;

import java.util.Objects;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * JDBC implementation of {@link OutboxDataSourceProvider}. Manages DataSource instances for capture and publishing operations, with
 * optional dedicated publishing DataSource.
 */
@Slf4j
public class JdbcOutboxDataSourceProvider implements OutboxDataSourceProvider {

  private final DataSource primaryDataSource; // capture

  private final DataSource publishingDataSource; // dedicated or same as primary

  private final DataSourceMetadata primaryMetadata;

  private final DataSourceMetadata publishingMetadata;

  /**
   * Creates a provider with a mandatory capture DataSource and an optional publishing DataSource.
   *
   * @param captureDataSource the DataSource for capture operations (required, typically the primary DataSource)
   * @param publishingDataSource the DataSource for publishing operations (optional, can be null)
   */
  public JdbcOutboxDataSourceProvider(
      DataSource captureDataSource,
      DataSource publishingDataSource) {

    this.primaryDataSource = Objects.requireNonNull(captureDataSource,
        "Capture DataSource is required");

    // If no dedicated publishing DataSource, use the same as capture
    if (publishingDataSource == null) {
      this.publishingDataSource = this.primaryDataSource;
      log.info("No dedicated publishing DataSource configured. Using primary DataSource for both capture and publishing operations");
    } else {
      this.publishingDataSource = publishingDataSource;
      log.info("Using dedicated DataSources: capture and publishing operations are isolated");
    }

    // Create and cache metadata (expensive operation, done once)
    this.primaryMetadata = new DataSourceMetadata(this.primaryDataSource);
    this.publishingMetadata = (this.publishingDataSource == this.primaryDataSource)
        ? this.primaryMetadata
        : new DataSourceMetadata(this.publishingDataSource);

    log.debug("Primary (capture) DataSource type: {}", this.primaryMetadata.getDatabaseType());
    if (this.publishingDataSource != this.primaryDataSource) {
      log.debug("Publishing DataSource type: {}", this.publishingMetadata.getDatabaseType());
    }
  }

  @Override
  public DataSource getPrimary() {
    return this.primaryDataSource;
  }

  @Override
  public DataSource getDedicatedForPublishing() {
    return this.publishingDataSource;
  }

  @Override
  public DataSourceMetadata getPrimaryDataSourceMetadata() {
    return this.primaryMetadata;
  }

  @Override
  public DataSourceMetadata getDedicatedForPublishingDataSourceMetadata() {
    return this.publishingMetadata;
  }
}
