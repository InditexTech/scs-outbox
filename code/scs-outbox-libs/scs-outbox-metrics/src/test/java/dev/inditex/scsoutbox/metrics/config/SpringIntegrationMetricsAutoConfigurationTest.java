package dev.inditex.scsoutbox.metrics.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.inditex.scsoutbox.OutboxServiceProperties;
import dev.inditex.scsoutbox.metrics.SpringIntegrationMeterFilter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpringIntegrationMetricsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SpringIntegrationMetricsAutoConfiguration.class));

  @Nested
  class SpringIntegrationMeterFilterBean {

    @Test
    void when_meter_registry_present_expect_filter_created() {
      SpringIntegrationMetricsAutoConfigurationTest.this.contextRunner
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(OutboxServiceProperties.class, () -> mock(OutboxServiceProperties.class))
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SpringIntegrationMeterFilter.class);
          });
    }

    @Test
    void when_meter_registry_absent_expect_autoconfiguration_skipped() {
      SpringIntegrationMetricsAutoConfigurationTest.this.contextRunner
          .withBean(OutboxServiceProperties.class, () -> mock(OutboxServiceProperties.class))
          .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SpringIntegrationMeterFilter.class);
          });
    }
  }
}
