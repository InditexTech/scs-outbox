package dev.inditex.scsoutbox.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GroupingValuesTest {

  @Test
  @DisplayName("of() creates instance with correct values")
  void of_creates_instance_with_correct_values() {
    final String destination = "topic";
    final String bindingName = "binding";
    final Map<String, Object> headers = new HashMap<>();
    headers.put("key", "value");

    final GroupingValues groupingValues = GroupingValues.of(destination, bindingName, headers);

    assertEquals(destination, groupingValues.getDestination());
    assertEquals(bindingName, groupingValues.getBindingName());
    assertEquals(headers, groupingValues.getMessageHeaders());
  }

  @Test
  @DisplayName("messageHeaders is unmodifiable")
  void message_headers_is_unmodifiable() {
    final GroupingValues groupingValues = GroupingValues.of("dest", "bind", Collections.singletonMap("k", "v"));
    final Map<String, Object> messageHeaders = groupingValues.getMessageHeaders();
    assertThrows(UnsupportedOperationException.class, () -> messageHeaders.put("x", 1));
  }

  @Test
  @DisplayName("equals and hashCode are consistent for equal values")
  void equals_and_hash_code_are_consistent_for_equal_values() {
    final Map<String, Object> headers1 = new HashMap<>();
    headers1.put("a", 1);
    final Map<String, Object> headers2 = new HashMap<>();
    headers2.put("a", 1);
    final GroupingValues g1 = GroupingValues.of("d", "b", headers1);
    final GroupingValues g2 = GroupingValues.of("d", "b", headers2);
    assertEquals(g1, g2);
    assertEquals(g1.hashCode(), g2.hashCode());
  }

  @Test
  @DisplayName("toString contains all fields")
  void to_string_contains_all_fields() {
    final GroupingValues groupingValues = GroupingValues.of("dest", "bind", Collections.singletonMap("k", "v"));
    final String str = groupingValues.toString();
    assertTrue(str.contains("dest"));
    assertTrue(str.contains("bind"));
    assertTrue(str.contains("k"));
    assertTrue(str.contains("v"));
  }

  @Nested
  class EdgeCases {
    @Test
    @DisplayName("of() with empty headers map")
    void of_with_empty_headers() {
      final GroupingValues groupingValues = GroupingValues.of("d", "b", Collections.emptyMap());
      assertTrue(groupingValues.getMessageHeaders().isEmpty());
    }
  }
}
