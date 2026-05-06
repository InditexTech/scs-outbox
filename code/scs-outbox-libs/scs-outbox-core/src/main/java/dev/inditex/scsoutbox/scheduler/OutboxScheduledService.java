package dev.inditex.scsoutbox.scheduler;

import dev.inditex.scsoutbox.publish.OutboxPublishingTask;
import dev.inditex.scsoutbox.publish.OutboxPublishingTaskReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class OutboxScheduledService {

  private final OutboxPublishingTask outboxPublishingTask;

  @Scheduled(
      cron = "${scs-outbox.publishing.scheduler.cron-expression:}",
      fixedRateString = "#{ ('${scs-outbox.publishing.scheduler.cron-expression:}' eq '') ?"
          + " ${scs-outbox.publishing.scheduler.fixed-rate:'5000'} :"
          + "${scs-outbox.publishing.scheduler.fixed-rate:''}}",
      initialDelayString = "${scs-outbox.publishing.scheduler.initial-delay:}")
  @SchedulerLock(name = "${scs-outbox.publishing.scheduler.task-name:outboxPublishingTask}",
      lockAtMostFor = "${scs-outbox.publishing.scheduler.lock-at-most-for:PT5m}")
  public void outboxPublishingTask() {
    final OutboxPublishingTaskReport report = this.outboxPublishingTask.run();
    log.debug(report.toString());
  }

}
