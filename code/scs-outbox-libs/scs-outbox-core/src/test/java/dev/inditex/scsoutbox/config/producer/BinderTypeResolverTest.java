package dev.inditex.scsoutbox.config.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class BinderTypeResolverTest {

  private static final ClassLoader NO_BINDERS = new URLClassLoader(new URL[0], null);

  @Nested
  class DeclaredBinderOnBinding {

    @Test
    void when_binding_declares_binder_name_without_type_expect_name_used_as_type() {
      final BinderTypeResolver resolver = new BinderTypeResolver(new MockEnvironment(), NO_BINDERS);

      assertThat(resolver.resolve("produce-book-out-0", "kafka")).contains("kafka");
    }

    @Test
    void when_binding_declares_binder_name_with_explicit_type_expect_type_used() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.binders.my-broker.type", "kafka");
      final BinderTypeResolver resolver = new BinderTypeResolver(environment, NO_BINDERS);

      assertThat(resolver.resolve("produce-book-out-0", "my-broker")).contains("kafka");
    }

    @Test
    void when_binding_declares_blank_binder_expect_fallback_to_next_strategy() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka");
      final BinderTypeResolver resolver = new BinderTypeResolver(environment, NO_BINDERS);

      assertThat(resolver.resolve("produce-book-out-0", "  ")).contains("kafka");
    }
  }

  @Nested
  class DefaultBinder {

    @Test
    void when_default_binder_declared_expect_it_resolved() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "kafka");
      final BinderTypeResolver resolver = new BinderTypeResolver(environment, NO_BINDERS);

      assertThat(resolver.resolve("produce-book-out-0", null)).contains("kafka");
    }

    @Test
    void when_default_binder_is_a_named_binder_expect_its_type_resolved() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "my-broker")
          .withProperty("spring.cloud.stream.binders.my-broker.type", "kafka");
      final BinderTypeResolver resolver = new BinderTypeResolver(environment, NO_BINDERS);

      assertThat(resolver.resolve("produce-book-out-0", null)).contains("kafka");
    }

    @Test
    void when_binding_binder_and_default_binder_both_declared_expect_binding_binder_wins() {
      final MockEnvironment environment = new MockEnvironment()
          .withProperty("spring.cloud.stream.default-binder", "rabbit");
      final BinderTypeResolver resolver = new BinderTypeResolver(environment, NO_BINDERS);

      assertThat(resolver.resolve("produce-book-out-0", "kafka")).contains("kafka");
    }
  }

  @Nested
  class ClasspathDiscovery {

    @Test
    void when_single_binder_on_classpath_expect_it_resolved(@TempDir final Path tempDir) throws IOException {
      final ClassLoader classLoader = classLoaderWith(tempDir, "kafka:\\\norg.example.KafkaBinderConfiguration\n");
      final BinderTypeResolver resolver = new BinderTypeResolver(new MockEnvironment(), classLoader);

      assertThat(resolver.resolve("produce-book-out-0", null)).contains("kafka");
    }

    @Test
    void when_several_binders_on_classpath_expect_unresolved(@TempDir final Path tempDir) throws IOException {
      final ClassLoader classLoader = classLoaderWith(tempDir,
          "kafka:\\\norg.example.KafkaBinderConfiguration\nrabbit:\\\norg.example.RabbitBinderConfiguration\n");
      final BinderTypeResolver resolver = new BinderTypeResolver(new MockEnvironment(), classLoader);

      assertThat(resolver.resolve("produce-book-out-0", null)).isEmpty();
    }

    @Test
    void when_no_binder_on_classpath_expect_unresolved() {
      final BinderTypeResolver resolver = new BinderTypeResolver(new MockEnvironment(), NO_BINDERS);

      assertThat(resolver.resolve("produce-book-out-0", null)).isEmpty();
    }

    @Test
    void when_resolved_twice_expect_classpath_scanned_once(@TempDir final Path tempDir) throws IOException {
      final ClassLoader classLoader = classLoaderWith(tempDir, "kafka:\\\norg.example.KafkaBinderConfiguration\n");
      final BinderTypeResolver resolver = new BinderTypeResolver(new MockEnvironment(), classLoader);

      assertThat(resolver.resolve("a-out-0", null)).contains("kafka");
      assertThat(resolver.resolve("b-out-0", null)).contains("kafka");
    }

    private static ClassLoader classLoaderWith(final Path root, final String springBindersContent) throws IOException {
      final Path metaInf = Files.createDirectories(root.resolve("META-INF"));
      Files.writeString(metaInf.resolve("spring.binders"), springBindersContent);
      return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
    }
  }
}
