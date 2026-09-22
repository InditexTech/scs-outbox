package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Integration test covering binder-specific properties declared in the <em>binder child environment</em>, that is under
 * {@code spring.cloud.stream.binders.<name>.environment.*}.
 *
 * <p>Those properties never appear in the main application environment: Spring Cloud Stream materialises them only in the child context it
 * creates for the binder. scs-outbox must therefore read the effective producer configuration from the binder itself, otherwise it would
 * both fail to notice that the application already enabled synchronous publishing and silently override an explicit decision to disable it.
 */
class SyncProducerBinderEnvironmentIT {

  private static final String BINDER_DEFAULT_SYNC_PROPERTY =
      "spring.cloud.stream.binders.named-kafka.environment.spring.cloud.stream.kafka.default.producer.sync";

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
   * The properties are passed as command line arguments because {@code SpringApplicationBuilder.properties(..)} contributes default
   * properties, which have a lower precedence than {@code application.yml}.
   */
  private static ConfigurableApplicationContext run(final String... properties) {
    final String[] arguments = Stream.concat(
        Stream.of(
            "--spring.docker.compose.enabled=true",
            "--spring.docker.compose.skip.in-tests=false",
            "--app.scheduling.enable=false",
            // A named binder instance, declaring its own child environment.
            "--spring.cloud.stream.binders.named-kafka.type=kafka",
            "--spring.cloud.stream.binders.named-kafka.environment.spring.cloud.stream.kafka.binder.brokers=localhost:30810",
            "--spring.cloud.stream.default-binder=named-kafka"),
        Stream.of(properties).map(property -> "--" + property))
        .toArray(String[]::new);
    return new SpringApplicationBuilder(TestConfig.class)
        .web(WebApplicationType.NONE)
        .run(arguments);
  }

  private static boolean isSync(final ConfigurableApplicationContext context, final String bindingName) {
    final ExtendedPropertiesBinder<?, ?, ?> binder =
        (ExtendedPropertiesBinder<?, ?, ?>) context.getBean(BinderFactory.class).getBinder(null, MessageChannel.class);
    return ((KafkaProducerProperties) binder.getExtendedProducerProperties(bindingName)).isSync();
  }

  @Test
  void when_sync_is_enabled_only_in_the_binder_child_environment_expect_startup_to_succeed() {
    try (ConfigurableApplicationContext context = run(BINDER_DEFAULT_SYNC_PROPERTY + "=true")) {
      assertThat(context.isRunning()).isTrue();
      assertThat(isSync(context, "output")).isTrue();
    }
  }

  @Test
  void when_sync_is_disabled_only_in_the_binder_child_environment_expect_startup_failure() {
    assertThatThrownBy(() -> run(BINDER_DEFAULT_SYNC_PROPERTY + "=false"))
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("output")
        .hasMessageContaining("spring.cloud.stream.kafka.default.producer.sync=false");
  }

  @Test
  void when_sync_is_not_configured_anywhere_expect_binder_configured_as_synchronous() {
    try (ConfigurableApplicationContext context = run()) {
      assertThat(context.isRunning()).isTrue();
      assertThat(isSync(context, "output")).isTrue();
    }
  }
}
