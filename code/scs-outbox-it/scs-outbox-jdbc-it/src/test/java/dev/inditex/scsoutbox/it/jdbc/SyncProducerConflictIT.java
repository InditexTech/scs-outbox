package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Integration test validating that the application refuses to start when an outbox-enabled binding is explicitly configured to publish
 * asynchronously.
 *
 * <p>{@code SyncProducerBinderListener} never overrides a property owned by the application, so an explicit {@code producer.sync=false}
 * would otherwise silently disable the delivery guarantee of the outbox: the outbox record is deleted as soon as {@code StreamBridge.send}
 * returns {@code true}, which for an asynchronous producer happens before the broker acknowledges the record. Failing at startup surfaces
 * the misconfiguration instead of losing messages at runtime.
 */
class SyncProducerConflictIT {

  private static final String OUTBOX_BINDING_SYNC_PROPERTY = "spring.cloud.stream.kafka.bindings.output.producer.sync";

  @Configuration
  @EnableAutoConfiguration
  @EnableTransactionManagement
  static class TestConfig {

    @Bean
    public Consumer<String> myConsumer() {
      return message -> {
      };
    }
  }

  /**
   * Starts a full application context.
   *
   * <p>The properties are passed as command line arguments on purpose: {@code SpringApplicationBuilder.properties(..)} contributes default
   * properties, which have a lower precedence than {@code application.yml} and would therefore not override it.
   */
  private static ConfigurableApplicationContext run(final String... properties) {
    final String[] arguments = Stream.concat(
        Stream.of(
            "--spring.docker.compose.enabled=true",
            "--spring.docker.compose.skip.in-tests=false",
            "--app.scheduling.enable=false"),
        Stream.of(properties).map(property -> "--" + property))
        .toArray(String[]::new);
    return new SpringApplicationBuilder(TestConfig.class)
        .web(WebApplicationType.NONE)
        .run(arguments);
  }

  @Test
  void when_outbox_enabled_binding_is_explicitly_asynchronous_expect_startup_failure() {
    assertThatThrownBy(() -> run(OUTBOX_BINDING_SYNC_PROPERTY + "=false"))
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("output")
        .hasMessageContaining(OUTBOX_BINDING_SYNC_PROPERTY + "=false")
        .hasMessageContaining("scs-outbox.bindings.exclusions")
        // The global 'scs-outbox.bindings.sync-producers.enabled=false' switch must never be suggested as a fix for a single
        // misconfigured binding: it disables the delivery guarantee for every outbox-enabled binding in the application.
        .hasMessageNotContaining("scs-outbox.bindings.sync-producers.enabled=false");
  }

  @Test
  void when_asynchronous_binding_is_excluded_from_the_outbox_expect_startup_to_succeed() {
    try (ConfigurableApplicationContext context = run(
        OUTBOX_BINDING_SYNC_PROPERTY + "=false",
        "scs-outbox.bindings.inclusions=",
        "scs-outbox.bindings.exclusions=output")) {
      assertThat(context.isRunning()).isTrue();
    }
  }

  @Test
  void when_automatic_configuration_is_disabled_expect_startup_to_succeed() {
    try (ConfigurableApplicationContext context = run(
        OUTBOX_BINDING_SYNC_PROPERTY + "=false",
        "scs-outbox.bindings.sync-producers.enabled=false")) {
      assertThat(context.isRunning()).isTrue();
    }
  }
}
