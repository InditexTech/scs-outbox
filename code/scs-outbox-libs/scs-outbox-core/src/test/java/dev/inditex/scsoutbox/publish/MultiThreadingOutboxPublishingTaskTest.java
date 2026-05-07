package dev.inditex.scsoutbox.publish;

import static dev.inditex.scsoutbox.OutboxMessageMother.anOutboxMessageBuilder;
import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.publish.config.PublishingProperties;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class MultiThreadingOutboxPublishingTaskTest extends AbstractOutboxPublishingTaskTest {

  @Test
  void multithreading_test() {
    // Given a
    // ThreadPool size of 5
    // and
    // 30 ms of send message delay
    // then
    // 150 messages can be published in less than 1.5 seconds
    final int ThreadPoolSize = 5;
    final int sendMessageDelay = 25;
    final int numOfMessages = 150;
    this.generateScenario(ThreadPoolSize, sendMessageDelay, numOfMessages);
    final List<OutboxMessage> unpublishedOutboxMessages = this.repository.findAllOrderByCapturedAt(UNLIMITED);

    final OutboxPublishingTaskReport report = this.task.run();
    assertThat(report.getDuration().toMillis()).isLessThan(1500);
    assertThat(report.getThroughput()).isGreaterThan(100);
    this.assertPublishedMessages(unpublishedOutboxMessages);
  }

  private void generateScenario(final int threadPoolSize, final int sendMessageDelay, final int numOfMessages) {
    this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    this.task = new OutboxPublishingTask(this.repository,
        new ParallelPublisher(this.executorService, this.publisher),
        new KeyGroupingStrategy(new DestinationGroupingKeyGenerator()),
        new PublishingProperties(null, null, null, null, null));
    when(this.messageSender.send(any())).thenAnswer(new AnswerWithDelay(sendMessageDelay, true));
    this.repository.init(buildMessages(numOfMessages));
  }

  private static List<OutboxMessage> buildMessages(final int numOfMessages) {
    final List<OutboxMessage> messages = new ArrayList<>(numOfMessages);
    for (int i = 1; i <= numOfMessages; i++) {
      final String destination = "" + i % 5;
      messages.add(anOutboxMessageBuilder()
          .destination(destination)
          .build());
    }
    return messages;
  }

  private class AnswerWithDelay implements Answer<Object> {

    private final int delay;

    private final Object response;

    public AnswerWithDelay(final int delay, final Object response) {
      this.delay = delay;
      this.response = response;
    }

    @Override
    @SuppressWarnings("java:S2925")
    public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
      TimeUnit.MILLISECONDS.sleep(this.delay);
      return this.response;
    }
  }
}
