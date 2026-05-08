package dev.inditex.scsoutbox.jdbc.config;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.config.OutboxAutoConfiguration;
import dev.inditex.scsoutbox.jdbc.JdbcOutboxDataSourceProvider;
import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for JDBC DataSource selection used by outbox capture and publishing operations.
 */
@Slf4j
@AutoConfiguration(before = OutboxAutoConfiguration.class)
public class JdbcDataSourceAutoConfiguration {

  /**
   * Bean name for the dedicated DataSource used for outbox publishing operations. If a bean with this name exists, it will be used for
   * publishing and archive operations.
   */
  public static final String OUTBOX_PUBLISHING_DATASOURCE_BEAN_NAME = "outboxPublishingDataSource";

  /**
   * Creates the DataSource provider that coordinates which DataSource to use for each operation. The publishing DataSource is optional - if
   * not present, the primary DataSource is used for both operations.
   *
   * @param captureDataSource the primary DataSource (always present)
   * @param publishingDataSource optional dedicated DataSource for publishing operations
   * @return the provider that resolves the capture and publishing DataSources
   */
  @Bean
  public OutboxDataSourceProvider outboxDataSourceProvider(
      final DataSource captureDataSource,
      @Qualifier(OUTBOX_PUBLISHING_DATASOURCE_BEAN_NAME) @Autowired(required = false) final DataSource publishingDataSource) {
    return new JdbcOutboxDataSourceProvider(captureDataSource, publishingDataSource);
  }

}
