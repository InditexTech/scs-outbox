package dev.inditex.scsoutbox.metrics;

import static java.util.stream.StreamSupport.stream;

import java.util.List;

import dev.inditex.scsoutbox.OutboxServiceProperties;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.integration.support.management.IntegrationManagement;

@RequiredArgsConstructor
public class SpringIntegrationMeterFilter implements MeterFilter {

  private static final String NAME = "name";

  private static final String RESULT = "result";

  private static final String EXCEPTION = "exception";

  private final OutboxServiceProperties outboxServiceProperties;

  @Override
  public Id map(final Id id) {
    if (id.getName().equals(IntegrationManagement.SEND_TIMER_NAME)
        && this.outboxServiceProperties.isOutboxEnabledFor(id.getTag(NAME))
        && "failure".equals(id.getTag(RESULT))
        && "none".equals(id.getTag(EXCEPTION))) {
      final List<Tag> tags = stream(id.getTagsAsIterable().spliterator(), false)
          .map(t -> {
            if (!t.getKey().equals(RESULT)) {
              return t;
            }
            return Tag.of(RESULT, "success");
          }).toList();
      return id.replaceTags(tags);
    }
    return id;
  }

}
