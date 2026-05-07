package dev.inditex.scsoutbox.jdbc;

import java.util.regex.Pattern;

/**
 * Utility class for database object name validation. Provides common validation logic for schema and table names.
 */
public final class DbNamingValidator {
  private static final int MAX_LENGTH = 128;

  private static final Pattern VALID_CHARACTERS_PATTERN = Pattern.compile("^\\w+$");

  private DbNamingValidator() {
    // Utility class should not be instantiated
  }

  /**
   * Validates a database object name.
   *
   * @param value the name value to validate
   * @param nameType the type of name (e.g., "Schema", "Table")
   * @return the validated value
   * @throws IllegalArgumentException if the value is invalid
   */
  public static String validate(String value, String nameType) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(nameType + " value cannot be null or empty");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(nameType + " value cannot exceed " + MAX_LENGTH + " characters");
    }
    if (!VALID_CHARACTERS_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(nameType + " value can only contain alphanumeric characters and underscores");
    }
    return value;
  }
}
