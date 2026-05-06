package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import java.util.function.Consumer;

import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.publish.OutboxPublishingTask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test to verify that {@code scs-outbox.publishing.batch-size} is hot-refreshable.
 *
 * <p>This test verifies that changing {@code batch-size} at runtime via Spring Cloud Config refresh immediately limits the number of
 * messages published per publishing cycle, without requiring an application restart.
 *
 * <p>The scheduler is disabled ({@code app.scheduling.enable=false}) so that publishing cycles are driven manually via
 * {@link OutboxPublishingTask#run()}, giving full control over when each cycle executes.
 */
@SpringBootTest(
    classes = {BatchSizeHotRefreshIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.batch-size=3",
        "app.scheduling.enable=false",
        "management.endpoints.web.exposure.include=refresh",
        "management.endpoint.refresh.enabled=true",
        "spring.cloud.refresh.enabled=true",
        "spring.cloud.function.definition=myConsumer",
        "scs-outbox.bindings.inclusions=output"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class BatchSizeHotRefreshIT {

  @Configuration
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  static class TestConfig {

    @Bean
    public Consumer<String> myConsumer() {
      return message -> {
        // Mock bean will handle the verification
      };
    }
  }

  @Autowired
  private StreamBridge streamBridge;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private ConfigurableEnvironment environment;

  @Autowired
  private ContextRefresher contextRefresher;

  @Autowired
  private OutboxPublishingTask outboxPublishingTask;

  @Autowired
  private OutboxMessageRepository outboxMessageRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoBean
  private Consumer<String> myConsumer;

  @BeforeEach
  void setUp() {
    this.jdbcTemplate.execute("DELETE FROM scs_outbox");
    // Remove any custom property source so each test starts from the @SpringBootTest defaults
    this.environment.getPropertySources().remove("test-batch-size-refresh-props");
    this.contextRefresher.refresh();
  }

  @Test
  void shouldReducePublishedMessagesPerCycleAfterBatchSizeHotRefresh() {
    // 1. Insert 5 messages – initial batch-size is 3, so a single cycle publishes at most 3
    for (int i = 1; i <= 5; i++) {
      this.sendMessage("msg-reduce-" + i);
    }
    assertThat(this.outboxMessageRepository.count()).isEqualTo(5);

    // 2. Run one publishing cycle with batch-size=3: expect exactly 3 messages published, 2 remaining
    this.outboxPublishingTask.run();
    assertThat(this.outboxMessageRepository.count()).isEqualTo(2);

    // 3. Hot-refresh batch-size to 1
    this.updateBatchSize(1);
    this.contextRefresher.refresh();

    // 4. Run another cycle with the new batch-size=1: only 1 additional message should be published, 1 remaining
    this.outboxPublishingTask.run();
    assertThat(this.outboxMessageRepository.count()).isEqualTo(1);
  }

  @Test
  void shouldIncreasePublishedMessagesPerCycleAfterBatchSizeHotRefresh() {
    // 1. Insert 5 messages – initial batch-size is 3
    for (int i = 1; i <= 5; i++) {
      this.sendMessage("msg-increase-" + i);
    }
    assertThat(this.outboxMessageRepository.count()).isEqualTo(5);

    // 2. Hot-refresh batch-size to 5 so all messages fit in a single cycle
    this.updateBatchSize(5);
    this.contextRefresher.refresh();

    // 3. Run one publishing cycle with the new batch-size=5: all 5 messages should be published
    this.outboxPublishingTask.run();
    assertThat(this.outboxMessageRepository.count()).isZero();
  }

  private void sendMessage(final String payload) {
    this.transactionTemplate.execute(status -> this.streamBridge.send("output", payload));
  }

  private void updateBatchSize(final int batchSize) {
    final MutablePropertySources propertySources = this.environment.getPropertySources();
    final Properties props = new Properties();
    props.setProperty("scs-outbox.publishing.batch-size", String.valueOf(batchSize));
    propertySources.remove("test-batch-size-refresh-props");
    propertySources.addFirst(new PropertiesPropertySource("test-batch-size-refresh-props", props));
  }

}
