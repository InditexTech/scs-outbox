package dev.inditex.scsoutbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;

class OutboxServicePropertiesTest {

  private List<String> inclusionsList;

  private List<String> exclusionsList;

  private OutboxServiceProperties outboxServiceProperties;

  private BindingServiceProperties bindingServiceProperties;

  @BeforeEach
  void setUp() {
    this.inclusionsList = new ArrayList<>();
    this.exclusionsList = new ArrayList<>();
    final Bindings bindings = new Bindings(this.inclusionsList, this.exclusionsList);
    final OutboxProperties properties = new OutboxProperties(bindings);
    this.bindingServiceProperties = mock(BindingServiceProperties.class);
    this.outboxServiceProperties = new OutboxServiceProperties(properties, this.bindingServiceProperties);

  }

  @Test
  void enabled_by_default() {
    final String bindingName = "bindingName";

    final boolean enabled = this.outboxServiceProperties.isOutboxEnabledFor(bindingName);

    assertTrue(enabled);
  }

  @Test
  void enabled_if_inclusion_list_contains_binding_name() {
    final String bindingName = "bindingName";
    this.inclusionsList.add(bindingName);
    this.updateLists(this.inclusionsList, this.exclusionsList);

    final boolean enabled = this.outboxServiceProperties.isOutboxEnabledFor(bindingName);

    assertTrue(enabled);
  }

  @Test
  void disabled_if_exclusion_list_contains_binding_name() {
    final String bindingName = "bindingName";
    this.exclusionsList.add(bindingName);
    this.updateLists(this.inclusionsList, this.exclusionsList);

    final boolean enabled = this.outboxServiceProperties.isOutboxEnabledFor(bindingName);

    assertFalse(enabled);
  }

  @Test
  void disabled_if_inclusions_list_not_contains_binding_name() {
    this.inclusionsList.add("anyBindingName");
    this.updateLists(this.inclusionsList, this.exclusionsList);

    final boolean enabled = this.outboxServiceProperties.isOutboxEnabledFor("anyOtherBindingName");

    assertFalse(enabled);
  }

  @Test
  void enabled_if_exclusions_list_not_contains_binding_name() {
    this.exclusionsList.add("anyBindingName");
    this.updateLists(this.inclusionsList, this.exclusionsList);

    final boolean enabled = this.outboxServiceProperties.isOutboxEnabledFor("anyOtherBindingName");

    assertTrue(enabled);
  }

  @Test
  void disabled_if_exclusions_and_exclusions_list_not_contain_binding_name() {
    this.inclusionsList.add("anyBindingName");
    this.exclusionsList.add("anyOtherBindingName");
    this.updateLists(this.inclusionsList, this.exclusionsList);

    final boolean enabled = this.outboxServiceProperties.isOutboxEnabledFor("anotherBindingName");

    assertFalse(enabled);
  }

