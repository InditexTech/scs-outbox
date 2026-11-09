package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SyncProducerDiagnosticsTest {

  @Nested
  class ProducerSyncEnforcementDisabledMessage {

    @Test
    void expect_message_mentions_the_property_that_disables_enforcement_and_the_risk() {
      final String message = SyncProducerDiagnostics.producerSyncEnforcementDisabledMessage();

      assertThat(message)
          .contains(SyncProducerDiagnostics.ENFORCE_PRODUCER_SYNC_PROPERTY + "=false")
          .contains("fully responsible for configuring synchronous producers")
          .contains("messages may be lost");
    }
  }

  @Nested
  class UnsupportedBinderMessage {

    @Test
    void expect_message_mentions_the_binder_name_the_description_and_the_supported_binders() {
      final String message = SyncProducerDiagnostics.unsupportedBinderMessage("named-kafka", "some.Binder.Class");

      assertThat(message)
          .contains("named-kafka")
          .contains("some.Binder.Class")
          .contains("Supported binders: " + SyncProducerMappings.supportedBinders());
    }
  }

  @Nested
  class ViolationMessage {

    @Test
    void expect_message_lists_every_violation_with_binding_and_declared_property() {
      final Map<String, String> violations = new LinkedHashMap<>();
      violations.put("produce-book-out-0", "spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync=false");
      violations.put("produce-audit-out-0", "spring.cloud.stream.kafka.default.producer.sync=false");

      final String message = SyncProducerDiagnostics.violationMessage("named-kafka", violations);

      assertThat(message)
          .contains("named-kafka")
          .contains("binding 'produce-book-out-0': spring.cloud.stream.kafka.bindings.produce-book-out-0.producer.sync=false")
          .contains("binding 'produce-audit-out-0': spring.cloud.stream.kafka.default.producer.sync=false")
          .contains(SyncProducerDiagnostics.BINDINGS_EXCLUSIONS_PROPERTY);
    }

    @Test
    void expect_message_suggests_removing_the_property_or_excluding_the_binding() {
      final String message = SyncProducerDiagnostics.violationMessage("named-kafka", Map.of("produce-book-out-0", "sync=false"));

      assertThat(message)
          .contains("Fix it by removing that property")
          .contains("exclude it from the outbox via '" + SyncProducerDiagnostics.BINDINGS_EXCLUSIONS_PROPERTY + "'");
    }
  }
}
