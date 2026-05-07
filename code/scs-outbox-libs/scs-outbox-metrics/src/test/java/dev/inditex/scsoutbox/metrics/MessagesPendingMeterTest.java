package dev.inditex.scsoutbox.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.publish.OutboxPublishingTask;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class MessagesPendingMeterTest {

  public static final long NUM_OF_MESSAGES_IN_REPOSITORY = 2L;

  private SimpleMeterRegistry meterRegistry;

  private OutboxPublishingTask outboxPublishingTask;

  @BeforeEach
  void setUp() {
    this.meterRegistry = new SimpleMeterRegistry();
    final OutboxMessageRepository outboxMessageRepository = mock(OutboxMessageRepository.class);
    when(outboxMessageRepository.estimatedCount()).thenReturn(NUM_OF_MESSAGES_IN_REPOSITORY);
    final MessagesPendingMeter messagesPendingMeter = new MessagesPendingMeter(this.meterRegistry, outboxMessageRepository);
    final OutboxPublishingTask publishingTask = mock(OutboxPublishingTask.class);
    final AspectJProxyFactory factory = new AspectJProxyFactory(publishingTask);
    factory.addAspect(messagesPendingMeter);
    this.outboxPublishingTask = factory.getProxy();
  }

  @Test
  void update_metric_when_count_is_called() {
    assertThat(
        this.meterRegistry.get("outbox.messages.pending").gauge().value())
            .isZero();

    this.outboxPublishingTask.run();

    assertThat(
        this.meterRegistry.get("outbox.messages.pending").gauge().value())
            .isEqualTo(NUM_OF_MESSAGES_IN_REPOSITORY);
  }

  @Test
  void messages_pending_value_must_be_equal_to_the_number_of_messages_returned() {

    this.outboxPublishingTask.run();

    assertThat(
        this.meterRegistry.get("outbox.messages.pending").gauge().value())
            .isEqualTo(NUM_OF_MESSAGES_IN_REPOSITORY);
  }
}
