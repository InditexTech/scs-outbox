package dev.inditex.scsoutbox.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcOutboxDataSourceProviderTest {

  @Test
  void constructor_requires_capture_datasource() {
    assertThatThrownBy(() -> new JdbcOutboxDataSourceProvider(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Capture DataSource is required");
  }

  @Test
  void with_only_capture_datasource_uses_it_for_both() {
    final DataSource captureDs = this.createTestDataSource("capture");

    final JdbcOutboxDataSourceProvider provider = new JdbcOutboxDataSourceProvider(captureDs, null);

    assertThat(provider.getPrimary()).isSameAs(captureDs);
    assertThat(provider.getDedicatedForPublishing()).isSameAs(captureDs);
  }

  @Test
  void with_dedicated_publishing_datasource_uses_different_instances() {
    final DataSource captureDs = this.createTestDataSource("capture");
    final DataSource publishingDs = this.createTestDataSource("publishing");

    final JdbcOutboxDataSourceProvider provider = new JdbcOutboxDataSourceProvider(captureDs, publishingDs);

    assertThat(provider.getPrimary()).isSameAs(captureDs);
    assertThat(provider.getDedicatedForPublishing()).isSameAs(publishingDs);
    assertThat(provider.getPrimary()).isNotSameAs(provider.getDedicatedForPublishing());
  }

  @Test
  void metadata_is_cached_for_same_datasource() {
    final DataSource captureDs = this.createTestDataSource("capture");

    final JdbcOutboxDataSourceProvider provider = new JdbcOutboxDataSourceProvider(captureDs, null);

    final DataSourceMetadata captureMetadata = provider.getPrimaryDataSourceMetadata();
    final DataSourceMetadata publishingMetadata = provider.getDedicatedForPublishingDataSourceMetadata();

    // Should be the same instance (cached)
    assertThat(captureMetadata).isSameAs(publishingMetadata);
  }

  @Test
  void metadata_is_different_for_different_datasources() {
    final DataSource captureDs = this.createTestDataSource("capture");
    final DataSource publishingDs = this.createTestDataSource("publishing");

    final JdbcOutboxDataSourceProvider provider = new JdbcOutboxDataSourceProvider(captureDs, publishingDs);

    final DataSourceMetadata captureMetadata = provider.getPrimaryDataSourceMetadata();
    final DataSourceMetadata publishingMetadata = provider.getDedicatedForPublishingDataSourceMetadata();

    // Should be different instances
    assertThat(captureMetadata).isNotSameAs(publishingMetadata);
  }

  private DataSource createTestDataSource(String name) {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .setName(name)
        .build();
  }

}
