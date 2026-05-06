package dev.inditex.scsoutbox.it.mongo;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    classes = {ScsOutboxMongodbIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.batch-size=1",
        "scs-outbox.publishing.after-commit=false",
        "scs-outbox.publishing.scheduler.fixed-rate=2000",
    })
class ScsOutboxMongodbIT {

  @Autowired
  private StreamBridge streamBridge;

  @MockitoBean
  private Consumer<String> myConsumer;

  @Autowired
  private TransactionTemplate transactionTemplate;

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
      return this.streamBridge.send("output", "key");
    });

    assertFalse(sent);
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> verify(this.myConsumer).accept("key"));
  }

  @Configuration
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  @Slf4j
  static class TestConfig {
    @Bean
    public Consumer<String> myConsumer() {
      return value -> log.info("message received: " + value);
    }

    @Bean
    MongoTransactionManager transactionManager(final MongoDatabaseFactory dbFactory) {
      return new MongoTransactionManager(dbFactory);
    }
  }
}
