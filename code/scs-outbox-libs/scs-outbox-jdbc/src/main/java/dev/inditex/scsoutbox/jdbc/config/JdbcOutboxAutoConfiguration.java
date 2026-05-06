package dev.inditex.scsoutbox.jdbc.config;

import static dev.inditex.scsoutbox.config.OutboxAutoConfiguration.OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME;
import static dev.inditex.scsoutbox.config.OutboxAutoConfiguration.PUBLISHING_OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.config.OutboxAutoConfiguration;
import dev.inditex.scsoutbox.jdbc.DataSourceMetadata;
import dev.inditex.scsoutbox.jdbc.DbSchemaResolver;
import dev.inditex.scsoutbox.jdbc.JdbcOutboxMessageRepositoryFactory;
import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;
import dev.inditex.scsoutbox.jdbc.SchemaName;
import dev.inditex.scsoutbox.jdbc.Table;
import dev.inditex.scsoutbox.jdbc.TableName;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for JDBC Outbox. Sets up the necessary beans for JDBC-based outbox message repository.
 *
 * <p>This configuration uses an OutboxDataSourceProvider to coordinate DataSource usage and two qualified repositories:
 * outboxMessageRepository for message capture and publishingOutboxMessageRepository for message publishing.
 */
@Slf4j
@AutoConfiguration(before = OutboxAutoConfiguration.class, after = JdbcDataSourceAutoConfiguration.class)
@EnableConfigurationProperties({JdbcProperties.class})
public class JdbcOutboxAutoConfiguration {

  /**
   * Creates the OutboxMessageRepository for message capture using the capture DataSource from the provider. This repository is used during
   * application transactions to capture outbox messages.
   *
   * @param dataSourceProvider the provider that coordinates DataSource usage
   * @param properties JDBC configuration properties
   * @param outboxMessageSerializer serializer used to persist outbox payloads and headers
   * @return the capture repository instance
   */
  @Bean(OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME)
  public OutboxMessageRepository outboxMessageRepository(
      final OutboxDataSourceProvider dataSourceProvider,
      final JdbcProperties properties,
      final OutboxMessageSerializer outboxMessageSerializer) {

    final DataSource dataSource = dataSourceProvider.getPrimary();
    final DataSourceMetadata metadata = dataSourceProvider.getPrimaryDataSourceMetadata();
    final DbSchemaResolver dbSchemaResolver = new DbSchemaResolver(metadata);
    final String schemaName = dbSchemaResolver.resolve(properties.getSchema());
    final String tableName = properties.getTableName();
    final Table table = new Table(new SchemaName(schemaName), new TableName(tableName));

    log.info("Creating capture OutboxMessageRepository for table: {}", table.getQualifiedTableName());

    return JdbcOutboxMessageRepositoryFactory.create(
        new JdbcTemplate(dataSource),
        metadata,
        table,
        outboxMessageSerializer);
  }

  /**
   * Creates the OutboxMessageRepository for message publishing using the publishing DataSource from the provider. This repository is used
   * during scheduled tasks to publish outbox messages. It will use a dedicated DataSource if configured, otherwise it will use the primary
   * DataSource.
   *
   * @param dataSourceProvider the provider that coordinates DataSource usage
   * @param properties JDBC configuration properties
   * @param outboxMessageSerializer serializer used to persist outbox payloads and headers
   * @return the publishing repository instance
   */
  @Bean(PUBLISHING_OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME)
  public OutboxMessageRepository publishingOutboxMessageRepository(
      final OutboxDataSourceProvider dataSourceProvider,
      final JdbcProperties properties,
      final OutboxMessageSerializer outboxMessageSerializer) {

    final DataSource dataSource = dataSourceProvider.getDedicatedForPublishing();
    final DataSourceMetadata metadata = dataSourceProvider.getDedicatedForPublishingDataSourceMetadata();
    final DbSchemaResolver dbSchemaResolver = new DbSchemaResolver(metadata);
    final String schemaName = dbSchemaResolver.resolve(properties.getSchema());
    final String tableName = properties.getTableName();
    final Table table = new Table(new SchemaName(schemaName), new TableName(tableName));

    log.info("Creating publishing OutboxMessageRepository for table: {}", table.getQualifiedTableName());

    return JdbcOutboxMessageRepositoryFactory.create(
        new JdbcTemplate(dataSource),
        metadata,
        table,
        outboxMessageSerializer);
  }
}
