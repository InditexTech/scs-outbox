package dev.inditex.scsoutbox.publish.archive.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scs-outbox.publishing.archive")
public class ArchiveProperties {

  @Getter
  private final boolean jsonPayloadEnabled;

  public ArchiveProperties(final Boolean jsonPayloadEnabled) {
    this.jsonPayloadEnabled = jsonPayloadEnabled != null && jsonPayloadEnabled;
  }

}
