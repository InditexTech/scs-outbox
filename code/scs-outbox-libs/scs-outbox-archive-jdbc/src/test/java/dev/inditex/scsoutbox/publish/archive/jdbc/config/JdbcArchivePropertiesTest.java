package dev.inditex.scsoutbox.publish.archive.jdbc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

class JdbcArchivePropertiesTest {

  public static final String DEFAULT_TABLE_NAME = "SCS_OUTBOX_ARCHIVE";

  public static final String DEFAULT_SCHEMA = "";

  @Test
  void default_values() {
    assertEquals(DEFAULT_TABLE_NAME, new JdbcArchiveProperties().getTableName());
    assertEquals(DEFAULT_SCHEMA, new JdbcArchiveProperties().getSchema());
    assertEquals(DEFAULT_TABLE_NAME, new JdbcArchiveProperties(null, null).getTableName());
    assertEquals(DEFAULT_SCHEMA, new JdbcArchiveProperties(null, null).getSchema());
    assertEquals(DEFAULT_TABLE_NAME, new JdbcArchiveProperties("", "").getTableName());
    assertEquals(DEFAULT_SCHEMA, new JdbcArchiveProperties("", "").getSchema());
  }

  @Test
  void with_invalid_table_name() {
    assertThrows(IllegalArgumentException.class,
        () -> new JdbcArchiveProperties("VALUE WITH SPACES", null));
  }

  @Test
  void with_invalid_schema_name() {
    assertThrows(IllegalArgumentException.class,
        () -> new JdbcArchiveProperties(null, "SCHEMA WITH SPACES"));
  }

  @Test
  void with_table_name() {
    assertEquals("SCS_OUTBOX_TEST", new JdbcArchiveProperties("SCS_OUTBOX_TEST", null).getTableName());
  }

  @Test
  void with_schema_name() {
    assertEquals("TEST_SCHEMA", new JdbcArchiveProperties(null, "TEST_SCHEMA").getSchema());
  }

  @Test
  void with_full_table_name() {
    final JdbcArchiveProperties props = new JdbcArchiveProperties("SCS_OUTBOX_TEST", "TEST_SCHEMA");
    assertEquals("SCS_OUTBOX_TEST", props.getTableName());
    assertEquals("TEST_SCHEMA", props.getSchema());
  }

  @Test
  void with_full_table_name_no_schema() {
    JdbcArchiveProperties props = new JdbcArchiveProperties("SCS_OUTBOX_TEST", "");
    assertEquals("SCS_OUTBOX_TEST", props.getTableName());
    assertEquals("", props.getSchema());

    props = new JdbcArchiveProperties("SCS_OUTBOX_TEST", null);
    assertEquals("SCS_OUTBOX_TEST", props.getTableName());
    assertEquals("", props.getSchema());
  }

  @Nested
  @SpringBootTest(classes = {JdbcArchivePropertiesTest.class})
  @EnableConfigurationProperties(JdbcArchiveProperties.class)
  class SpringBootTestWithoutProperties {
    @Autowired
    private JdbcArchiveProperties properties;

    @Test
    void default_values() {
      assertEquals(DEFAULT_TABLE_NAME, this.properties.getTableName());
      assertEquals(DEFAULT_SCHEMA, this.properties.getSchema());
    }
  }

  @Nested
  @SpringBootTest(classes = {JdbcArchivePropertiesTest.class},
      properties = {
          "scs-outbox.publishing.archive.jdbc.table-name=SCS_OUTBOX_ARCHIVE_TEST",
          "scs-outbox.publishing.archive.jdbc.schema=TEST_SCHEMA"
      })
  @EnableConfigurationProperties(JdbcArchiveProperties.class)
  class SpringBootTestWithProperties {
    @Autowired
    private JdbcArchiveProperties properties;

    @Test
    void property_values() {
      assertEquals("SCS_OUTBOX_ARCHIVE_TEST", this.properties.getTableName());
      assertEquals("TEST_SCHEMA", this.properties.getSchema());
    }
  }

  @Nested
  @SpringBootTest(classes = {JdbcArchivePropertiesTest.class},
      properties = {
          "scs-outbox.publishing.archive.jdbc.table-name=SCS_OUTBOX_ARCHIVE_TEST"
      })
  @EnableConfigurationProperties(JdbcArchiveProperties.class)
  class SpringBootTestWithTableNameOnly {
    @Autowired
    private JdbcArchiveProperties properties;

    @Test
    void property_values() {
      assertEquals("SCS_OUTBOX_ARCHIVE_TEST", this.properties.getTableName());
      assertEquals(DEFAULT_SCHEMA, this.properties.getSchema());
    }
  }

}
