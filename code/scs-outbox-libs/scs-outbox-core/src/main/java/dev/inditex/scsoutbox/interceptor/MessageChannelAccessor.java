package dev.inditex.scsoutbox.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.MessageChannel;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MessageChannelAccessor {

  private final String appName;

  /**
   * In spring cloud stream 4.0.5 getFullChannelName return bindingName but in spring cloud stream 4.1.1 getFullChannelName return the.
   * following pattern: ${spring.application.name}.[bindingName]
   */
  public String getBindingName(final MessageChannel messageChannel) {
    final String fullChannelName = ((AbstractMessageChannel) messageChannel).getFullChannelName();
    if (StringUtils.hasText(this.appName)) {
      return fullChannelName.replace(this.appName + ".", "");
    }
    return fullChannelName;
  }

}
