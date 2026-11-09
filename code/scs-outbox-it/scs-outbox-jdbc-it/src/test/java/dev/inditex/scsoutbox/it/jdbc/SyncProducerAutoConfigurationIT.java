package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import dev.inditex.scsoutbox.OutboxMessageRepository;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test validating that scs-outbox automatically switches the Kafka producer of every outbox-enabled binding into synchronous
 * mode, and that it leaves bindings excluded from the outbox untouched.
 *
 * <p>The assertion is made against the {@link KafkaProducerProperties} actually resolved by the Kafka binder, which is the object the
 * binder uses when it creates the producer binding.
 *
 * <p>Synchronous publishing is what makes the outbox safe: {@code OutboxMessagePublisher} deletes the outbox record as soon as
 * {@code StreamBridge.send} returns {@code true}, which for an asynchronous producer happens before the broker acknowledges the record.
 */
@SpringBootTest(
    classes = {SyncProducerAutoConfigurationIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "spring.cloud.function.definition=myConsumer",
        "spring.cloud.stream.bindings.myConsumer-in-0.destination=outbox",
        "spring.cloud.stream.bindings.myConsumer-in-0.group=outbox",
        "spring.cloud.stream.bindings.excluded-out-0.destination=excluded-destination",
        "scs-outbox.bindings.inclusions=",
        "scs-outbox.bindings.exclusions=myConsumer-in-0,excluded-out-0",
        "scs-outbox.publishing.scheduler.fixed-rate=1000",
    })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class SyncProducerAutoConfigurationIT {

  private static final String OUTBOX_BINDING = "output";

  private static final String EXCLUDED_BINDING = "excluded-out-0";

  @Configuration
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  @Slf4j
  static class TestConfig {

    static final AtomicInteger RECEIVED = new AtomicInteger();

    @Bean
    public Consumer<String> myConsumer() {
      return message -> {
        log.info("received [{}]", message);
        RECEIVED.incrementAndGet();
      };
    }
  }

  @Autowired
  private BinderFactory binderFactory;

  @Autowired
  private StreamBridge streamBridge;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  @Qualifier("outboxMessageRepository")
  private OutboxMessageRepository outboxMessageRepository;

  private KafkaProducerProperties resolvedProducerProperties(final String bindingName) {
    final Binder<MessageChannel, ?, ?> binder = this.binderFactory.getBinder(null, MessageChannel.class);
    assertThat(binder).isInstanceOf(ExtendedPropertiesBinder.class);
    final Object producerProperties =
        ((ExtendedPropertiesBinder<?, ?, ?>) binder).getExtendedProducerProperties(bindingName);
    assertThat(producerProperties).isInstanceOf(KafkaProducerProperties.class);
    return (KafkaProducerProperties) producerProperties;
  }

  @Test
  void when_binding_is_outbox_enabled_expect_kafka_producer_configured_as_synchronous() {
    assertThat(this.resolvedProducerProperties(OUTBOX_BINDING).isSync()).isTrue();
  }

  @Test
  void when_binding_is_excluded_from_the_outbox_expect_kafka_producer_left_asynchronous() {
    assertThat(this.resolvedProducerProperties(EXCLUDED_BINDING).isSync()).isFalse();
  }

  @Test
  void when_message_is_captured_expect_it_published_synchronously_and_removed_from_the_outbox() {
    final int alreadyReceived = TestConfig.RECEIVED.get();

    this.transactionTemplate.executeWithoutResult(status -> this.streamBridge.send(OUTBOX_BINDING, "sync-producer-payload"));

    await().atMost(30, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(TestConfig.RECEIVED.get()).isGreaterThan(alreadyReceived));
    await().atMost(30, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(this.outboxMessageRepository.count()).isZero());
  }
}
