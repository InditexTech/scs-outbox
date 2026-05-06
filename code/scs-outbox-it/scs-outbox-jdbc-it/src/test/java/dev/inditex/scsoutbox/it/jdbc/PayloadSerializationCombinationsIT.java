package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration.OVERRIDE;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests that verify the outbox works correctly with combinations of
 * {@code spring.cloud.stream.default.producer.useNativeEncoding}.
 *
 * <p>The two combinations are: <table> <tr><th>useNativeEncoding</th><th>Nested class</th></tr>
 * <tr><td>false</td><td>{@link DefaultSerializationDefaultEncoding}</td></tr>
 * <tr><td>true</td><td>{@link DefaultSerializationNativeEncoding}</td></tr> </table>
 *
 * <p>When {@code useNativeEncoding=false} (default), SCS applies its {@code CompositeMessageConverter} pipeline on the producer side,
 * converting the payload to {@code byte[]} before the outbox interceptor captures it. The raw bytes are stored as-is.
 *
 * <p>When {@code useNativeEncoding=true}, SCS does not apply its converter pipeline. The outbox interceptor captures the payload as the
 * original application-level Object (e.g. {@code String}). On publishing, the deserialized payload is sent via the default
 * {@code StreamBridge} path, and a Kafka-native {@code StringSerializer} handles the wire format.
 */
class PayloadSerializationCombinationsIT {

  @Configuration
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  @Slf4j
  static class TestConfig {

    @Bean
    public MessageCollector messageCollector() {
      return new MessageCollector();
    }

    @Bean
    public Consumer<Message<byte[]>> myConsumer(final MessageCollector collector) {
      return message -> {
        final String decoded = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("Consumer received message: payload=[{}], contentType=[{}]",
            decoded, message.getHeaders().get("contentType"));
        collector.add(decoded, message.getHeaders());
      };
    }
  }

  @Getter
  static class MessageCollector {

    private final CopyOnWriteArrayList<ReceivedMessage> messages = new CopyOnWriteArrayList<>();

    void add(final String payload, final MessageHeaders headers) {
      this.messages.add(new ReceivedMessage(payload, headers));
    }

    void clear() {
      this.messages.clear();
    }
  }

  record ReceivedMessage(String payload, MessageHeaders headers) {

  }

  // ──────────────────────────────────────────────────────────────────────────
  // Combo: useNativeEncoding=false (default)
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Default SCS behaviour. SCS converts the payload to {@code byte[]} before the interceptor captures it. Since the payload is already
   * {@code byte[]}, it is stored as raw bytes (no serialization engine). On publishing, the raw {@code byte[]} payload is sent via the
   * {@code sendRaw} path using {@code OutboxMessageConverter}, which extracts the bytes and copies the original captured headers.
   */
  @Nested
  @NestedTestConfiguration(OVERRIDE)
  @SpringBootTest(
      classes = PayloadSerializationCombinationsIT.TestConfig.class,
      properties = {
          "spring.docker.compose.enabled=true",
          "spring.docker.compose.skip.in-tests=false",
          "scs-outbox.publishing.batch-size=10",
          "scs-outbox.publishing.after-commit=false",
          "scs-outbox.publishing.scheduler.fixed-rate=1000",
      })
  @DirtiesContext(classMode = ClassMode.AFTER_CLASS)
  class DefaultSerializationDefaultEncoding {

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MessageCollector messageCollector;

    @BeforeEach
    void beforeEach() {
      this.messageCollector.clear();
    }

    /**
     * A message sent within a transaction is captured as SCS-converted {@code byte[]}, stored as raw bytes, published via the
     * {@code sendRaw} path, and the consumer receives the correct payload.
     */
    @Test
    void when_default_settings_consumer_receives_correct_payload() {
      final String payload = "default-serialization-default-encoding-test";

      this.transactionTemplate.execute(status -> this.streamBridge.send("output", payload));

      await()
          .atMost(15, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            assertThat(this.messageCollector.getMessages()).hasSizeGreaterThanOrEqualTo(1);
            final ReceivedMessage received = this.messageCollector.getMessages().stream()
                .filter(m -> m.payload().equals(payload))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected message not found"));
            assertThat(received.payload()).isEqualTo(payload);
          });
    }

