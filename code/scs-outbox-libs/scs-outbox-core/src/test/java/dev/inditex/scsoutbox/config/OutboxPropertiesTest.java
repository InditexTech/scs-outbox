package dev.inditex.scsoutbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboxPropertiesTest {

  @Nested
  class Constructor {

    @Test
    void when_null_bindings_expect_empty_bindings() {
      final OutboxProperties properties = new OutboxProperties(null);

      assertThat(properties.getBindings()).isNotNull();
    }
  }

  @Nested
  class BindingsConstructor {

    @Test
    void when_null_inclusions_and_exclusions_expect_empty_lists() {
      final Bindings bindings = new Bindings(null, null);

      assertThat(bindings.getInclusions()).isEmpty();
      assertThat(bindings.getExclusions()).isEmpty();
    }

    @Test
    void when_same_binding_in_both_lists_expect_illegal_argument_exception() {
      final String bindingName = "bindingName";

      assertThatThrownBy(() -> new Bindings(List.of(bindingName), List.of(bindingName)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_valid_regex_entries_expect_accepted() {
      final Bindings bindings = new Bindings(
          List.of("regex:produce-.*-out-\\d+"),
          List.of("regex:consume-.*"));

      assertThat(bindings.getInclusions()).hasSize(1);
      assertThat(bindings.getInclusions().get(0).isRegex()).isTrue();
      assertThat(bindings.getExclusions()).hasSize(1);
      assertThat(bindings.getExclusions().get(0).isRegex()).isTrue();
    }

    @Test
    void when_invalid_regex_in_inclusions_expect_illegal_argument_exception() {
      assertThatThrownBy(() -> new Bindings(List.of("regex:[invalid"), List.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_invalid_regex_in_exclusions_expect_illegal_argument_exception() {
      assertThatThrownBy(() -> new Bindings(List.of(), List.of("regex:[invalid")))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_regex_entries_in_both_lists_expect_no_conflict() {
      final Bindings bindings = new Bindings(
          List.of("regex:produce-.*"),
          List.of("regex:produce-book-.*"));

      assertThat(bindings.getInclusions()).isNotEmpty();
      assertThat(bindings.getExclusions()).isNotEmpty();
    }

    @Test
    void when_mixed_entries_with_different_exact_values_expect_no_conflict() {
      final Bindings bindings = new Bindings(
          List.of("binding-a", "regex:produce-.*"),
          List.of("binding-b", "regex:consume-.*"));

      assertThat(bindings.getInclusions()).hasSize(2);
      assertThat(bindings.getExclusions()).hasSize(2);
    }

    @Test
    void when_exact_conflict_mixed_with_regex_expect_illegal_argument_exception() {
      assertThatThrownBy(() -> new Bindings(
          List.of("binding-a", "regex:produce-.*"),
          List.of("binding-a", "regex:consume-.*")))
              .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
