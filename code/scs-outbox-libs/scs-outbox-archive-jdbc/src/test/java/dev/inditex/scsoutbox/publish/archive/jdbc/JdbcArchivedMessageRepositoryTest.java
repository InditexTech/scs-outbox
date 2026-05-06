package dev.inditex.scsoutbox.publish.archive.jdbc;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcArchivedMessageRepositoryTest extends AbstractJdbcArchivedMessageRepositoryTest {

  @Override
  public DataSource getDataSource() {
    return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
        .addScript("classpath:scripts/mariadb-archive-table.sql")
        .build();
  }

}
