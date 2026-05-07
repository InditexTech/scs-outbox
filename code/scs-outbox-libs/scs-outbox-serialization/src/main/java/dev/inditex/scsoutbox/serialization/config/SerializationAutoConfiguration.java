package dev.inditex.scsoutbox.serialization.config;

import dev.inditex.scsoutbox.OutboxServiceProperties;
import dev.inditex.scsoutbox.serialization.HeadersMapper;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageReconverter;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;
import dev.inditex.scsoutbox.serialization.SerializationEngine;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.CompositeMessageConverter;

@AutoConfiguration
public class SerializationAutoConfiguration {

  /**
   * Default serialization engine.
   */
  @ConditionalOnMissingBean
  @Bean
  public SerializationEngine serializationEngine() {
    return new JavaSerialization();
  }

  @ConditionalOnMissingBean
  @Bean
  public HeadersMapper headersMapper() {
    return new JsonHeadersMapper();
  }

  @Bean
  public OutboxMessageReconverter outboxMessageReconverter(
      OutboxServiceProperties outboxServiceProperties,
      CompositeMessageConverter compositeMessageConverter) {
    return new OutboxMessageReconverter(outboxServiceProperties, compositeMessageConverter);
  }

  @Bean
  public OutboxMessageSerializer outboxMessageSerializer(
      SerializationEngine serializationEngine,
      HeadersMapper headerMapper) {
    return new OutboxMessageSerializer(serializationEngine, headerMapper);
  }
}
