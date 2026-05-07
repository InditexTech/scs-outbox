package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.inditex.scsoutbox.OutboxMessageRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test to validate transaction rollback behavior in the outbox pattern.
 *
 * <p>This test ensures that: 1. Messages are not persisted when transactions roll back 2. The system maintains transactional integrity 3.
 * The database remains in a consistent state after rollback
 */
@SpringBootTest(
    classes = {TransactionRollbackIT.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.after-commit=false",
        "scs-outbox.publishing.scheduler.cron-expression=-",
        "app.scheduling.enable=false"
    })
@EnableAsync
@EnableAutoConfiguration
@EnableScheduling
@EnableTransactionManagement
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class TransactionRollbackIT {

  @Autowired
  private StreamBridge streamBridge;

  @Autowired
  private OutboxMessageRepository outboxMessageRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    this.transactionTemplate = new TransactionTemplate(this.transactionManager);

    // Clean up any existing messages to ensure clean state
    try {
      this.jdbcTemplate.execute("DELETE FROM scs_outbox");
    } catch (final Exception e) {
      // Table might not exist yet, ignore
    }

    // Verify clean state
    assertThat(this.outboxMessageRepository.count()).isZero();
  }

  @Test
  void shouldNotPersistMessagesWhenTransactionRollsBack() {
    // Given: Clean database state
    assertThat(this.outboxMessageRepository.count()).isZero();

    // When: Executing operations within a transaction that will be rolled back
    assertThatThrownBy(
        () -> {
          this.transactionTemplate.execute(
              status -> {
                // Capture multiple messages within the transaction
                this.streamBridge.send("output", "message1");
                this.streamBridge.send("output", "message2");
                this.streamBridge.send("output", "message3");

                // Force rollback by throwing an exception
                throw new RuntimeException("Forced rollback for test");
              });
        })
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Forced rollback for test");

    // Then: No messages should be persisted in the database
    assertThat(this.outboxMessageRepository.count()).isZero();
  }

  @Test
  void shouldHandlePartialRollbackWithSavepoints() {
    // Given: Clean state
    assertThat(this.outboxMessageRepository.count()).isZero();

    // When: Using manual transaction management with savepoints
    final DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    final TransactionStatus status = this.transactionManager.getTransaction(def);

    try {
      // First message before savepoint
      this.streamBridge.send("output", "before-savepoint");

      // Create savepoint
      final Object savepoint = status.createSavepoint();

      try {
        // Messages after savepoint
        this.streamBridge.send("output", "after-savepoint-1");
        this.streamBridge.send("output", "after-savepoint-2");

        // Rollback to savepoint
        status.rollbackToSavepoint(savepoint);

        // Message after rollback to savepoint
        this.streamBridge.send("output", "after-rollback-to-savepoint");

        // Commit the transaction
        this.transactionManager.commit(status);

      } catch (final Exception e) {
        status.rollbackToSavepoint(savepoint);
        this.transactionManager.commit(status);
      }

    } catch (final Exception e) {
      this.transactionManager.rollback(status);
    }

    // Then: Only messages before savepoint and after rollback should exist
    final long finalCount = this.outboxMessageRepository.count();
    assertThat(finalCount).isEqualTo(2); // before-savepoint and after-rollback-to-savepoint
  }

  @Test
  void shouldMaintainTransactionalIntegrityAcrossMultipleRollbacks() {
    // Given: Clean state
    assertThat(this.outboxMessageRepository.count()).isZero();

    // When: Executing multiple transactions with rollbacks
    for (int i = 0; i < 5; i++) {
      final int messageId = i;

      if (i % 2 == 0) {
        // Even iterations: successful transactions
        this.transactionTemplate.execute(
            status -> {
              this.streamBridge.send("output", "success-message-" + messageId);
              return null;
            });
      } else {
        // Odd iterations: failed transactions (rollback)
        assertThatThrownBy(
            () -> {
              this.transactionTemplate.execute(
                  status -> {
                    this.streamBridge.send("output", "failed-message-" + messageId);
                    throw new RuntimeException("Forced rollback " + messageId);
                  });
            })
                .isInstanceOf(RuntimeException.class);
      }
    }

    // Then: Only successful transactions should have persisted messages
    final long successfulMessages = this.outboxMessageRepository.count();
    assertThat(successfulMessages).isEqualTo(3); // Messages 0, 2, 4
  }

  @Test
  void shouldRecoverConsistentStateAfterRollback() {
    // Given: Some initial messages in successful transaction
    this.transactionTemplate.execute(
        status -> {
          this.streamBridge.send("output", "initial-message-1");
          this.streamBridge.send("output", "initial-message-2");
          return null;
        });

    final long initialCount = this.outboxMessageRepository.count();
    assertThat(initialCount).isEqualTo(2);

    // When: A transaction fails and rolls back
    assertThatThrownBy(
        () -> {
          this.transactionTemplate.execute(
              status -> {
                this.streamBridge.send("output", "failing-message-1");
                this.streamBridge.send("output", "failing-message-2");
                this.streamBridge.send("output", "failing-message-3");

                // Simulate database constraint violation or business logic failure
                throw new RuntimeException("Business logic failure");
              });
        })
            .isInstanceOf(RuntimeException.class);

    // Then: State should be exactly as before the failed transaction
    final long finalCount = this.outboxMessageRepository.count();
    assertThat(finalCount).isEqualTo(initialCount);

    // And: System should still be functional for new transactions
    this.transactionTemplate.execute(
        status -> {
          this.streamBridge.send("output", "recovery-message");
          return null;
        });

    assertThat(this.outboxMessageRepository.count()).isEqualTo(initialCount + 1);
  }
}
