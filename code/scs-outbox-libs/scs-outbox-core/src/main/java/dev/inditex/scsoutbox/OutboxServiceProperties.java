package dev.inditex.scsoutbox;

import java.util.LinkedList;
import java.util.List;

import dev.inditex.scsoutbox.config.BindingMatcher;
import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.stream.config.BindingServiceProperties;

@Slf4j
@RequiredArgsConstructor
public class OutboxServiceProperties implements InitializingBean {

  private final OutboxProperties properties;

  private final BindingServiceProperties bindingServiceProperties;

  /**
   * Determines whether the outbox is enabled for the given Spring Cloud Stream binding name.
   *
   * <p>Evaluation rules (in order): <ol> <li>If both {@code inclusions} and {@code exclusions} are empty, outbox is enabled for all
   * bindings (default behaviour).</li> <li>If {@code inclusions} is empty, outbox is enabled unless the binding matches any entry in
   * {@code exclusions}.</li> <li>Otherwise, outbox is enabled only if the binding matches at least one entry in {@code inclusions} AND does
   * not match any entry in {@code exclusions}. <strong>Exclusions always take precedence.</strong></li> </ol>
   *
   * <p>Each entry in {@code inclusions} / {@code exclusions} is represented by a {@link dev.inditex.scsoutbox.config.BindingMatcher} that
   * performs either an exact {@link String#equals} comparison or a full Java-regex match (when the entry is prefixed with
   * {@code "regex:"}).
   *
   * <p><strong>Caching:</strong> results are intentionally <em>not</em> memoised. The per-call cost (~6–137 ns depending on configuration)
   * is negligible compared to the overall cost of the outbox operation (~1–50 ms), and caching would introduce stale-state risk if
   * {@code OutboxProperties} ever becomes {@code @RefreshScope}-aware. See <a
   * href="../../../../../../../../docs/adr/0001-no-cache-for-isOutboxEnabledFor.md">ADR-0001</a> for the full analysis and rationale.
   *
   * @param bindingName the Spring Cloud Stream binding name to evaluate
   * @return {@code true} if the outbox should intercept messages for this binding, {@code false} otherwise
   */
  public boolean isOutboxEnabledFor(final String bindingName) {
    final Bindings bindings = this.properties.getBindings();
    if (bindings.getInclusions().isEmpty() && bindings.getExclusions().isEmpty()) {
      // Default behaviour
      return true;
    } else if (bindings.getInclusions().isEmpty()) {
      return bindings.getExclusions().stream().noneMatch(m -> m.matches(bindingName));
    }
    return bindings.getInclusions().stream().anyMatch(m -> m.matches(bindingName))
        && bindings.getExclusions().stream().noneMatch(m -> m.matches(bindingName));
  }

  public String getDestination(final String bindingName) {
    return this.bindingServiceProperties.getBindingProperties(bindingName).getDestination();
  }

  public boolean useNativeEncoding(final String bindingName) {
    return this.bindingServiceProperties.getProducerProperties(bindingName).isUseNativeEncoding();
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    final List<String> bindingNames = List.copyOf(this.bindingServiceProperties.getBindings().keySet());

    // Validate exact (non-regex) entries exist in SCS bindings configuration
    final List<String> exactInclusions = this.properties.getBindings().getInclusions().stream()
        .filter(m -> !m.isRegex())
        .map(BindingMatcher::getRawValue)
        .collect(LinkedList::new, LinkedList::add, LinkedList::addAll);
    final List<String> exactExclusions = this.properties.getBindings().getExclusions().stream()
        .filter(m -> !m.isRegex())
        .map(BindingMatcher::getRawValue)
        .collect(LinkedList::new, LinkedList::add, LinkedList::addAll);

    exactInclusions.removeAll(bindingNames);
    exactExclusions.removeAll(bindingNames);

    if (!exactInclusions.isEmpty() || !exactExclusions.isEmpty()) {
      throw new IllegalArgumentException(
          "Binding names not detected in spring cloud stream bindings configuration."
              + " Inclusions [ " + exactInclusions + "]"
              + " Exclusions [ " + exactExclusions + "]");
    }

    // Warn about regex patterns that don't match any declared binding
    this.properties.getBindings().getInclusions().stream()
        .filter(BindingMatcher::isRegex)
        .filter(m -> bindingNames.stream().noneMatch(m::matches))
        .forEach(m -> log.warn(
            "Regex inclusion pattern '{}' does not match any declared Spring Cloud Stream binding.", m.getRawValue()));

    this.properties.getBindings().getExclusions().stream()
        .filter(BindingMatcher::isRegex)
        .filter(m -> bindingNames.stream().noneMatch(m::matches))
        .forEach(m -> log.warn(
            "Regex exclusion pattern '{}' does not match any declared Spring Cloud Stream binding.", m.getRawValue()));
  }
}