  @Test
  void on_initialize_validate_that_binding_names_are_included_in_scs_binding_names() {
    final String includedBindingName = "includedBindingName";
    final String notIncludedBindingName1 = "notIncludedBindingName1";
    final String notIncludedBindingName2 = "notIncludedBindingName2";
    when(this.bindingServiceProperties.getBindings()).thenReturn(Map.of(includedBindingName, new BindingProperties()));
    this.inclusionsList.add(includedBindingName);
    this.inclusionsList.add(notIncludedBindingName1);
    this.exclusionsList.add(notIncludedBindingName2);
    this.updateLists(this.inclusionsList, this.exclusionsList);

    final IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> this.outboxServiceProperties.afterPropertiesSet());
    final String exceptionMessage = exception.getMessage();
    assertTrue(
        exceptionMessage.contains(notIncludedBindingName1)
            && exceptionMessage.contains(notIncludedBindingName2)
            && !exceptionMessage.contains(includedBindingName));
  }

  private void updateLists(final List<String> inclusionsList, final List<String> exclusionsList) {
    final Bindings bindings = new Bindings(inclusionsList, exclusionsList);
    final OutboxProperties properties = new OutboxProperties(bindings);
    this.outboxServiceProperties = new OutboxServiceProperties(properties, this.bindingServiceProperties);
  }

  @Test
  void get_destination_from_binding_name() {
    final String bindingName = "bindingName";
    final String destination = "destination";
    final BindingProperties bindingProperties = new BindingProperties();
    bindingProperties.setDestination(destination);
    when(this.bindingServiceProperties.getBindingProperties(bindingName)).thenReturn(bindingProperties);

    final String result = this.outboxServiceProperties.getDestination(bindingName);

    assertEquals(destination, result);
  }

  @Test
  void use_native_encoding_from_binding_name() {
    final String bindingName = "bindingName";
    final ProducerProperties producerProperties = new ProducerProperties();
    producerProperties.setUseNativeEncoding(true);
    when(this.bindingServiceProperties.getProducerProperties(bindingName)).thenReturn(producerProperties);

    final boolean result = this.outboxServiceProperties.useNativeEncoding(bindingName);

    assertTrue(result);
  }

  @Nested
  class RegexBindingMatchingTest {

    @Test
    void enabled_if_regex_inclusion_matches_binding_name() {
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      final boolean enabled =
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("produce-book-created-out-0");

      assertTrue(enabled);
    }

    @Test
    void disabled_if_regex_inclusion_does_not_match_binding_name() {
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      final boolean enabled =
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("consume-book-created-in-0");

      assertFalse(enabled);
    }

    @Test
    void disabled_if_regex_exclusion_matches_binding_name() {
      OutboxServicePropertiesTest.this.exclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      final boolean enabled =
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("produce-book-created-out-0");

      assertFalse(enabled);
    }

    @Test
    void enabled_if_regex_exclusion_does_not_match_binding_name() {
      OutboxServicePropertiesTest.this.exclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      final boolean enabled =
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("consume-book-created-in-0");

      assertTrue(enabled);
    }

    @Test
    void disabled_if_regex_exclusion_matches_even_when_regex_inclusion_also_matches() {
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.exclusionsList.add("regex:produce-book-.*");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      final boolean enabled =
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("produce-book-created-out-0");

      assertFalse(enabled);
    }

    @Test
    void enabled_with_mixed_exact_and_regex_inclusion() {
      OutboxServicePropertiesTest.this.inclusionsList.add("exact-binding-name");
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      assertTrue(
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("exact-binding-name"));
      assertTrue(
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("produce-book-created-out-0"));
      assertFalse(
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("other-binding"));
    }

    @Test
    void disabled_with_exact_exclusion_overriding_regex_inclusion() {
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.exclusionsList.add("produce-book-created-out-0");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      final boolean enabled =
          OutboxServicePropertiesTest.this.outboxServiceProperties.isOutboxEnabledFor("produce-book-created-out-0");

      assertFalse(enabled);
    }

    @Test
    void regex_entries_are_not_validated_against_scs_bindings_on_initialize() {
      when(OutboxServicePropertiesTest.this.bindingServiceProperties.getBindings())
          .thenReturn(Map.of("produce-book-created-out-0", new BindingProperties()));
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.exclusionsList.add("regex:consume-.*");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      assertDoesNotThrow(() -> OutboxServicePropertiesTest.this.outboxServiceProperties.afterPropertiesSet());
    }

    @Test
    void mixed_exact_and_regex_entries_validation_only_applies_to_exact_entries() {
      when(OutboxServicePropertiesTest.this.bindingServiceProperties.getBindings())
          .thenReturn(Map.of("produce-book-created-out-0", new BindingProperties()));
      OutboxServicePropertiesTest.this.inclusionsList.add("produce-book-created-out-0");
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.exclusionsList.add("regex:consume-.*");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      assertDoesNotThrow(() -> OutboxServicePropertiesTest.this.outboxServiceProperties.afterPropertiesSet());
    }

    @Test
    void exact_entry_not_in_scs_bindings_still_fails_when_mixed_with_regex() {
      when(OutboxServicePropertiesTest.this.bindingServiceProperties.getBindings())
          .thenReturn(Map.of("produce-book-created-out-0", new BindingProperties()));
      OutboxServicePropertiesTest.this.inclusionsList.add("non-existent-binding");
      OutboxServicePropertiesTest.this.inclusionsList.add("regex:produce-.*-out-\\d+");
      OutboxServicePropertiesTest.this.updateLists(
          OutboxServicePropertiesTest.this.inclusionsList, OutboxServicePropertiesTest.this.exclusionsList);

      final IllegalArgumentException exception =
          assertThrows(IllegalArgumentException.class,
              () -> OutboxServicePropertiesTest.this.outboxServiceProperties.afterPropertiesSet());
      assertTrue(exception.getMessage().contains("non-existent-binding"));
    }
  }

}
