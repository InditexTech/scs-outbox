package dev.inditex.scsoutbox.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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

    /**
     * Whether scs-outbox should enforce synchronous producers for outbox-enabled bindings.
     *
     * <p>When enabled, scs-outbox configures missing producer sync settings and fails startup when the application explicitly configures an
     * outbox-enabled producer as asynchronous.
     */
    private final boolean enforceProducerSync;

    public Bindings(final List<String> inclusions, final List<String> exclusions) {
      this(inclusions, exclusions, null);
    }

    /**
     * Creates the binding selection and producer sync enforcement configuration.
     *
     * <p>Note: this constructor is explicitly annotated with {@link ConstructorBinding} because the class declares more than one
     * constructor, which disables Spring Boot's "single parameterized constructor" inference.
     *
     * @param inclusions raw inclusion entries (exact binding names or {@code regex:}-prefixed patterns)
     * @param exclusions raw exclusion entries (exact binding names or {@code regex:}-prefixed patterns)
     * @param enforceProducerSync whether scs-outbox should enforce synchronous producers for outbox-enabled bindings
     */
    @ConstructorBinding
    public Bindings(final List<String> inclusions, final List<String> exclusions, final Boolean enforceProducerSync) {
      if (inclusions != null) {
        inclusions.stream().map(BindingMatcher::new).forEach(this.inclusions::add);
      }
      if (exclusions != null) {
        exclusions.stream().map(BindingMatcher::new).forEach(this.exclusions::add);
      }
      this.enforceProducerSync = Objects.requireNonNullElse(enforceProducerSync, true);
      this.validateNoExactConflicts();
    }

    /**
     * Determines whether the outbox is enabled for the given Spring Cloud Stream binding name.
     *
     * <p>Evaluation rules (in order): <ol> <li>If both {@code inclusions} and {@code exclusions} are empty, outbox is enabled for all
     * bindings (default behaviour).</li> <li>If {@code inclusions} is empty, outbox is enabled unless the binding matches any entry in
     * {@code exclusions}.</li> <li>Otherwise, outbox is enabled only if the binding matches at least one entry in {@code inclusions} AND
     * does not match any entry in {@code exclusions}. <strong>Exclusions always take precedence.</strong></li> </ol>
     *
     * @param bindingName the Spring Cloud Stream binding name to evaluate
     * @return {@code true} if the outbox should intercept messages for this binding, {@code false} otherwise
     */
    public boolean matches(final String bindingName) {
      if (this.inclusions.isEmpty() && this.exclusions.isEmpty()) {
        // Default behaviour
        return true;
      } else if (this.inclusions.isEmpty()) {
        return this.exclusions.stream().noneMatch(m -> m.matches(bindingName));
      }
      return this.inclusions.stream().anyMatch(m -> m.matches(bindingName))
          && this.exclusions.stream().noneMatch(m -> m.matches(bindingName));
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
