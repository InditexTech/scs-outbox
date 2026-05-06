package dev.inditex.scsoutbox.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties("scs-outbox")
public class OutboxProperties {

  private final Bindings bindings;

  public OutboxProperties(final Bindings bindings) {
    this.bindings = Objects.requireNonNullElseGet(bindings, () -> new Bindings(List.of(), List.of()));
  }

  @Getter
  public static class Bindings {
    private final List<BindingMatcher> inclusions = new ArrayList<>();

    private final List<BindingMatcher> exclusions = new ArrayList<>();

    public Bindings(final List<String> inclusions, final List<String> exclusions) {
      if (inclusions != null) {
        inclusions.stream().map(BindingMatcher::new).forEach(this.inclusions::add);
      }
      if (exclusions != null) {
        exclusions.stream().map(BindingMatcher::new).forEach(this.exclusions::add);
      }
      this.validateNoExactConflicts();
    }

    private void validateNoExactConflicts() {
      final List<String> exactInclusions = this.inclusions.stream()
          .filter(m -> !m.isRegex())
          .map(BindingMatcher::getRawValue)
          .toList();
      final List<String> exactExclusions = this.exclusions.stream()
          .filter(m -> !m.isRegex())
          .map(BindingMatcher::getRawValue)
          .toList();
      final boolean hasConflict = exactInclusions.stream().anyMatch(exactExclusions::contains);
      if (hasConflict) {
        throw new IllegalArgumentException(
            "inclusion list cannot contain any element of exclusion list. Inclusions: "
                + this.inclusions + " Exclusions: " + this.exclusions);
      }
    }
  }
}
