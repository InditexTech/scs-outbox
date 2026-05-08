package dev.inditex.scsoutbox.config;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Encapsulates the logic for matching a binding name against either an exact name or a regular expression.
 *
 * <p>Entries prefixed with {@value #REGEX_PREFIX} are treated as Java-style regular expressions. All other entries are treated as exact
 * binding names.
 */
public class BindingMatcher {

  public static final String REGEX_PREFIX = "regex:";

  private final String rawValue;

  private final Pattern compiledPattern;

  private final boolean regex;

  /**
   * Creates a new {@link BindingMatcher} from the given raw value.
   *
   * @param rawValue plain binding name or a {@code regex:}-prefixed regular expression
   * @throws IllegalArgumentException if the regular expression syntax is invalid
   */
  public BindingMatcher(final String rawValue) {
    Objects.requireNonNull(rawValue, "Binding matcher value must not be null");
    this.rawValue = rawValue;
    if (rawValue.startsWith(REGEX_PREFIX)) {
      this.regex = true;
      final String pattern = rawValue.substring(REGEX_PREFIX.length());
      try {
        this.compiledPattern = Pattern.compile(pattern);
      } catch (final PatternSyntaxException e) {
        throw new IllegalArgumentException(
            "Invalid regex pattern in binding configuration: '" + pattern + "'. " + e.getDescription(), e);
      }
    } else {
      this.regex = false;
      this.compiledPattern = null;
    }
  }

  /**
   * Returns {@code true} if the given binding name matches this matcher.
   *
   * <p>For exact matchers the comparison is performed with {@link String#equals(Object)}. For regex matchers the full binding name must
   * match the compiled pattern.
   *
   * @param bindingName the binding name to test
   * @return {@code true} when the binding name matches
   */
  public boolean matches(final String bindingName) {
    if (this.regex) {
      return this.compiledPattern.matcher(bindingName).matches();
    }
    return this.rawValue.equals(bindingName);
  }

  /**
   * Returns {@code true} if this matcher was created from a {@code regex:}-prefixed value.
   */
  public boolean isRegex() {
    return this.regex;
  }

  /**
   * Returns the original raw value used to create this matcher (including the {@code regex:} prefix when applicable).
   */
  public String getRawValue() {
    return this.rawValue;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || this.getClass() != o.getClass()) {
      return false;
    }
    final BindingMatcher that = (BindingMatcher) o;
    return Objects.equals(this.rawValue, that.rawValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.rawValue);
  }

  @Override
  public String toString() {
    return this.rawValue;
  }
}
