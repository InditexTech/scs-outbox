package dev.inditex.scsoutbox.publish;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.NullMarked;

@RequiredArgsConstructor(staticName = "of")
@EqualsAndHashCode
@ToString
@NullMarked
public class GroupingKey {
  private final String value;

  public String asString() {
    return this.value;
  }
}
