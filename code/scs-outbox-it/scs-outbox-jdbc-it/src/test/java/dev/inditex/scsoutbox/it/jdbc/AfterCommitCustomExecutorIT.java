package dev.inditex.scsoutbox.it.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration test validating that a custom {@code outboxAfterCommitExecutor} bean, explicitly named as documented in the README, is
 * actually used at runtime to execute {@code AfterCommitTrigger.afterCommit(..)}.
 *
 * <p>The custom executor uses a distinctive {@link Thread} naming convention. Since {@code Executors.newCachedThreadPool(..)} only creates
 * threads lazily on demand, and nothing else in the application submits work to this specific bean, observing a thread with the expected
 * name proves the after-commit trigger actually ran on this dedicated executor.
 */
@SpringBootTest(
    classes = {AfterCommitCustomExecutorIT.TestConfig.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.after-commit=true",
    })
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class AfterCommitCustomExecutorIT {

  static final String CUSTOM_THREAD_NAME_PREFIX = "custom-after-commit-";

  @Configuration
  @EnableAsync
  @EnableAutoConfiguration
  @EnableScheduling
  @EnableTransactionManagement
  static class TestConfig {

    @Bean(name = "outboxAfterCommitExecutor")
    public ExecutorService outboxAfterCommitExecutor() {
      final AtomicInteger threadCount = new AtomicInteger();
      return Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, CUSTOM_THREAD_NAME_PREFIX + threadCount.incrementAndGet());
        thread.setDaemon(true);
        return thread;
      });
    }
  }

  @Autowired
  private StreamBridge streamBridge;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Test
  void after_commit_runs_on_the_custom_named_executor() {
    this.transactionTemplate.execute(status -> this.streamBridge.send("output", "custom-executor"));

    await().atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(Thread.getAllStackTraces().keySet())
            .describedAs("a thread from the custom outboxAfterCommitExecutor bean should have been created to run afterCommit(..)")
            .anyMatch(thread -> thread.getName().startsWith(CUSTOM_THREAD_NAME_PREFIX)));
  }

}
