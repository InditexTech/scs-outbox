package dev.inditex.scsoutbox.test;

/**
 * Docker image names for all databases and brokers used in integration tests.
 *
 * <p>Centralizes container image versions so they can be updated in a single place across all modules. Consume this module with
 * {@code <scope>test</scope>}.
 */
public final class ContainerImages {

  /** MongoDB container image. */
  public static final String MONGO = "mongo:7.0.14";

  /** MariaDB container image. */
  public static final String MARIADB = "mariadb:10.11.11";

  /** PostgreSQL container image. */
  public static final String POSTGRESQL = "postgres:16.8";

  private ContainerImages() {
  }
}
