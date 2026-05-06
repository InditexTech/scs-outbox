package dev.inditex.scsoutbox.jdbc;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.test.ContainerImages;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgreSqlJdbcOutboxMessageRepositoryIT extends AbstractJdbcOutboxMessageRepositoryTest {

  @Container
  public static PostgreSQLContainer postgreSqlContainer =
      new PostgreSQLContainer(DockerImageName.parse(ContainerImages.POSTGRESQL))
          .withInitScript("scripts/postgresql-outbox-table.sql");

  @BeforeAll
  static void startContainer() {
    postgreSqlContainer.start();
  }

  @AfterAll
  static void stopContainer() {
    postgreSqlContainer.stop();
  }

  @Override
  public DataSource getDataSource() {
    final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(postgreSqlContainer.getJdbcUrl());
    dataSource.setUser(postgreSqlContainer.getUsername());
    dataSource.setPassword(postgreSqlContainer.getPassword());
    return dataSource;
  }

}
