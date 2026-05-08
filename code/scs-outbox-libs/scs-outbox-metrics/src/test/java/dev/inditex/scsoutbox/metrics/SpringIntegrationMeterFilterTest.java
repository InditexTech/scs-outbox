package dev.inditex.scsoutbox.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.inditex.scsoutbox.OutboxServiceProperties;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.support.management.IntegrationManagement;

class SpringIntegrationMeterFilterTest {

  public static final String RESULT_TAG_NAME = "result";

  public static final String EXCEPTION_TAG_NAME = "exception";

  public static final String NONE = "none";

  public static final String CHANNEL_NAME_TAG_NAME = "name";

  public static final String FAILURE = "failure";

  public static final String SUCCESS = "success";

  private static final String CHANNEL_NAME = "test-channel";

  private SimpleMeterRegistry meterRegistry;

  private OutboxServiceProperties outboxServiceProperties;

  @BeforeEach
  void setUp() {
    this.outboxServiceProperties = mock(OutboxServiceProperties.class);
    final SpringIntegrationMeterFilter springIntegrationMeterFilter =
        new SpringIntegrationMeterFilter(this.outboxServiceProperties);
    this.meterRegistry = new SimpleMeterRegistry();
    this.meterRegistry.config().meterFilter(springIntegrationMeterFilter);
  }

  private void enableOutboxForChannel(final String channel) {
    when(this.outboxServiceProperties.isOutboxEnabledFor(channel)).thenReturn(true);
  }

  private void disableOutboxForChannel(final String channel) {
    when(this.outboxServiceProperties.isOutboxEnabledFor(channel)).thenReturn(false);
  }

  @Test
  void when_outbox_is_enabled_for_channel_and_result_is_failure_and_exception_is_none_then_result_is_replace_by_success() {
    this.enableOutboxForChannel(CHANNEL_NAME);
    this.createMeterWith(
        IntegrationManagement.SEND_TIMER_NAME,
        CHANNEL_NAME,
        FAILURE,
        NONE);

    final Id meterId = this.meterRegistry.get(IntegrationManagement.SEND_TIMER_NAME).timer().getId();

    assertEquals(SUCCESS, meterId.getTag(RESULT_TAG_NAME));
  }

  @Test
  void when_outbox_is_enabled_for_channel_and_result_is_failure_and_exception_is_not_none_then_result_continues_to_be_failure() {
    this.enableOutboxForChannel(CHANNEL_NAME);
    this.createMeterWith(
        IntegrationManagement.SEND_TIMER_NAME,
        CHANNEL_NAME,
        FAILURE,
        Exception.class.getName());

    final Id meterId = this.meterRegistry.get(IntegrationManagement.SEND_TIMER_NAME).timer().getId();

    assertEquals(FAILURE, meterId.getTag(RESULT_TAG_NAME));
  }

  @Test
  void when_outbox_is_disabled_for_channel_then_result_is_not_replaced() {
    this.disableOutboxForChannel(CHANNEL_NAME);
    this.createMeterWith(
        IntegrationManagement.SEND_TIMER_NAME,
        CHANNEL_NAME,
        SUCCESS,
        NONE);

    final Id meterId = this.meterRegistry.get(IntegrationManagement.SEND_TIMER_NAME).timer().getId();

    assertEquals(SUCCESS, meterId.getTag(RESULT_TAG_NAME));
  }

  @Test
  void when_result_is_not_failure_then_result_is_not_replaced() {
    this.enableOutboxForChannel(CHANNEL_NAME);
    final String resultValue = SUCCESS;
    this.createMeterWith(
        IntegrationManagement.SEND_TIMER_NAME,
        CHANNEL_NAME,
        resultValue,
        NONE);

    final Id meterId = this.meterRegistry.get(IntegrationManagement.SEND_TIMER_NAME).timer().getId();

    assertEquals(resultValue, meterId.getTag(RESULT_TAG_NAME));
  }

  @Test
  void when_metric_name_is_not_spring_integration_send_then_result_is_not_replaced() {
    this.enableOutboxForChannel(CHANNEL_NAME);
    final String metricName = "another-metric-name";
    final String resultValue = FAILURE;
    this.createMeterWith(
        metricName,
        CHANNEL_NAME,
        resultValue,
        NONE);

    final Id meterId = this.meterRegistry.get(metricName).timer().getId();

    assertEquals(resultValue, meterId.getTag(RESULT_TAG_NAME));
  }

  private void createMeterWith(final String meterName, final String channelName, final String result, final String exception) {
    this.meterRegistry.timer(
        meterName,
        CHANNEL_NAME_TAG_NAME, channelName,
        RESULT_TAG_NAME, result,
        EXCEPTION_TAG_NAME, exception);
  }
}
