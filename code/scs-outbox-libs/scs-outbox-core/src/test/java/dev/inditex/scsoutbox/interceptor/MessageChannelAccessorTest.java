package dev.inditex.scsoutbox.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.integration.channel.AbstractMessageChannel;

class MessageChannelAccessorTest {

  @Test
  void when_message_channel_is_prefixed() {
    final MessageChannelAccessor accessor = new MessageChannelAccessor("appName");
    final AbstractMessageChannel messageChannel = mock(AbstractMessageChannel.class);
    when(messageChannel.getFullChannelName()).thenReturn("appName.bindingName");
    assertEquals("bindingName", accessor.getBindingName(messageChannel));
  }

  @Test
  void when_message_channel_is_not_prefixed() {
    final MessageChannelAccessor accessor = new MessageChannelAccessor("appName");
    final AbstractMessageChannel messageChannel = mock(AbstractMessageChannel.class);
    when(messageChannel.getFullChannelName()).thenReturn("bindingName");
    assertEquals("bindingName", accessor.getBindingName(messageChannel));
  }
}
