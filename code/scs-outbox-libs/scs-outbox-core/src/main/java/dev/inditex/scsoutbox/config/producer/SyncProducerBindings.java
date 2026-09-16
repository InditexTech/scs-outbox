package dev.inditex.scsoutbox.config.producer;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.cloud.stream.config.BindingProperties;

/**
 * Helpers shared by {@link SyncProducerEnvironmentPostProcessor} and {@link SyncProducerValidator} so that both always agree on which
 * bindings they consider, and on how binder properties are looked up.
 */
final class SyncProducerBindings {

  /**
   * Spring Cloud Stream names the bindings derived from a function {@code <function>-in-<index>} for inputs and
   * {@code <function>-out-<index>} for outputs.
   */
  private static final Pattern FUNCTION_INPUT_BINDING = Pattern.compile(".*-in-\\d+");

  private SyncProducerBindings() {
  }

  /**
   * Returns {@code true} when the binding may be used to publish messages, and is therefore a candidate for synchronous producer
   * configuration.
   *
   * <p>A binding is discarded when it declares no destination (nothing would be published), when its name follows the Spring Cloud Stream
   * convention for function inputs, or when it only declares consumer settings. Contributing producer properties to a consumer binding
   * would be harmless for the binder but would make the validator report inbound bindings as violations.
   *
   * @param bindingName the Spring Cloud Stream binding name
   * @param bindingProperties the binding configuration, may be {@code null}
   * @return {@code true} when the binding can publish messages
   */
  static boolean isProducerBinding(final String bindingName, final BindingProperties bindingProperties) {
    if (bindingProperties == null || bindingProperties.getDestination() == null || bindingProperties.getDestination().isBlank()) {
      return false;
    }
    if (FUNCTION_INPUT_BINDING.matcher(bindingName).matches()) {
      return false;
    }
    return bindingProperties.getProducer() != null || bindingProperties.getGroup() == null;
  }

  /**
   * Adapts a property key into a {@link ConfigurationPropertyName}.
   *
   * <p>{@link ConfigurationPropertyName#of(CharSequence)} rejects characters that are legal in a binding name, such as the upper case
   * letters of {@code myProducer-out-0}. {@link ConfigurationPropertyName#adapt(CharSequence, char)} accepts them and still matches
   * properties declared in any of the relaxed forms supported by Spring Boot.
   *
   * @param propertyKey the canonical property key
   * @return the adapted configuration property name
   */
  static ConfigurationPropertyName propertyName(final String propertyKey) {
    return ConfigurationPropertyName.adapt(propertyKey, '.');
  }
}
