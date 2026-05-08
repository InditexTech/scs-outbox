package dev.inditex.scsoutbox.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BindingMatcherTest {

  // --- Exact matching ---

  @Test
  void exact_matches_same_name() {
    final BindingMatcher matcher = new BindingMatcher("produce-book-created-out-0");
    assertTrue(matcher.matches("produce-book-created-out-0"));
  }

  @Test
  void exact_does_not_match_different_name() {
    final BindingMatcher matcher = new BindingMatcher("produce-book-created-out-0");
    assertFalse(matcher.matches("produce-user-added-out-0"));
  }

  @Test
  void exact_is_not_regex() {
    final BindingMatcher matcher = new BindingMatcher("produce-book-created-out-0");
    assertFalse(matcher.isRegex());
  }

  @Test
  void exact_raw_value_is_preserved() {
    final BindingMatcher matcher = new BindingMatcher("produce-book-created-out-0");
    assertEquals("produce-book-created-out-0", matcher.getRawValue());
  }

  // --- Regex matching ---

  @Test
  void regex_matches_binding_name() {
    final BindingMatcher matcher = new BindingMatcher("regex:produce-.*-out-\\d+");
    assertTrue(matcher.matches("produce-book-created-out-0"));
    assertTrue(matcher.matches("produce-user-added-out-1"));
  }

  @Test
  void regex_does_not_match_non_matching_name() {
    final BindingMatcher matcher = new BindingMatcher("regex:produce-.*-out-\\d+");
    assertFalse(matcher.matches("consume-book-created-in-0"));
  }

  @Test
  void regex_is_regex() {
    final BindingMatcher matcher = new BindingMatcher("regex:produce-.*-out-\\d+");
    assertTrue(matcher.isRegex());
  }

  @Test
  void regex_raw_value_includes_prefix() {
    final BindingMatcher matcher = new BindingMatcher("regex:produce-.*-out-\\d+");
    assertEquals("regex:produce-.*-out-\\d+", matcher.getRawValue());
  }

  @Test
  void regex_requires_full_match() {
    final BindingMatcher matcher = new BindingMatcher("regex:out-\\d+");
    // Should NOT match because Pattern.matches() requires the entire string to match
    assertFalse(matcher.matches("produce-book-created-out-0"));
  }

  // --- Fail-fast on invalid regex ---

  @Test
  void invalid_regex_throws_illegal_argument_exception() {
    assertThrows(IllegalArgumentException.class, () -> new BindingMatcher("regex:[invalid"));
  }

  @Test
  void invalid_regex_exception_contains_pattern() {
    final IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> new BindingMatcher("regex:[invalid"));
    assertTrue(exception.getMessage().contains("[invalid"));
  }

  // --- Null value ---

  @Test
  void null_value_throws_null_pointer_exception() {
    assertThrows(NullPointerException.class, () -> new BindingMatcher(null));
  }

  // --- equals and hashCode ---

  @Test
  void equals_same_raw_value() {
    final BindingMatcher matcher1 = new BindingMatcher("produce-book-created-out-0");
    final BindingMatcher matcher2 = new BindingMatcher("produce-book-created-out-0");
    assertEquals(matcher1, matcher2);
    assertEquals(matcher1.hashCode(), matcher2.hashCode());
  }

  @Test
  void equals_same_regex_raw_value() {
    final BindingMatcher matcher1 = new BindingMatcher("regex:produce-.*-out-\\d+");
    final BindingMatcher matcher2 = new BindingMatcher("regex:produce-.*-out-\\d+");
    assertEquals(matcher1, matcher2);
    assertEquals(matcher1.hashCode(), matcher2.hashCode());
  }

  @Test
  void not_equals_different_raw_value() {
    final BindingMatcher matcher1 = new BindingMatcher("produce-book-created-out-0");
    final BindingMatcher matcher2 = new BindingMatcher("produce-user-added-out-0");
    assertNotEquals(matcher1, matcher2);
  }

  @Test
  void not_equals_null() {
    final BindingMatcher matcher = new BindingMatcher("produce-book-created-out-0");
    assertNotEquals(null, matcher);
  }

  // --- toString ---

  @Test
  void toString_returns_raw_value() {
    final BindingMatcher matcher = new BindingMatcher("regex:produce-.*-out-\\d+");
    assertEquals("regex:produce-.*-out-\\d+", matcher.toString());
  }
}
