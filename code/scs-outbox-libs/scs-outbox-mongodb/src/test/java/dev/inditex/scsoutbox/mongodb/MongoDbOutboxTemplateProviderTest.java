package dev.inditex.scsoutbox.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

class MongoDbOutboxTemplateProviderTest {

  @Test
  void constructor_requires_capture_template() {
    assertThatThrownBy(() -> new MongoDbOutboxTemplateProvider(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Capture MongoTemplate is required");
  }

  @Test
  void with_only_capture_template_uses_it_for_both() {
    final MongoTemplate captureTemplate = mock(MongoTemplate.class);

    final MongoDbOutboxTemplateProvider provider = new MongoDbOutboxTemplateProvider(captureTemplate, null);

    assertThat(provider.getPrimary()).isSameAs(captureTemplate);
    assertThat(provider.getDedicatedForPublishing()).isSameAs(captureTemplate);
  }

  @Test
  void with_dedicated_publishing_template_uses_different_instances() {
    final MongoTemplate captureTemplate = mock(MongoTemplate.class);
    final MongoTemplate publishingTemplate = mock(MongoTemplate.class);

    final MongoDbOutboxTemplateProvider provider =
        new MongoDbOutboxTemplateProvider(captureTemplate, publishingTemplate);

    assertThat(provider.getPrimary()).isSameAs(captureTemplate);
    assertThat(provider.getDedicatedForPublishing()).isSameAs(publishingTemplate);
    assertThat(provider.getPrimary()).isNotSameAs(provider.getDedicatedForPublishing());
  }

}
