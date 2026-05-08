package dev.inditex.scsoutbox.jdbc;

/**
 * Represents a validated table name.
 */
public record TableName(String value) {
  /**
   * Creates a new table name with validation.
   *
   * @param value the table name value
   * @throws IllegalArgumentException if the value is invalid
   */
  public TableName(String value) {
    this.value = DbNamingValidator.validate(value, this.getClass().getSimpleName());
  }

}
