package dev.inditex.scsoutbox.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DestinationGroupingKeyGeneratorTest {

  private DestinationGroupingKeyGenerator generator;

  @BeforeEach
  void setUp() {
    this.generator = new DestinationGroupingKeyGenerator();
  }

  @Test
  @DisplayName("generate returns key with destination value")
  void generate_returns_key_with_destination_value() {
    final GroupingValues values = GroupingValues.of("my-destination", "binding", Collections.emptyMap());
    final GroupingKey key = this.generator.generate(values);
    assertEquals("my-destination", key.asString());
  }

  @Test
  @DisplayName("generate returns different keys for different destinations")
  void generate_returns_different_keys_for_different_destinations() {
    final GroupingKey key1 = this.generator.generate(GroupingValues.of("dest1", "binding", Collections.emptyMap()));
    final GroupingKey key2 = this.generator.generate(GroupingValues.of("dest2", "binding", Collections.emptyMap()));
    assertNotEquals(key1, key2);
  }

  @Test
  @DisplayName("generate returns same key for same destination")
  void generate_returns_same_key_for_same_destination() {
    final GroupingKey key1 = this.generator.generate(GroupingValues.of("dest", "binding1", Collections.emptyMap()));
    final GroupingKey key2 = this.generator.generate(GroupingValues.of("dest", "binding2", Collections.emptyMap()));
    assertEquals(key1, key2);
  }
}
