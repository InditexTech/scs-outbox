package dev.inditex.scsoutbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.inditex.scsoutbox.config.OutboxProperties.Bindings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

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

    @Test
    void when_two_argument_constructor_expect_producer_sync_enforced_by_default() {
      final Bindings bindings = new Bindings(List.of(), List.of());

      assertThat(bindings.isEnforceProducerSync()).isTrue();
    }

    @Test
    void when_null_enforce_producer_sync_expect_enforced_by_default() {
      final Bindings bindings = new Bindings(List.of(), List.of(), null);

      assertThat(bindings.isEnforceProducerSync()).isTrue();
    }

    @Test
    void when_producer_sync_enforcement_disabled_expect_disabled() {
      final Bindings bindings = new Bindings(List.of(), List.of(), false);

      assertThat(bindings.isEnforceProducerSync()).isFalse();
    }
  }

  @Nested
  @SpringBootTest(
      classes = {OutboxPropertiesTest.class},
      properties = {"scs-outbox.bindings.enforce-producer-sync=false"})
  @EnableConfigurationProperties(OutboxProperties.class)
  class SpringBootBinding {

    @Autowired
    private OutboxProperties outboxProperties;

    @Test
    void when_producer_sync_enforcement_is_configured_expect_bound_value() {
      assertThat(this.outboxProperties.getBindings().isEnforceProducerSync()).isFalse();
    }
  }

  @Nested
  class BindingsMatches {

    @Test
    void when_no_inclusions_and_no_exclusions_expect_all_bindings_matched() {
      final Bindings bindings = new Bindings(List.of(), List.of());

      assertThat(bindings.matches("any-binding-out-0")).isTrue();
    }

    @Test
    void when_only_exclusions_expect_all_but_excluded_matched() {
      final Bindings bindings = new Bindings(List.of(), List.of("excluded-out-0"));

      assertThat(bindings.matches("included-out-0")).isTrue();
      assertThat(bindings.matches("excluded-out-0")).isFalse();
    }

    @Test
    void when_inclusions_expect_only_included_matched() {
      final Bindings bindings = new Bindings(List.of("included-out-0"), List.of());

      assertThat(bindings.matches("included-out-0")).isTrue();
      assertThat(bindings.matches("other-out-0")).isFalse();
    }

    @Test
    void when_binding_matches_inclusion_and_exclusion_regex_expect_exclusion_wins() {
      final Bindings bindings = new Bindings(List.of("regex:produce-.*"), List.of("regex:produce-audit-.*"));

      assertThat(bindings.matches("produce-book-out-0")).isTrue();
      assertThat(bindings.matches("produce-audit-out-0")).isFalse();
    }
  }
}
