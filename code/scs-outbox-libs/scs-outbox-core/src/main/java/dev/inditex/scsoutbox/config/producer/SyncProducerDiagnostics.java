package dev.inditex.scsoutbox.config.producer;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the messages logged or thrown by {@link SyncProducerBinderListener}.
 *
 * <p>Kept separate from the listener so the wording of each message can be read, tested and changed independently of the orchestration
 * logic that decides when each one applies.
 */
final class SyncProducerDiagnostics {

  /** Property that globally disables the automatic synchronous producer configuration. */
  static final String SYNC_PRODUCERS_ENABLED_PROPERTY = "scs-outbox.bindings.sync-producers.enabled";

  /** Property used to remove a binding from outbox management, suggested as a fix for a binding that must publish asynchronously. */
  static final String BINDINGS_EXCLUSIONS_PROPERTY = "scs-outbox.bindings.exclusions";

  private static final String VIOLATION_MESSAGE_TEMPLATE = """
      The following outbox-enabled bindings of binder [%s] are explicitly configured to publish asynchronously, which breaks \
      the delivery guarantee of the transactional outbox (the outbox record is deleted as soon as StreamBridge.send returns \
      true, which for an asynchronous producer happens before the broker acknowledges the record):
      %s
      Fix it by removing that property so scs-outbox can configure it, or, if this binding must genuinely publish asynchronously, \
      exclude it from the outbox via '%s' so it is no longer managed by scs-outbox.""";

  private SyncProducerDiagnostics() {
  }

  /** Message logged when {@link #SYNC_PRODUCERS_ENABLED_PROPERTY} disables the automatic synchronous producer configuration. */
  static String autoConfigurationDisabledMessage() {
    return "Automatic synchronous producer configuration is disabled ('"
        + SYNC_PRODUCERS_ENABLED_PROPERTY + "=false')."
        + " The application is fully responsible for configuring synchronous producers on outbox-enabled bindings;"
        + " messages may be lost if a producer publishes asynchronously.";
  }

  /**
   * Message logged when scs-outbox cannot enable synchronous producers automatically for a binder.
   *
   * @param binderConfigurationName the binder configuration name
   * @param description identifies the binder for the operator, e.g. its class name or its defaults prefix
   */
  static String unsupportedBinderMessage(final String binderConfigurationName, final String description) {
    return "scs-outbox cannot enable synchronous producers automatically for the bindings of binder ["
        + binderConfigurationName + "] ("
        + description + ")."
        + " Supported binders: " + SyncProducerMappings.supportedBinders() + "."
        + " Configure synchronous publishing manually for those bindings, otherwise messages may be lost:"
        + " the outbox record is deleted as soon as StreamBridge.send returns true.";
  }

  /**
   * Message thrown when one or more outbox-enabled bindings are explicitly configured to publish asynchronously.
   *
   * @param binderConfigurationName the binder configuration name
   * @param violations binding name to {@code property=value} pair explicitly declared by the application
   */
  static String violationMessage(final String binderConfigurationName, final Map<String, String> violations) {
    final String details = violations.entrySet().stream()
        .map(entry -> "  - binding '" + entry.getKey() + "': " + entry.getValue())
        .collect(Collectors.joining(System.lineSeparator()));
    return VIOLATION_MESSAGE_TEMPLATE.formatted(binderConfigurationName, details, BINDINGS_EXCLUSIONS_PROPERTY);
  }
}
