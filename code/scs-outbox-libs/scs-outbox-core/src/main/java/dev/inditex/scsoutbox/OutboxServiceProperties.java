package dev.inditex.scsoutbox;

import java.util.LinkedList;
import java.util.List;

import dev.inditex.scsoutbox.config.BindingMatcher;
import dev.inditex.scsoutbox.config.OutboxProperties;

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
   * <p>The evaluation rules live in {@link dev.inditex.scsoutbox.config.OutboxProperties.Bindings#matches(String)} so that they can also be
   * applied while a binder is being initialised (see {@code dev.inditex.scsoutbox.config.producer.SyncProducerBinderFactoryListener}).
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
    return this.properties.getBindings().matches(bindingName);
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
