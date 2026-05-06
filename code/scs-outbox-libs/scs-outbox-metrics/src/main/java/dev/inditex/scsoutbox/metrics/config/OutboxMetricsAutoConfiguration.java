package dev.inditex.scsoutbox.metrics.config;

import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.metrics.MessagesPendingMeter;
import dev.inditex.scsoutbox.metrics.PublishingDelayMeter;
import dev.inditex.scsoutbox.metrics.PublishingTaskMeter;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(value = "scs-outbox.metrics.enabled", havingValue = "true", matchIfMissing = false)
public class OutboxMetricsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public TimedAspect timedAspect(final MeterRegistry meterRegistry) {
    return new TimedAspect(meterRegistry);
  }

  @Bean
  public PublishingDelayMeter publishingDelayMeter(final MeterRegistry meterRegistry) {
    return new PublishingDelayMeter(meterRegistry);
  }

  @Bean
  public MessagesPendingMeter messagesPendingMeter(final MeterRegistry meterRegistry,
      final OutboxMessageRepository outboxMessageRepository) {
    return new MessagesPendingMeter(meterRegistry, outboxMessageRepository);
  }

  @Bean
  public PublishingTaskMeter publishingTaskMeter(final MeterRegistry meterRegistry) {
    return new PublishingTaskMeter(meterRegistry);
  }

}
