package dev.inditex.scsoutbox.metrics.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.metrics.MessagesPendingMeter;
import dev.inditex.scsoutbox.metrics.PublishingDelayMeter;
import dev.inditex.scsoutbox.metrics.PublishingTaskMeter;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxMetricsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(OutboxMetricsAutoConfiguration.class));

  private ApplicationContextRunner contextRunnerWithDependencies() {
    return this.contextRunner
        .withPropertyValues("scs-outbox.metrics.enabled=true")
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withBean(OutboxMessageRepository.class, () -> mock(OutboxMessageRepository.class));
  }

  @Nested
  class MetricsBeans {

    @Test
    void when_metrics_enabled_with_meter_registry_expect_all_meters_created() {
      OutboxMetricsAutoConfigurationTest.this.contextRunnerWithDependencies()
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TimedAspect.class);
            assertThat(context).hasSingleBean(MessagesPendingMeter.class);
            assertThat(context).hasSingleBean(PublishingDelayMeter.class);
            assertThat(context).hasSingleBean(PublishingTaskMeter.class);
          });
    }

    @Test
    void when_metrics_disabled_expect_autoconfiguration_skipped() {
      OutboxMetricsAutoConfigurationTest.this.contextRunner
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MessagesPendingMeter.class);
          });
    }

    @Test
    void when_meter_registry_absent_expect_autoconfiguration_skipped() {
      OutboxMetricsAutoConfigurationTest.this.contextRunner
          .withPropertyValues("scs-outbox.metrics.enabled=true")
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MessagesPendingMeter.class);
          });
    }
  }
}
