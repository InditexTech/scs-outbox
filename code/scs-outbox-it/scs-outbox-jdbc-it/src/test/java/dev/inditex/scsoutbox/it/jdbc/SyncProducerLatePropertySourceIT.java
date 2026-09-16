package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Integration test covering bindings that are contributed to the environment <em>late</em>, after Spring Boot has run every
 * {@code EnvironmentPostProcessor}.
 *
 * <p>Frameworks layered on top of Spring Boot commonly expose their own configuration namespace and relocate it into the
 * {@code spring.cloud.stream.*} namespace from an {@code EnvironmentPostProcessor} ordered at {@code Ordered.LOWEST_PRECEDENCE}, or later
 * still. Any attempt by scs-outbox to read {@code spring.cloud.stream.bindings.*} during its own environment post-processing would observe
 * an empty map and silently configure nothing.
 *
 * <p>Applying the configuration when the binder is initialised makes scs-outbox independent of that ordering: by then every property source
 * has been contributed and {@code BindingServiceProperties} is fully bound.
 */
class SyncProducerLatePropertySourceIT {

  private static final String LATE_BINDING = "late-out-0";

  /**
   * Contributes the binding definition only once the environment has been fully post-processed, reproducing what a framework that relocates
   * its own configuration namespace does.
   */
  static class LateBindingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      applicationContext.getEnvironment().getPropertySources().addLast(new MapPropertySource(
          "late-binding-property-source",
          Map.of("spring.cloud.stream.bindings." + LATE_BINDING + ".destination", "late-destination")));
    }
  }

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

  private static ConfigurableApplicationContext run() {
    final String[] arguments = Stream.of(
        "--spring.docker.compose.enabled=true",
        "--spring.docker.compose.skip.in-tests=false",
        "--app.scheduling.enable=false",
        // The binding is declared only by LateBindingInitializer; only the outbox selection is known upfront.
        "--spring.cloud.stream.output-bindings=" + LATE_BINDING,
        "--scs-outbox.bindings.inclusions=" + LATE_BINDING,
        "--scs-outbox.bindings.exclusions=")
        .toArray(String[]::new);
    return new SpringApplicationBuilder(TestConfig.class)
        .web(WebApplicationType.NONE)
        .initializers(new LateBindingInitializer())
        .run(arguments);
  }

  @Test
  void when_binding_is_declared_after_environment_post_processing_expect_producer_configured_as_synchronous() {
    try (ConfigurableApplicationContext context = run()) {
      assertThat(context.isRunning()).isTrue();

      final ExtendedPropertiesBinder<?, ?, ?> binder =
          (ExtendedPropertiesBinder<?, ?, ?>) context.getBean(BinderFactory.class).getBinder(null, MessageChannel.class);
      final KafkaProducerProperties producerProperties =
          (KafkaProducerProperties) binder.getExtendedProducerProperties(LATE_BINDING);

      assertThat(producerProperties.isSync()).isTrue();
    }
  }
}
