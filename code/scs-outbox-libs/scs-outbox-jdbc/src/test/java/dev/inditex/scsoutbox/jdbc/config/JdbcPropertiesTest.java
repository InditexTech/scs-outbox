package dev.inditex.scsoutbox.jdbc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

class JdbcPropertiesTest {

  public static final String DEFAULT_TABLE_NAME = "SCS_OUTBOX";

  public static final String DEFAULT_SCHEMA = "";

  @Test
  void default_values() {
    assertEquals(DEFAULT_TABLE_NAME, new JdbcProperties().getTableName());
    assertEquals(DEFAULT_SCHEMA, new JdbcProperties().getSchema());
    assertEquals(DEFAULT_TABLE_NAME, new JdbcProperties(null, null).getTableName());
    assertEquals(DEFAULT_SCHEMA, new JdbcProperties(null, null).getSchema());
    assertEquals(DEFAULT_TABLE_NAME, new JdbcProperties("", "").getTableName());
    assertEquals(DEFAULT_SCHEMA, new JdbcProperties("", "").getSchema());
  }

  @Test
  void with_invalid_table_name() {
    assertThrows(IllegalArgumentException.class,
        () -> new JdbcProperties("VALUE WITH SPACES", null));
  }

  @Test
  void with_invalid_schema_name() {
    assertThrows(IllegalArgumentException.class,
        () -> new JdbcProperties(null, "SCHEMA WITH SPACES"));
  }

  @Test
  void with_table_name() {
    assertEquals("SCS_OUTBOX_TEST", new JdbcProperties("SCS_OUTBOX_TEST", null).getTableName());
  }

  @Test
  void with_schema_name() {
    assertEquals("TEST_SCHEMA", new JdbcProperties(null, "TEST_SCHEMA").getSchema());
  }

  @Nested
  @SpringBootTest(classes = {JdbcPropertiesTest.class})
  @EnableConfigurationProperties(JdbcProperties.class)
  class SpringBootTestWithoutProperties {
    @Autowired
    private JdbcProperties properties;

    @Test
    void default_values() {
      assertEquals(DEFAULT_TABLE_NAME, this.properties.getTableName());
      assertEquals(DEFAULT_SCHEMA, this.properties.getSchema());
    }
  }

  @Nested
  @SpringBootTest(classes = {JdbcPropertiesTest.class},
      properties = {
          "scs-outbox.jdbc.table-name=SCS_OUTBOX_TEST",
          "scs-outbox.jdbc.schema=TEST_SCHEMA"
      })
  @EnableConfigurationProperties(JdbcProperties.class)
  class SpringBootTestWithProperties {
    @Autowired
    private JdbcProperties properties;

    @Test
    void property_values() {
      assertEquals("SCS_OUTBOX_TEST", this.properties.getTableName());
      assertEquals("TEST_SCHEMA", this.properties.getSchema());
    }
  }

  @Nested
  @SpringBootTest(classes = {JdbcPropertiesTest.class},
      properties = {
          "scs-outbox.jdbc.table-name=SCS_OUTBOX_TEST"
      })
  @EnableConfigurationProperties(JdbcProperties.class)
  class SpringBootTestWithTableNameOnly {
    @Autowired
    private JdbcProperties properties;

    @Test
    void property_values() {
      assertEquals("SCS_OUTBOX_TEST", this.properties.getTableName());
      assertEquals(DEFAULT_SCHEMA, this.properties.getSchema());
    }
  }

}
