package dev.inditex.scsoutbox.publish.archive.jdbc;

import javax.sql.DataSource;

import dev.inditex.scsoutbox.test.ContainerImages;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class MariaDbJdbcArchivedMessageRepositoryIT extends AbstractJdbcArchivedMessageRepositoryTest {

  @Container
  public static MariaDBContainer mariaDBContainer =
      new MariaDBContainer(DockerImageName.parse(ContainerImages.MARIADB))
          .withInitScript("scripts/mariadb-archive-table.sql");

  @BeforeAll
  static void startContainer() {
    mariaDBContainer.start();
  }

  @AfterAll
  static void stopContainer() {
    mariaDBContainer.stop();
  }

  @Override
  @SneakyThrows
  public DataSource getDataSource() {
    final MariaDbDataSource dataSource = new MariaDbDataSource();
    dataSource.setUrl(mariaDBContainer.getJdbcUrl());
    dataSource.setUser(mariaDBContainer.getUsername());
    dataSource.setPassword(mariaDBContainer.getPassword());
    return dataSource;
  }
}
