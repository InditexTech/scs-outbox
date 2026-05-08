package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    classes = {ScsOutboxJdbcIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.metrics.enabled=true",
        "scs-outbox.publishing.batch-size=1",
        "scs-outbox.publishing.after-commit=false",
        "scs-outbox.publishing.scheduler.fixed-rate=1000",
    })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class ScsOutboxJdbcIT {

  @Configuration
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  @Slf4j
  static class TestConfig {
    @Bean
    public Consumer<String> myConsumer() {
      return value -> log.info("message received: {}", value);
    }

    @Bean
    public MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Autowired
  private StreamBridge streamBridge;

  @MockitoBean
  private Consumer<String> myConsumer;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private MeterRegistry meterRegistry;

  @Test
  void sending_a_message_outside_a_transaction() {
    final MessageDeliveryException messageDeliveryException = assertThrows(
        MessageDeliveryException.class,
        () -> this.streamBridge.send("output", "key"));

    assertInstanceOf(IllegalTransactionStateException.class, messageDeliveryException.getCause());

  }

  @Test
  void sending_a_message_within_a_transaction() {

    final Boolean sent = this.transactionTemplate.execute(status -> {
      return this.streamBridge.send("output", "transaction");
    });

    assertFalse(sent);
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> verify(this.myConsumer).accept("transaction"));
  }

  @Test
  void messages_pending_metric() {
    final int maxNumOfMessages = 10;
    for (int i = 0; i < maxNumOfMessages; i++) {
      this.transactionTemplate.execute(status -> {
        return this.streamBridge.send("output", "key");
      });
    }
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> assertThat(this.meterRegistry.get("outbox.messages.pending").gauge().value())
                .isGreaterThan(0).isLessThan(maxNumOfMessages));
  }
}
