package dev.inditex.scsoutbox;

import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutboxMessageTest {

  @Nested
  class Equals {

    @Test
    void when_same_id_expect_equal() {
      final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
      final UUID sameId = UUID.randomUUID();
      final String destination = "destination";
      final Instant capturedAt = Instant.parse("2025-01-01T00:00:00Z");
      final OutboxMessage outboxMessage = OutboxMessage.builder()
          .id(sameId)
          .payload(payload)
          .destination(destination)
          .bindingName("bindingName")
          .capturedAt(capturedAt)
          .headers(Map.of())
          .build();
      final OutboxMessage otherOutboxMessage = OutboxMessage.builder()
          .id(sameId)
          .payload(payload)
          .destination(destination)
          .bindingName("bindingName")
          .capturedAt(capturedAt)
          .headers(Map.of())
          .build();

      assertThat(outboxMessage).isEqualTo(otherOutboxMessage);
    }

    @Test
    void when_different_id_expect_not_equal() {
      final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
      final Instant capturedAt = Instant.parse("2025-01-01T00:00:00Z");
      final OutboxMessage outboxMessage = OutboxMessage.builder()
          .id(UUID.randomUUID())
          .payload(payload)
          .destination("destination")
          .bindingName("bindingName")
          .capturedAt(capturedAt)
          .headers(Map.of())
          .build();
      final OutboxMessage otherOutboxMessage = OutboxMessage.builder()
          .id(UUID.randomUUID())
          .payload(payload)
          .destination("destination")
          .bindingName("bindingName")
          .capturedAt(capturedAt)
          .headers(Map.of())
          .build();

      assertThat(outboxMessage).isNotEqualTo(otherOutboxMessage);
    }
  }

  @Nested
  class Build {

    static Stream<Arguments> nullFieldProvider() {
      return Stream.of(
          Arguments.of("id", (ThrowingCallable) () -> anOutboxMessageBuilder().id(null).build()),
          Arguments.of("payload", (ThrowingCallable) () -> anOutboxMessageBuilder().payload(null).build()),
          Arguments.of("destination", (ThrowingCallable) () -> anOutboxMessageBuilder().destination(null).build()),
          Arguments.of("bindingName", (ThrowingCallable) () -> anOutboxMessageBuilder().bindingName(null).build()),
          Arguments.of("capturedAt", (ThrowingCallable) () -> anOutboxMessageBuilder().capturedAt(null).build()),
          Arguments.of("headers", (ThrowingCallable) () -> anOutboxMessageBuilder().headers(null).build()));
    }

    @ParameterizedTest
    @MethodSource("nullFieldProvider")
    void when_required_field_is_null_expect_null_pointer_exception(final String fieldName, final ThrowingCallable callable) {
      assertThatThrownBy(callable)
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(fieldName + " is marked non-null but is null");
    }
  }

}
