package dev.inditex.scsoutbox.jdbc;

import javax.sql.DataSource;

/**
 * Provides DataSource instances and their metadata for different outbox operations. This allows using different connection pools for
 * capture (transactional) and publishing (scheduled) operations.
 */
public interface OutboxDataSourceProvider {

  /**
   * Gets the DataSource used for message capture operations. This DataSource is used during application transactions.
   *
   * @return the capture DataSource (never null)
   */
  DataSource getPrimary();

  /**
   * Gets the DataSource used for message publishing operations. This DataSource is used during scheduled publishing tasks. Returns the same
   * as capture DataSource if no dedicated publishing DataSource is configured.
   *
   * @return the publishing DataSource (never null)
   */
  DataSource getDedicatedForPublishing();

  /**
   * Gets the metadata for the capture DataSource.
   *
   * @return metadata about the capture DataSource
   */
  DataSourceMetadata getPrimaryDataSourceMetadata();

  /**
   * Gets the metadata for the publishing DataSource.
   *
   * @return metadata about the publishing DataSource
   */
  DataSourceMetadata getDedicatedForPublishingDataSourceMetadata();
}
