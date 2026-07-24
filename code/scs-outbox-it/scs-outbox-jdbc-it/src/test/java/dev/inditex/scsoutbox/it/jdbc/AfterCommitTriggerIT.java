package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import dev.inditex.scsoutbox.OutboxMessageRepository;
import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger;
import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger.MessageCaptured;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    classes = {AfterCommitTriggerIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.after-commit=true",
    })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class AfterCommitTriggerIT {

  @Configuration
  @EnableAsync
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  static class TestConfig {

    /**
     * Deliberately tiny, unqualified default {@code @Async} executor (core=1, max=1, no queue), simulating the application-wide default
     * executor described in issue #45. Because {@code AfterCommitTrigger.afterCommit(..)} is explicitly qualified with
     * {@code outboxAfterCommitExecutor}, this bean must never be used by the after-commit trigger: a bulk transaction should still complete
     * successfully even though this bean alone would reject a burst of concurrent tasks.
     */
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
      final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(1);
      executor.setMaxPoolSize(1);
      executor.setQueueCapacity(0);
      executor.setThreadNamePrefix("bounded-default-async-");
      executor.initialize();
      return executor;
    }
  }

  @Autowired
  private StreamBridge streamBridge;

  @MockitoSpyBean
  private AfterCommitTrigger afterCommitTrigger;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private OutboxMessageRepository outboxMessageRepository;

  @Test
  void when_message_is_sent_then_publish_messages_is_captured_and_after_commit_is_executed() {
    this.transactionTemplate.execute(status -> this.streamBridge.send("output", "aftercommit"));

    verify(this.afterCommitTrigger).publishMessageCapturedEvent();
    await().atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(this.afterCommitTrigger).afterCommit(any(MessageCaptured.class)));
  }

  @Test
  void when_many_messages_are_captured_in_a_single_transaction_then_after_commit_runs_once_and_all_messages_are_published() {
    final int messageCount = 300;

    this.transactionTemplate.execute(status -> {
      for (int i = 0; i < messageCount; i++) {
        this.streamBridge.send("output", "bulk-" + i);
      }
      return null;
    });

    // One publishMessageCapturedEvent() invocation per captured message...
    verify(this.afterCommitTrigger, times(messageCount)).publishMessageCapturedEvent();

    // ...but exactly one coalesced afterCommit(..) invocation for the whole transaction, running on the dedicated
    // outboxAfterCommitExecutor (never on the tiny bounded "taskExecutor" default bean defined above).
    await().atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(this.afterCommitTrigger, times(1)).afterCommit(any(MessageCaptured.class)));

    // All captured messages are eventually published and removed from the outbox, with no task rejected in the process.
    await().atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(this.outboxMessageRepository.count()).isZero());
  }

}
