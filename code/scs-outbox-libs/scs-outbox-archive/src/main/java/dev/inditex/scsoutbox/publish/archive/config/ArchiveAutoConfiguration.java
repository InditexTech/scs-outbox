package dev.inditex.scsoutbox.publish.archive.config;

import java.util.List;

import dev.inditex.scsoutbox.config.OutboxAutoConfiguration;
import dev.inditex.scsoutbox.publish.archive.ArchiveOutboxMessagePublisherInterceptor;
import dev.inditex.scsoutbox.publish.archive.ArchiveService;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageRepository;
import dev.inditex.scsoutbox.publish.archive.ArchivedMessageSerializer;
import dev.inditex.scsoutbox.publish.archive.json.AvroToJsonMapper;
import dev.inditex.scsoutbox.publish.archive.json.CompositeJsonMapper;
import dev.inditex.scsoutbox.publish.archive.json.DefaultJsonMapper;
import dev.inditex.scsoutbox.publish.archive.json.JsonMapper;
import dev.inditex.scsoutbox.serialization.HeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageReconverter;
import dev.inditex.scsoutbox.serialization.SerializationEngine;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.annotation.Bean;

@ConditionalOnProperty(value = "scs-outbox.publishing.archive.enabled", havingValue = "true", matchIfMissing = false)
@AutoConfiguration(after = {OutboxAutoConfiguration.class})
@EnableConfigurationProperties(ArchiveProperties.class)
public class ArchiveAutoConfiguration {

  @Bean
  public ArchivedMessageSerializer archivedMessageSerializer(
      final SerializationEngine serializationEngine,
      final HeadersMapper headersMapper) {
    return new ArchivedMessageSerializer(serializationEngine, headersMapper);
  }

  @Bean
  private ArchiveOutboxMessagePublisherInterceptor archiveOutboxMessagePublisherInterceptor(
      final ArchiveService archiveService) {
    return new ArchiveOutboxMessagePublisherInterceptor(archiveService);
  }

  @Bean
  public ArchiveService archiveService(
      final ArchivedMessageRepository archivedMessageRepository,
      final BindingServiceProperties bindingServiceProperties,
      final CompositeJsonMapper compositeJsonMapper,
      final ArchiveProperties properties,
      final OutboxMessageReconverter outboxMessageReconverter) {
    return new ArchiveService(
        archivedMessageRepository, bindingServiceProperties, compositeJsonMapper, properties, outboxMessageReconverter);
  }

  @SuppressWarnings("rawtypes")
  @Bean
  public JsonMapper avroToJsonMapper() {
    return new AvroToJsonMapper();
  }

  @Bean
  CompositeJsonMapper compositeJsonMapper(final List<JsonMapper<?>> mappers) {
    return new CompositeJsonMapper(
        new DefaultJsonMapper(tools.jackson.databind.json.JsonMapper.builder().build()),
        mappers);
  }

}
