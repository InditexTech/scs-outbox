package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.stream.binder.BinderFactory;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binding.Bindable;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Verifies that an anonymous function input renamed to a custom binding name is not treated as a producer.
 */
class SyncProducerBindableInputIT {

  private static final String INPUT_BINDING = "anonymous-inbound";

  private static final String OUTPUT_BINDING = "output";

  private static final String INPUT_PRODUCER_SYNC_PROPERTY =
      "spring.cloud.stream.kafka.bindings." + INPUT_BINDING + ".producer.sync";

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

  private static ConfigurableApplicationContext run(final String... properties) {
    final String[] arguments = Stream.concat(
        Stream.of(
            "--spring.docker.compose.enabled=true",
            "--spring.docker.compose.skip.in-tests=false",
            "--app.scheduling.enable=false",
            "--spring.cloud.function.definition=myConsumer",
            "--spring.cloud.stream.function.bindings.myConsumer-in-0=" + INPUT_BINDING,
            "--spring.cloud.stream.bindings." + INPUT_BINDING + ".destination=inbound",
            "--spring.cloud.stream.output-bindings=" + OUTPUT_BINDING,
            "--spring.cloud.stream.bindings." + OUTPUT_BINDING + ".destination=published",
            "--scs-outbox.bindings.inclusions=",
            "--scs-outbox.bindings.exclusions="),
        Stream.of(properties).map(property -> "--" + property))
        .toArray(String[]::new);
    return new SpringApplicationBuilder(TestConfig.class)
        .web(WebApplicationType.NONE)
        .run(arguments);
  }

  @Test
  void when_renamed_anonymous_input_declares_producer_property_expect_startup_to_succeed() {
    try (ConfigurableApplicationContext context = run(INPUT_PRODUCER_SYNC_PROPERTY + "=false")) {
      assertThat(context.isRunning()).isTrue();
      assertThat(context.getBean(BindingServiceProperties.class).getBindingProperties(INPUT_BINDING).getGroup()).isNull();
      assertThat(context.getBeansOfType(Bindable.class).values())
          .anySatisfy(bindable -> assertThat(bindable.getInputs()).contains(INPUT_BINDING));

      final ExtendedPropertiesBinder<?, ?, ?> binder =
          (ExtendedPropertiesBinder<?, ?, ?>) context.getBean(BinderFactory.class).getBinder(null, MessageChannel.class);
      assertThat(((KafkaProducerProperties) binder.getExtendedProducerProperties(OUTPUT_BINDING)).isSync()).isTrue();
    }
  }
}
