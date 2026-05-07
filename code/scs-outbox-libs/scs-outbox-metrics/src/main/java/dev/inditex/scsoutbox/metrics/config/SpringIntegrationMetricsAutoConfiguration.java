package dev.inditex.scsoutbox.metrics.config;

import dev.inditex.scsoutbox.OutboxServiceProperties;
import dev.inditex.scsoutbox.metrics.SpringIntegrationMeterFilter;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(MeterRegistry.class)
public class SpringIntegrationMetricsAutoConfiguration {

  @Bean
  public SpringIntegrationMeterFilter springIntegrationMeterFilter(
      final OutboxServiceProperties outboxServiceProperties) {
    return new SpringIntegrationMeterFilter(outboxServiceProperties);
  }

}
