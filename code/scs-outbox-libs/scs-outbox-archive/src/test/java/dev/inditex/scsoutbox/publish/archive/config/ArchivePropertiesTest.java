package dev.inditex.scsoutbox.publish.archive.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArchivePropertiesTest {

  @Test
  void json_payload_default_value() {
    final ArchiveProperties archiveProperties = new ArchiveProperties(null);
    assertFalse(archiveProperties.isJsonPayloadEnabled());
  }

  @Test
  void json_payload_enabled() {
    final ArchiveProperties archiveProperties = new ArchiveProperties(true);
    assertTrue(archiveProperties.isJsonPayloadEnabled());
  }

  @Test
  void json_payload_disabled() {
    final ArchiveProperties archiveProperties = new ArchiveProperties(false);
    assertFalse(archiveProperties.isJsonPayloadEnabled());
  }
}
