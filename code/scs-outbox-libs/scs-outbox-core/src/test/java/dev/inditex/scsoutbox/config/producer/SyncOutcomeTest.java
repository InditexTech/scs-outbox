package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SyncOutcomeTest {

  @Test
  void when_violation_has_no_declared_setting_expect_null_pointer_exception() {
    assertThatThrownBy(() -> SyncOutcome.violation(null))
        .isInstanceOf(NullPointerException.class);
  }
}
