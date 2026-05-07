package dev.inditex.scsoutbox.it.jdbc;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger;
import dev.inditex.scsoutbox.scheduler.AfterCommitTrigger.MessageCaptured;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    classes = {AfterCommitTriggerIT.class},
    properties = {
        "spring.docker.compose.enabled=true",
        "spring.docker.compose.skip.in-tests=false",
        "scs-outbox.publishing.after-commit=true",
    })
@EnableAsync
@EnableAutoConfiguration
@EnableScheduling
@EnableTransactionManagement
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class AfterCommitTriggerIT {

  @Autowired
  private StreamBridge streamBridge;

  @MockitoSpyBean
  private AfterCommitTrigger afterCommitTrigger;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Test
  void when_message_is_sent_then_publish_messages_is_captured_and_after_commit_is_executed() {
    this.transactionTemplate.execute(status -> this.streamBridge.send("output", "aftercommit"));

    verify(this.afterCommitTrigger).publishMessageCapturedEvent();
    await().atMost(5, TimeUnit.SECONDS)
        .untilAsserted(() -> verify(this.afterCommitTrigger).afterCommit(any(MessageCaptured.class)));
  }

}
