package dev.inditex.scsoutbox.metrics;

import dev.inditex.scsoutbox.OutboxMessage;

import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;

/**
 * Counts exceptions thrown by {@code OutboxMessagePublisherInterceptor.postSend(..)} implementations (e.g. the archive interceptor). These
 * interceptors run as best-effort side effects after a message has already been published to the broker: {@code
 * OutboxMessagePublisher} catches and logs any exception they throw, so this counter is the only metric-based signal for that kind of
 * failure.
 */
@Aspect
public class PostSendErrorsMeter {

  private static final String POSTSEND_ERRORS_METRIC = "outbox.publishing.postsend.errors";

  private static final String INTERCEPTOR_TAG = "interceptor";

  private static final String DESTINATION_TAG = "destination";

  private final MeterRegistry meterRegistry;

  public PostSendErrorsMeter(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @AfterThrowing(
      value = "execution(* dev.inditex.scsoutbox.publish.OutboxMessagePublisherInterceptor+.postSend(..)) && args(message)",
      argNames = "joinPoint,message")
  public void countPostSendError(final JoinPoint joinPoint, final OutboxMessage message) {
    this.meterRegistry.counter(
        POSTSEND_ERRORS_METRIC,
        INTERCEPTOR_TAG, joinPoint.getTarget().getClass().getName(),
        DESTINATION_TAG, message.getDestination())
        .increment();
  }

}
