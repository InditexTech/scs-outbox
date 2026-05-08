package dev.inditex.scsoutbox.publish;

import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.NullMarked;

@Getter
@EqualsAndHashCode
@ToString
@NullMarked
public class GroupingValues {
  private final String destination;

  private final String bindingName;

  private final Map<String, Object> messageHeaders;

  private GroupingValues(String destination, String bindingName, Map<String, Object> messageHeaders) {
    this.destination = destination;
    this.bindingName = bindingName;
    this.messageHeaders = Map.copyOf(messageHeaders);
  }

  public static GroupingValues of(String destination, String bindingName, Map<String, Object> messageHeaders) {
    return new GroupingValues(destination, bindingName, messageHeaders);
  }
}
