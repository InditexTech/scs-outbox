package dev.inditex.scsoutbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ExecutorService;

import dev.inditex.scsoutbox.MessageCaptureTxService;
import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.OutboxServiceProperties;
import dev.inditex.scsoutbox.interceptor.MessageChannelAccessor;
import dev.inditex.scsoutbox.interceptor.OutboxChannelInterceptor;
import dev.inditex.scsoutbox.publish.DestinationGroupingKeyGenerator;
import dev.inditex.scsoutbox.publish.GroupingKeyGenerator;
import dev.inditex.scsoutbox.publish.KafkaKeyGroupingKeyGenerator;
import dev.inditex.scsoutbox.publish.KeyGroupingStrategy;
import dev.inditex.scsoutbox.publish.OutboxMessagePublisher;
import dev.inditex.scsoutbox.publish.OutboxMessageSender;
import dev.inditex.scsoutbox.publish.OutboxPublishingTask;
import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger;
import dev.inditex.scsoutbox.scheduler.OutboxScheduledService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.CompositeMessageConverter;

class OutboxAutoConfigurationTest {

  private final ApplicationContextRunner baseContextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class, RefreshAutoConfiguration.class))
      .withBean("outboxMessageRepository", OutboxMessageRepository.class, () -> mock(OutboxMessageRepository.class))
      .withBean("publishingOutboxMessageRepository", OutboxMessageRepository.class, () -> mock(OutboxMessageRepository.class))
      .withBean(BindingServiceProperties.class, () -> mock(BindingServiceProperties.class))
      .withBean(CompositeMessageConverter.class, () -> mock(CompositeMessageConverter.class))
      .withBean(StreamBridge.class, () -> mock(StreamBridge.class));

  @Nested
  @DisplayName("groupingStrategy is CUSTOM_GROUPING_KEY")
  class CustomGroupingKey {

    private final ApplicationContextRunner contextRunner = OutboxAutoConfigurationTest.this.baseContextRunner
        .withPropertyValues("scs-outbox.publishing.grouping-strategy=CUSTOM_GROUPING_KEY");

    @Test
    @DisplayName("fails context loading when no custom generator is provided")
    void fails_context_loading_when_custom_grouping_key_generator_missing() {
      this.contextRunner
          .run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(NullPointerException.class)
                .hasMessageContaining("GroupingKeyGenerator bean must be provided when using CUSTOM_GROUPING_KEY");
          });
    }

    @Test
    @DisplayName("does not fail context loading when custom GroupingKeyGenerator is provided")
    void does_not_fail_when_custom_grouping_key_generator_provided() {
      final GroupingKeyGenerator customGenerator = values -> null;

      this.contextRunner
          .withBean(GroupingKeyGenerator.class, () -> customGenerator)
          .run(context -> {
            assertThat(context).hasNotFailed();
          });
    }
  }

  @Nested
  @DisplayName("groupingStrategy is DESTINATION")
  class DestinationGroupingKey {

    private final ApplicationContextRunner contextRunner = OutboxAutoConfigurationTest.this.baseContextRunner
        .withPropertyValues("scs-outbox.publishing.grouping-strategy=DESTINATION");

    @Test
    @DisplayName("creates DestinationGroupingKeyGenerator when using DESTINATION")
    void creates_destination_grouping_key_generator() {
      this.contextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KeyGroupingStrategy.class);
            final KeyGroupingStrategy strategy = context.getBean(KeyGroupingStrategy.class);
            assertThat(strategy).extracting("groupingKeyGenerator")
                .isInstanceOf(DestinationGroupingKeyGenerator.class);
          });
    }
  }

  @Nested
  @DisplayName("groupingStrategy is KAFKA_MESSAGE_KEY")
  class KafkaMessageKeyGrouping {

    private final ApplicationContextRunner contextRunner = OutboxAutoConfigurationTest.this.baseContextRunner
        .withPropertyValues("scs-outbox.publishing.grouping-strategy=KAFKA_MESSAGE_KEY");

    @Test
    @DisplayName("creates KafkaKeyGroupingKeyGenerator when using KAFKA_MESSAGE_KEY")
    void creates_kafka_key_grouping_key_generator() {
      this.contextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KeyGroupingStrategy.class);
            final KeyGroupingStrategy strategy = context.getBean(KeyGroupingStrategy.class);
            assertThat(strategy).extracting("groupingKeyGenerator")
                .isInstanceOf(KafkaKeyGroupingKeyGenerator.class);
          });
    }
  }

  @Nested
  @DisplayName("OutboxScheduledService configuration")
  class ScheduledServiceConfiguration {

    @Test
    @DisplayName("creates OutboxScheduledService when scheduling is enabled")
    void creates_scheduled_service_when_scheduling_enabled() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .withPropertyValues("app.scheduling.enable=true")
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxScheduledService.class);
          });
    }

    @Test
    @DisplayName("creates OutboxScheduledService when scheduling property is missing")
    void creates_scheduled_service_when_scheduling_property_missing() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxScheduledService.class);
          });
    }

    @Test
    @DisplayName("does not create OutboxScheduledService when scheduling is disabled")
    void does_not_create_scheduled_service_when_scheduling_disabled() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .withPropertyValues("app.scheduling.enable=false")
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OutboxScheduledService.class);
          });
    }
  }

  @Nested
  @DisplayName("AfterCommitTrigger configuration")
  class AfterCommitTriggerConfiguration {

    @Test
    @DisplayName("creates AfterCommitTrigger when after-commit is enabled")
    void creates_after_commit_trigger_when_enabled() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .withBean(ApplicationEventPublisher.class, () -> mock(ApplicationEventPublisher.class))
          .withPropertyValues("scs-outbox.publishing.after-commit=true")
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AfterCommitTrigger.class);
          });
    }

    @Test
    @DisplayName("does not create AfterCommitTrigger when after-commit is disabled")
    void does_not_create_after_commit_trigger_when_disabled() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .withBean(ApplicationEventPublisher.class, () -> mock(ApplicationEventPublisher.class))
          .withPropertyValues("scs-outbox.publishing.after-commit=false")
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AfterCommitTrigger.class);
          });
    }

    @Test
    @DisplayName("does not create AfterCommitTrigger when property is missing")
    void does_not_create_after_commit_trigger_when_property_missing() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .withBean(ApplicationEventPublisher.class, () -> mock(ApplicationEventPublisher.class))
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AfterCommitTrigger.class);
          });
    }
  }

  @Nested
  @DisplayName("ExecutorService configuration")
  class ExecutorServiceConfiguration {

    @Test
    @DisplayName("creates default executor service when no custom executor is provided")
    void creates_default_executor_service_when_no_custom_executor() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ExecutorService.class);
            assertThat(context).hasBean("defaultOutboxExecutorService");
            assertThat(context).hasBean("outboxExecutorService");
          });
    }

    @Test
    @DisplayName("uses custom executor service when provided")
    void uses_custom_executor_service_when_provided() {
      final ExecutorService customExecutor = mock(ExecutorService.class);

      OutboxAutoConfigurationTest.this.baseContextRunner
          .withBean("outboxExecutorService", ExecutorService.class, () -> customExecutor)
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean("outboxExecutorService").isSameAs(customExecutor);
            assertThat(context).doesNotHaveBean("defaultOutboxExecutorService");
          });
    }
  }

  @Nested
  @DisplayName("Core beans configuration")
  class CoreBeansConfiguration {

    @Test
    @DisplayName("creates all required core beans")
    void creates_all_required_core_beans() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .withPropertyValues("spring.application.name=test-app")
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxChannelInterceptor.class);
            assertThat(context).hasSingleBean(MessageChannelAccessor.class);
            assertThat(context).hasSingleBean(OutboxServiceProperties.class);
            assertThat(context).hasSingleBean(MessageCaptureTxService.class);
            assertThat(context).hasSingleBean(OutboxMessageSender.class);
            assertThat(context).hasSingleBean(OutboxMessagePublisher.class);
            assertThat(context).hasSingleBean(OutboxPublishingTask.class);
          });
    }

    @Test
    @DisplayName("creates MessageChannelAccessor with application name")
    void creates_message_channel_accessor_with_application_name() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .withPropertyValues("spring.application.name=my-test-app")
          .run(context -> {
            assertThat(context).hasNotFailed();
            final MessageChannelAccessor accessor = context.getBean(MessageChannelAccessor.class);
            assertThat(accessor).extracting("appName").isEqualTo("my-test-app");
          });
    }

    @Test
    @DisplayName("creates MessageChannelAccessor with empty name when application name is missing")
    void creates_message_channel_accessor_with_empty_name_when_missing() {
      OutboxAutoConfigurationTest.this.baseContextRunner
          .run(context -> {
            assertThat(context).hasNotFailed();
            final MessageChannelAccessor accessor = context.getBean(MessageChannelAccessor.class);
            assertThat(accessor).extracting("appName").isEqualTo("");
          });
    }
  }
}
