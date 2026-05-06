package dev.inditex.scsoutbox.config;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.inditex.scsoutbox.MessageCaptureTxService;
import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.OutboxServiceProperties;
import dev.inditex.scsoutbox.interceptor.MessageChannelAccessor;
import dev.inditex.scsoutbox.interceptor.OutboxChannelInterceptor;
import dev.inditex.scsoutbox.publish.DestinationGroupingKeyGenerator;
import dev.inditex.scsoutbox.publish.GroupingKeyGenerator;
import dev.inditex.scsoutbox.publish.GroupingStrategy;
import dev.inditex.scsoutbox.publish.KafkaKeyGroupingKeyGenerator;
import dev.inditex.scsoutbox.publish.KeyGroupingStrategy;
import dev.inditex.scsoutbox.publish.OutboxMessageConverter;
import dev.inditex.scsoutbox.publish.OutboxMessagePublisher;
import dev.inditex.scsoutbox.publish.OutboxMessagePublisherInterceptor;
import dev.inditex.scsoutbox.publish.OutboxMessageSender;
import dev.inditex.scsoutbox.publish.OutboxPublishingTask;
import dev.inditex.scsoutbox.publish.ParallelPublisher;
import dev.inditex.scsoutbox.publish.StreamBridgeOutboxMessageSender;
import dev.inditex.scsoutbox.publish.config.PublishingProperties;
import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger;
import dev.inditex.scsoutbox.scheduler.OutboxScheduledService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.config.GlobalChannelInterceptor;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({OutboxProperties.class, PublishingProperties.class})
public class OutboxAutoConfiguration {

  /**
   * Bean name for the repository used for message capture during application transactions.
   */
  public static final String OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME = "outboxMessageRepository";

  /**
   * Bean name for the repository used for message publishing during scheduled tasks.
   */
  public static final String PUBLISHING_OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME = "publishingOutboxMessageRepository";

  private static final String OUTBOX_EXECUTOR_SERVICE_BEAN_NAME = "outboxExecutorService";

  private static final String DEFAULT_OUTBOX_EXECUTOR_SERVICE_BEAN_NAME = "defaultOutboxExecutorService";

  @Bean
  @GlobalChannelInterceptor
  public OutboxChannelInterceptor outboxChannelInterceptor(
      final MessageCaptureTxService messageCaptureTxService,
      final MessageChannelAccessor messageChannelAccessor,
      final OutboxServiceProperties outboxServiceProperties) {
    return new OutboxChannelInterceptor(messageCaptureTxService, messageChannelAccessor, outboxServiceProperties);
  }

  @Bean
  public MessageChannelAccessor messageChannelAccessor(final @Value("${spring.application.name:}") String appName) {
    return new MessageChannelAccessor(appName);
  }

  @Bean
  public OutboxServiceProperties scsOutboxServiceProperties(
      final OutboxProperties outboxProperties,
      final BindingServiceProperties bindingServiceProperties) {
    return new OutboxServiceProperties(outboxProperties, bindingServiceProperties);
  }

  @Bean
  public MessageCaptureTxService messageCaptureTxService(
      @Qualifier(OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME) final OutboxMessageRepository repository,
      final OutboxServiceProperties outboxServiceProperties) {
    return new MessageCaptureTxService(
        repository, outboxServiceProperties);
  }

  @Bean
  @ConditionalOnProperty(value = "app.scheduling.enable", havingValue = "true", matchIfMissing = true)
  public OutboxScheduledService scsOutboxScheduledService(final OutboxPublishingTask outboxPublishingTask) {
    return new OutboxScheduledService(outboxPublishingTask);
  }

  @Bean
  public OutboxMessageSender outboxMessageSender(
      final BindingServiceProperties bindingServiceProperties,
      final StreamBridge streamBridge) {
    return new StreamBridgeOutboxMessageSender(streamBridge, bindingServiceProperties);
  }

  /**
   * Registers a custom {@link org.springframework.messaging.converter.MessageConverter} that converts an {@code OutboxMessage} with a raw
   * {@code byte[]} payload into a broker-ready message. The converter is exclusively targeted via a custom MIME type
   * ({@code application/x-scs-outbox-raw}) that no other SCS converter recognizes. It extracts the raw bytes and captured headers
   * (including the original content type) from the {@code OutboxMessage} so the binder sends the correct content type to the broker.
   */
  @Bean
  public OutboxMessageConverter outboxMessageConverter() {
    return new OutboxMessageConverter();
  }

  @Bean
  public OutboxMessagePublisher outboxMessagePublisher(
      final OutboxMessageSender messageSender,
      @Qualifier(PUBLISHING_OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME) final OutboxMessageRepository publishingRepository,
      final List<OutboxMessagePublisherInterceptor> interceptors) {
    return new OutboxMessagePublisher(messageSender, publishingRepository, interceptors);
  }

  @Bean
  public OutboxPublishingTask outboxPublishingTask(
      @Qualifier(PUBLISHING_OUTBOX_MESSAGE_REPOSITORY_BEAN_NAME) final OutboxMessageRepository publishingRepository,
      @Qualifier(OUTBOX_EXECUTOR_SERVICE_BEAN_NAME) final ExecutorService executorService,
      final OutboxMessagePublisher messagePublisher,
      final PublishingProperties publishingProperties,
      GroupingStrategy groupingStrategy) {
    final ParallelPublisher parallelPublisher = new ParallelPublisher(executorService, messagePublisher);
    return new OutboxPublishingTask(publishingRepository, parallelPublisher, groupingStrategy, publishingProperties);
  }

  @ConditionalOnProperty(value = "scs-outbox.publishing.after-commit", havingValue = "true", matchIfMissing = false)
  @Bean
  public AfterCommitTrigger afterCommitTrigger(
      final ApplicationEventPublisher applicationEventPublisher,
      final OutboxScheduledService scsOutboxScheduledService) {
    return new AfterCommitTrigger(applicationEventPublisher, scsOutboxScheduledService);
  }

  /**
   * Default executor service.
   */
  @ConditionalOnMissingBean
  @Bean(name = {DEFAULT_OUTBOX_EXECUTOR_SERVICE_BEAN_NAME, OUTBOX_EXECUTOR_SERVICE_BEAN_NAME})
  public ExecutorService defaultOutboxExecutorService() {
    return Executors.newCachedThreadPool();
  }

  @ConditionalOnMissingBean(name = {OUTBOX_EXECUTOR_SERVICE_BEAN_NAME})
  @Bean(OUTBOX_EXECUTOR_SERVICE_BEAN_NAME)
  public ExecutorService outboxExecutorService(
      ExecutorService candidateExecutorServices) {
    return candidateExecutorServices;
  }

  @Bean
  public GroupingStrategy groupingStrategy(
      PublishingProperties publishingProperties,
      @Autowired(required = false) GroupingKeyGenerator customGroupingKeyGenerator) {

    final GroupingKeyGenerator groupingKeyGenerator = switch (publishingProperties.getGroupingStrategy()) {
      case DESTINATION -> new DestinationGroupingKeyGenerator();
      case KAFKA_MESSAGE_KEY -> new KafkaKeyGroupingKeyGenerator();
      case CUSTOM_GROUPING_KEY -> {
        Objects.requireNonNull(customGroupingKeyGenerator,
            "GroupingKeyGenerator bean must be provided when using CUSTOM_GROUPING_KEY");
        yield customGroupingKeyGenerator;
      }
    };
    log.info("Using {} as grouping strategy", groupingKeyGenerator.getClass().getSimpleName());
    return new KeyGroupingStrategy(groupingKeyGenerator);
  }

}
