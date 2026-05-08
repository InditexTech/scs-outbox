package dev.inditex.scsoutbox.publish;

public class MessageNotPublishedException extends RuntimeException {

  public MessageNotPublishedException(final String message) {
    super(message);
  }
}
