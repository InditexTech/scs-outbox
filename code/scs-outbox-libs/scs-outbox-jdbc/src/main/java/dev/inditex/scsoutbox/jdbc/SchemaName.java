package dev.inditex.scsoutbox.jdbc;

/**
 * Represents a validated schema name.
 */
public record SchemaName(String value) {
  /**
   * Creates a new schema name with validation.
   *
   * @param value the schema name value
   * @throws IllegalArgumentException if the value is invalid
   */
  public SchemaName(String value) {
    this.value = DbNamingValidator.validate(value, this.getClass().getSimpleName());
  }

}
