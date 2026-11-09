package dev.inditex.scsoutbox.config.producer;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of enforcing synchronous publishing on a single {@link OutboxBinding}.
 *
 * <p>Returned instead of thrown so that {@link SyncProducerBinderListener} can enforce synchronous publishing on every outbox-enabled
 * binding of a binder before deciding whether to fail, instead of aborting on the first violation found.
 */
final class SyncOutcome {

  enum Status {
    /** The binding already published synchronously; nothing was changed. */
    ALREADY_SYNCHRONOUS,
    /** The binding published asynchronously and nothing was explicitly declared for it; scs-outbox switched it to synchronous. */
    CONFIGURED,
    /** The application explicitly declared this binding as asynchronous; scs-outbox left it untouched. */
    VIOLATION
  }

  private final Status status;

  private final DeclaredSetting declaredSetting;

  private SyncOutcome(final Status status, final DeclaredSetting declaredSetting) {
    this.status = status;
    this.declaredSetting = declaredSetting;
  }

  static SyncOutcome alreadySynchronous() {
    return new SyncOutcome(Status.ALREADY_SYNCHRONOUS, null);
  }

  static SyncOutcome configured() {
    return new SyncOutcome(Status.CONFIGURED, null);
  }

  static SyncOutcome violation(final DeclaredSetting declaredSetting) {
    return new SyncOutcome(Status.VIOLATION, Objects.requireNonNull(declaredSetting));
  }

  Status status() {
    return this.status;
  }

  /** The setting explicitly declared by the application, present only when {@link #status()} is {@link Status#VIOLATION}. */
  Optional<DeclaredSetting> declaredSetting() {
    return Optional.ofNullable(this.declaredSetting);
  }
}
