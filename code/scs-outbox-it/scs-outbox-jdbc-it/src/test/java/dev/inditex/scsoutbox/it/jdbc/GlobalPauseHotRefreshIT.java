package dev.inditex.scsoutbox.it.jdbc;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test to verify the functionality of globally pausing publishing on the fly.
 *
 * <p>This test verifies that it is possible to pause ALL message publishing at runtime using Spring Cloud Config refresh, without needing
 * to restart the application.
 */
@SpringBootTest(classes = {GlobalPauseHotRefreshIT.TestConfig.class}, properties = {
    "spring.docker.compose.enabled=true",
    "spring.docker.compose.skip.in-tests=false",
    "scs-outbox.publishing.batch-size=1",
    "scs-outbox.publishing.scheduler.fixed-rate=1000",
    "scs-outbox.publishing.after-commit=false",
    "management.endpoints.web.exposure.include=refresh",
    "management.endpoint.refresh.enabled=true",
    "spring.cloud.refresh.enabled=true",
    "spring.cloud.function.definition=myConsumer",
    "scs-outbox.bindings.inclusions=output"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class GlobalPauseHotRefreshIT {

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

  @MockitoBean
  private Consumer<String> myConsumer;

  @Test
  void shouldPauseAllPublishingGlobally() {
    // 1. Check initial state - publishing enabled
    this.sendMessageToDestination("output", "message1");
    this.verifyMessagePublished(this.myConsumer, "message1");

    // 2. Pause all publishing globally
    this.updateConfiguration("scs-outbox.publishing.paused", "true");
    this.refreshConfiguration();

    // 3. Send new message after global pause - should not be published
    this.sendMessageToDestination("output", "message2");
    this.verifyMessageNotPublished(this.myConsumer, "message2");

    // 4. Resume publishing globally
    this.updateConfiguration("scs-outbox.publishing.paused", "false");
    this.refreshConfiguration();

    // 5. Verify that publishing works again
    this.sendMessageToDestination("output", "message3");
    this.verifyMessagePublished(this.myConsumer, "message3");
  }

  @Test
  void globalPauseTakesPrecedenceOverPausedDestinations() {
    // 1. Set specific destinations as paused but publishing enabled
    this.updateConfiguration("scs-outbox.publishing.paused-destinations", "nonExistentDestination");
    this.refreshConfiguration();

    // 2. Verify that messages are still published (destination not in paused list)
    this.sendMessageToDestination("output", "message1");
    this.verifyMessagePublished(this.myConsumer, "message1");

    // 3. Enable global pause (should take precedence over paused destinations)
    this.updateConfiguration("scs-outbox.publishing.paused", "true");
    this.refreshConfiguration();

    // 4. Verify that NO messages are published, despite destination not being in
    // paused list
    this.sendMessageToDestination("output", "message2");
    this.verifyMessageNotPublished(this.myConsumer, "message2");

    // 5. Disable global pause
    this.updateConfiguration("scs-outbox.publishing.paused", "false");
    this.refreshConfiguration();

    // 6. Verify that publishing works again
    this.sendMessageToDestination("output", "message3");
    this.verifyMessagePublished(this.myConsumer, "message3");
  }

  private void sendMessageToDestination(String destination, String message) {
    this.transactionTemplate.execute(status -> this.streamBridge.send(destination, message));
  }

  private void verifyMessagePublished(Consumer<String> consumer, String expectedMessage) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(consumer).accept(expectedMessage));
  }

  private void verifyMessageNotPublished(Consumer<String> consumer, String message) {
    // Wait a reasonable time and verify that it was NOT called
    await()
        .pollDelay(3, TimeUnit.SECONDS)
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(consumer, never()).accept(message));
  }

  private void updateConfiguration(String property, String value) {
    // Update the Spring environment with the new configuration
    final MutablePropertySources propertySources = this.environment.getPropertySources();
    final Properties props = new Properties();
    props.setProperty(property, value);

    // Remove the previous property if it exists to avoid conflicts
    propertySources.remove("test-global-pause-refresh-props");

    // Add the new configuration with high priority
    propertySources.addFirst(new PropertiesPropertySource("test-global-pause-refresh-props", props));
  }

  private void refreshConfiguration() {
    // Use ContextRefresher to trigger the refresh programmatically
    // This simulates what a call to /actuator/refresh would do
    this.contextRefresher.refresh();

    // Give a short time for the refresh to be processed
    await()
        .pollDelay(500, TimeUnit.MILLISECONDS)
        .atMost(2, TimeUnit.SECONDS)
        .until(() -> true); // Just wait
  }
}
