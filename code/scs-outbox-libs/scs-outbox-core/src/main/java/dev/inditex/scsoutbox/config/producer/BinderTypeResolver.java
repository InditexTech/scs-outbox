package dev.inditex.scsoutbox.config.producer;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.stream.config.BinderProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Resolves the Spring Cloud Stream binder type backing a given binding, using only the {@link Environment} and the classpath.
 *
 * <p>Resolution happens before the application context exists (see {@link SyncProducerEnvironmentPostProcessor}), so the regular
 * {@code BindingServiceProperties} bean is not available. The same resolver is reused at validation time so that both steps always agree on
 * the binder type they attributed to a binding.
 *
 * <p>Resolution order: <ol> <li>the binding's own {@code spring.cloud.stream.bindings.<binding>.binder} entry</li>
 * <li>{@code spring.cloud.stream.default-binder}</li> <li>the single binder found on the classpath through
 * {@code META-INF/spring.binders}</li> </ol>
 *
 * <p>A binder <em>name</em> is translated into a binder <em>type</em> through {@code spring.cloud.stream.binders.<name>.type}, falling back
 * to the name itself when no explicit type is declared, which mirrors Spring Cloud Stream's own behaviour.
 *
 * <p>When several binders are present on the classpath and the binding declares none, the binder type is reported as unresolved rather than
 * guessed.
 */
@Slf4j
public class BinderTypeResolver {

  private static final String BINDERS_PREFIX = "spring.cloud.stream.binders";

  private static final String DEFAULT_BINDER_PROPERTY = "spring.cloud.stream.default-binder";

  private static final String SPRING_BINDERS_LOCATION = "classpath*:META-INF/spring.binders";

  private final ClassLoader classLoader;

  private final Binder binder;

  private Set<String> classpathBinderTypes;

  public BinderTypeResolver(final Environment environment) {
    this(environment, BinderTypeResolver.class.getClassLoader());
  }

  public BinderTypeResolver(final Environment environment, final ClassLoader classLoader) {
    this.classLoader = classLoader;
    this.binder = Binder.get(environment);
  }

  /**
   * Resolves the binder type for a binding.
   *
   * @param bindingName the Spring Cloud Stream binding name, used for logging only
   * @param declaredBinderName the value of the binding's {@code binder} property, may be {@code null}
   * @return the resolved binder type, or {@link Optional#empty()} when it cannot be determined unambiguously
   */
  public Optional<String> resolve(final String bindingName, final String declaredBinderName) {
    if (declaredBinderName != null && !declaredBinderName.isBlank()) {
      return Optional.of(this.toBinderType(declaredBinderName));
    }

    final String defaultBinder = this.binder.bind(DEFAULT_BINDER_PROPERTY, Bindable.of(String.class)).orElse(null);
    if (defaultBinder != null && !defaultBinder.isBlank()) {
      return Optional.of(this.toBinderType(defaultBinder));
    }

    final Set<String> candidates = this.classpathBinderTypes();
    if (candidates.size() == 1) {
      return Optional.of(candidates.iterator().next());
    }

    log.debug("Unable to resolve the binder type for binding [{}]; candidates found on the classpath: {}", bindingName, candidates);
    return Optional.empty();
  }

  private String toBinderType(final String binderName) {
    final Map<String, BinderProperties> binders = this.binder
        .bind(BINDERS_PREFIX, Bindable.mapOf(String.class, BinderProperties.class))
        .orElseGet(Map::of);
    final BinderProperties binderProperties = binders.get(binderName);
    if (binderProperties != null && binderProperties.getType() != null && !binderProperties.getType().isBlank()) {
      return binderProperties.getType();
    }
    // Spring Cloud Stream falls back to the binder name itself when no explicit type is declared.
    return binderName;
  }

  private Set<String> classpathBinderTypes() {
    if (this.classpathBinderTypes == null) {
      this.classpathBinderTypes = this.scanClasspathBinderTypes();
    }
    return this.classpathBinderTypes;
  }

  private Set<String> scanClasspathBinderTypes() {
    final Set<String> types = new LinkedHashSet<>();
    try {
      final Resource[] resources = new PathMatchingResourcePatternResolver(this.classLoader).getResources(SPRING_BINDERS_LOCATION);
      for (final Resource resource : resources) {
        try (InputStream inputStream = resource.getInputStream()) {
          final Properties properties = new Properties();
          properties.load(inputStream);
          properties.stringPropertyNames().forEach(types::add);
        }
      }
    } catch (final IOException e) {
      log.debug("Unable to scan {} to discover the available binders", SPRING_BINDERS_LOCATION, e);
    }
    return types;
  }
}
