package dev.inditex.scsoutbox.config.producer;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.cloud.stream.binding.Bindable;

/** The input binding names resolved by Spring Cloud Stream for the application. */
final class InputBindings {

  private static final Pattern CONVENTIONAL_INPUT_NAME = Pattern.compile(".*-in-\\d+");

  private final Set<String> resolvedNames;

  private InputBindings(final Set<String> resolvedNames) {
    this.resolvedNames = resolvedNames;
  }

  static InputBindings from(final Collection<? extends Bindable> bindables) {
    final Set<String> inputNames = bindables.stream()
        .flatMap(bindable -> bindable.getInputs().stream())
        .collect(Collectors.toUnmodifiableSet());
    return new InputBindings(inputNames);
  }

  static InputBindings conventionalNamesOnly() {
    return new InputBindings(Set.of());
  }

  boolean isInputBinding(final String bindingName) {
    return this.resolvedNames.contains(bindingName) || CONVENTIONAL_INPUT_NAME.matcher(bindingName).matches();
  }
}
