package dev.inditex.scsoutbox.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupingKeyTest {

  @Test
  @DisplayName("of() creates instance with correct value")
  void of_creates_instance_with_correct_value() {
    final GroupingKey key = GroupingKey.of("abc");
    assertEquals("abc", key.asString());
  }

  @Test
  @DisplayName("asString returns the original value")
  void as_string_returns_original_value() {
    final GroupingKey key = GroupingKey.of("xyz");
    assertEquals("xyz", key.asString());
  }

  @Test
  @DisplayName("equals and hashCode are consistent for equal values")
  void equals_and_hash_code_are_consistent_for_equal_values() {
    final GroupingKey key1 = GroupingKey.of("same");
    final GroupingKey key2 = GroupingKey.of("same");
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
  }

  @Test
  @DisplayName("toString contains value")
  void to_string_contains_value() {
    final GroupingKey key = GroupingKey.of("val");
    assertTrue(key.toString().contains("val"));
  }

  @Test
  @DisplayName("different values are not equal")
  void different_values_are_not_equal() {
    final GroupingKey key1 = GroupingKey.of("a");
    final GroupingKey key2 = GroupingKey.of("b");
    assertNotEquals(key1, key2);
  }
}
