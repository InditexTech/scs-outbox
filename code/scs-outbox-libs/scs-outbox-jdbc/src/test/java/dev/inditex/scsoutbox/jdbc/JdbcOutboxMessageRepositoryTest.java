package dev.inditex.scsoutbox.jdbc;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcOutboxMessageRepositoryTest extends AbstractJdbcOutboxMessageRepositoryTest {

  @Override
  public DataSource getDataSource() {
    return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
        .addScript("classpath:scripts/mariadb-outbox-table.sql")
        .build();
  }

}