    /**
     * Validates that multiple messages sent with default settings are all received by the consumer.
     */
    @Test
    void when_default_settings_multiple_messages_are_all_received() {
      final int messageCount = 5;
      for (int i = 0; i < messageCount; i++) {
        final int index = i;
        this.transactionTemplate.execute(status -> this.streamBridge.send("output", "default-batch-" + index));
      }

      await()
          .atMost(30, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            final long batchMessages = this.messageCollector.getMessages().stream()
                .filter(m -> m.payload().startsWith("default-batch-"))
                .count();
            assertThat(batchMessages).isEqualTo(messageCount);
          });
    }

    /**
     * Validates that the consumer receives the original binding content type. This combo uses the {@code sendRaw} path where
     * {@code OutboxMessageConverter} copies the captured headers — this assertion proves that the original {@code contentType} is preserved
     * and not replaced by internal MIME types like {@code application/scs-outbox-message} or {@code application/octet-stream}.
     */
    @Test
    void when_default_settings_consumer_receives_original_content_type() {
      final String payload = "content-type-verification";

      this.transactionTemplate.execute(status -> this.streamBridge.send("output", payload));

      await()
          .atMost(15, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            assertThat(this.messageCollector.getMessages()).isNotEmpty();
            final ReceivedMessage received = this.messageCollector.getMessages().stream()
                .filter(m -> m.payload().equals(payload))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected message not found"));
            final Object contentType = received.headers().get("contentType");
            assertThat(contentType).isNotNull();
            assertThat(contentType.toString()).doesNotContain("octet-stream");
            assertThat(contentType.toString()).doesNotContain("scs-outbox-message");
          });
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Combo: useNativeEncoding=true
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * With native encoding, SCS does not convert the payload to {@code byte[]} before the interceptor captures it — the payload stays as the
   * original Object ({@code String}). Since the payload is not {@code byte[]}, the default {@code SerializationEngine}
   * ({@code JavaSerialization}) serializes it. On publishing, the deserialized payload ({@code String}) is sent via the default
   * {@code StreamBridge} path, and the Kafka {@code StringSerializer} handles the wire format.
   */
  @Nested
  @NestedTestConfiguration(OVERRIDE)
  @SpringBootTest(
      classes = PayloadSerializationCombinationsIT.TestConfig.class,
      properties = {
          "spring.docker.compose.enabled=true",
          "spring.docker.compose.skip.in-tests=false",
          "spring.cloud.stream.default.producer.useNativeEncoding=true",
          "spring.cloud.stream.kafka.binder.producer-properties[value.serializer]="
              + "org.apache.kafka.common.serialization.StringSerializer",
          "scs-outbox.publishing.batch-size=10",
          "scs-outbox.publishing.after-commit=false",
          "scs-outbox.publishing.scheduler.fixed-rate=1000",
      })
  @DirtiesContext(classMode = ClassMode.AFTER_CLASS)
  class DefaultSerializationNativeEncoding {

    @Autowired
    private StreamBridge streamBridge;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private MessageCollector messageCollector;

    @BeforeEach
    void beforeEach() {
      this.messageCollector.clear();
    }

    /**
     * A message sent within a transaction is captured, stored with {@code JavaSerialization}, published through the default
     * {@code StreamBridge} path, and the consumer receives the correct payload via Kafka's {@code StringSerializer}.
     */
    @Test
    void when_native_encoding_enabled_consumer_receives_correct_payload() {
      final String payload = "default-serialization-native-encoding-test";

      this.transactionTemplate.execute(status -> this.streamBridge.send("output", payload));

      await()
          .atMost(15, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            assertThat(this.messageCollector.getMessages()).hasSizeGreaterThanOrEqualTo(1);
            final ReceivedMessage received = this.messageCollector.getMessages().stream()
                .filter(m -> m.payload().equals(payload))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected message not found"));
            assertThat(received.payload()).isEqualTo(payload);
          });
    }

    /**
     * Validates that multiple messages sent with this combination are all received by the consumer.
     */
    @Test
    void when_native_encoding_enabled_multiple_messages_are_all_received() {
      final int messageCount = 5;
      for (int i = 0; i < messageCount; i++) {
        final int index = i;
        this.transactionTemplate.execute(status -> this.streamBridge.send("output", "native-batch-" + index));
      }

      await()
          .atMost(30, TimeUnit.SECONDS)
          .untilAsserted(() -> {
            final long batchMessages = this.messageCollector.getMessages().stream()
                .filter(m -> m.payload().startsWith("native-batch-"))
                .count();
            assertThat(batchMessages).isEqualTo(messageCount);
          });
    }
  }
}
