package dev.inditex.scsoutbox.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.KafkaHeaders;

class KafkaKeyGroupingKeyGeneratorTest {

  private KafkaKeyGroupingKeyGenerator generator;

  @BeforeEach
  void setUp() {
    this.generator = new KafkaKeyGroupingKeyGenerator();
  }

  @Test
  @DisplayName("generate returns key with destination and kafka key")
  void generate_returns_key_with_destination_and_kafka_key() {
    final Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, "kafka-key");
    final GroupingValues values = GroupingValues.of("topic", "binding", headers);
    final GroupingKey key = this.generator.generate(values);
    assertEquals("topic-kafka-key", key.asString());
  }

  @Test
  @DisplayName("generate returns key with destination and null when kafka key is missing")
  void generate_returns_key_with_null_when_kafka_key_missing() {
    final GroupingValues values = GroupingValues.of("topic", "binding", Collections.emptyMap());
    final GroupingKey key = this.generator.generate(values);
    assertEquals("topic-null", key.asString());
  }

  @Test
  @DisplayName("generate returns different keys for different kafka keys")
  void generate_returns_different_keys_for_different_kafka_keys() {
    final Map<String, Object> headers1 = new HashMap<>();
    headers1.put(KafkaHeaders.KEY, "key1");
    final Map<String, Object> headers2 = new HashMap<>();
    headers2.put(KafkaHeaders.KEY, "key2");
    final GroupingValues values1 = GroupingValues.of("topic", "binding", headers1);
    final GroupingValues values2 = GroupingValues.of("topic", "binding", headers2);
    final GroupingKey key1 = this.generator.generate(values1);
    final GroupingKey key2 = this.generator.generate(values2);
    assertNotEquals(key1, key2);
  }

  @Test
  @DisplayName("generate returns same key for same destination and kafka key")
  void generate_returns_same_key_for_same_destination_and_kafka_key() {
    final Map<String, Object> headers = new HashMap<>();
    headers.put(KafkaHeaders.KEY, "same-key");
    final GroupingValues values1 = GroupingValues.of("topic", "binding1", headers);
    final GroupingValues values2 = GroupingValues.of("topic", "binding2", headers);
    final GroupingKey key1 = this.generator.generate(values1);
    final GroupingKey key2 = this.generator.generate(values2);
    assertEquals(key1, key2);
  }
}
