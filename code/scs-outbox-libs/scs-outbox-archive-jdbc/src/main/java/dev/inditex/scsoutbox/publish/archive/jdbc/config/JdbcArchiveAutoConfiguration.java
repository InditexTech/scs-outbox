package dev.inditex.scsoutbox.publish.archive.jdbc.config;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.config.OutboxAutoConfiguration;
import dev.inditex.scsoutbox.jdbc.DataSourceMetadata;
import dev.inditex.scsoutbox.jdbc.DbSchemaResolver;
import dev.inditex.scsoutbox.jdbc.OutboxDataSourceProvider;
import dev.inditex.scsoutbox.jdbc.SchemaName;
import dev.inditex.scsoutbox.jdbc.Table;
import dev.inditex.scsoutbox.jdbc.TableName;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.config.ArchiveAutoConfiguration;
import dev.inditex.scsoutbox.publish.archive.jdbc.JdbcArchivedMessageRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for JDBC-based archived message repository. The archive repository uses the same DataSource as publishing operations
 * since archiving only happens during publishing phase.
 */
@Slf4j
@ConditionalOnBean(ArchiveAutoConfiguration.class)
@AutoConfiguration(after = OutboxAutoConfiguration.class)
@EnableConfigurationProperties(JdbcArchiveProperties.class)
public class JdbcArchiveAutoConfiguration {

  /**
   * Creates the archived message repository using the publishing DataSource from the provider. Archive operations always use the publishing
   * DataSource since archiving happens during publishing tasks.
   *
   * @param dataSourceProvider the provider that coordinates DataSource usage
   * @param archivedMessageSerializer the serializer for archived messages
   * @param properties archive configuration properties
   * @return the archived message repository instance
   */
  @Bean
  public ArchivedMessageRepository archivedMessageRepository(
      final OutboxDataSourceProvider dataSourceProvider,
      final ArchivedMessageSerializer archivedMessageSerializer,
      final JdbcArchiveProperties properties) {

    final DataSource dataSource = dataSourceProvider.getDedicatedForPublishing();
    final DataSourceMetadata metadata = dataSourceProvider.getDedicatedForPublishingDataSourceMetadata();
    final DbSchemaResolver dbSchemaResolver = new DbSchemaResolver(metadata);
    final String schema = dbSchemaResolver.resolve(properties.getSchema());
    final Table table = new Table(new SchemaName(schema), new TableName(properties.getTableName()));

    log.info("Creating archived message repository for table: {}", table.getQualifiedTableName());

    return new JdbcArchivedMessageRepository(
        new JdbcTemplate(dataSource),
        archivedMessageSerializer,
        table);
  }

}
