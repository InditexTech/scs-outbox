package dev.inditex.scsoutbox.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbSchemaResolverTest {

  private DataSourceMetadata dataSourceMetadata;

  private DbSchemaResolver dbSchemaResolver;

  @BeforeEach
  void setUp() {
    this.dataSourceMetadata = mock(DataSourceMetadata.class);
    this.dbSchemaResolver = new DbSchemaResolver(this.dataSourceMetadata);
  }

  @Test
  void should_return_configured_schema_when_provided() {
    // Given
    final String configuredSchema = "configured_schema";

    // When
    final String result = this.dbSchemaResolver.resolve(configuredSchema);

    // Then
    assertThat(result).isEqualTo(configuredSchema);
  }

  @Test
  void should_return_default_schema_when_configured_schema_is_null() {
    // Given
    final String defaultSchema = "default_schema";
    when(this.dataSourceMetadata.getDefaultSchema()).thenReturn(defaultSchema);

    // When
    final String result = this.dbSchemaResolver.resolve(null);

    // Then
    assertThat(result).isEqualTo(defaultSchema);
  }

  @Test
  void should_return_default_schema_when_configured_schema_is_empty() {
    // Given
    final String defaultSchema = "default_schema";
    when(this.dataSourceMetadata.getDefaultSchema()).thenReturn(defaultSchema);

    // When
    final String result = this.dbSchemaResolver.resolve("");

    // Then
    assertThat(result).isEqualTo(defaultSchema);
  }

  @Test
  void should_throw_exception_when_no_schema_can_be_resolved() {
    // Given
    when(this.dataSourceMetadata.getDefaultSchema()).thenReturn(null);

    // When/Then
    assertThatThrownBy(() -> this.dbSchemaResolver.resolve(null))
        .isInstanceOf(DbSchemaResolver.SchemaResolutionException.class)
        .hasMessageContaining("No schemaName configured and could not detect default schemaName");
  }

  @Test
  void should_throw_exception_when_default_schema_is_empty() {
    // Given
    when(this.dataSourceMetadata.getDefaultSchema()).thenReturn("");

    // When/Then
    assertThatThrownBy(() -> this.dbSchemaResolver.resolve(null))
        .isInstanceOf(DbSchemaResolver.SchemaResolutionException.class)
        .hasMessageContaining("No schemaName configured and could not detect default schemaName");
  }
}
