package dev.inditex.scsoutbox.config.producer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import dev.inditex.scsoutbox.config.OutboxProperties;
import dev.inditex.scsoutbox.config.producer.SyncProducerBinderRegistry.SyncProducerMapping;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Injects the binder-specific synchronous producer property for every outbox-enabled binding that does not configure it already.
 *
 * <p>scs-outbox deletes an outbox record as soon as {@code StreamBridge.send} returns {@code true}. With an asynchronous producer that
 * happens before the broker acknowledges the record, so any failure occurring afterwards silently loses the message. Requiring every
 * application to remember the binder-specific property is error-prone, hence this automatic configuration.
 *
 * <p><strong>Precedence.</strong> The generated values are contributed through a {@link MapPropertySource} appended <em>last</em>, so any
 * property source owned by the application wins. In addition, a binding is skipped entirely when the application already binds either the
 * binding-scoped property or the binder-wide default property. This second check is required because Spring Cloud Stream resolves
 * binding-scoped entries over binder-wide defaults regardless of the property source order, so contributing a binding-scoped value would
 * otherwise silently override an explicit binder-wide default set by the application.
 *
 * <p>Bindings whose binder type cannot be resolved, or for which no synchronous mapping is known, are left untouched;
 * {@link SyncProducerValidator} reports them at startup.
 *
 * <p>Only bindings selected by {@code scs-outbox.bindings.inclusions} / {@code scs-outbox.bindings.exclusions} are affected. The whole
 * processor is disabled by {@code scs-outbox.bindings.sync-producers.enabled=false}.
 */
public class SyncProducerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  /**
   * Name of the property source contributed by this post-processor.
   */
  public static final String PROPERTY_SOURCE_NAME = "scs-outbox-sync-producers";

  static final String BINDINGS_PREFIX = "spring.cloud.stream.bindings";

  static final String OUTBOX_PREFIX = "scs-outbox";

  private final Log log;

  public SyncProducerEnvironmentPostProcessor(final DeferredLogFactory logFactory) {
    this.log = logFactory.getLog(SyncProducerEnvironmentPostProcessor.class);
  }

  SyncProducerEnvironmentPostProcessor(final Log log) {
    this.log = log;
  }

  @Override
  public int getOrder() {
    // Must run after the configuration data (application.yml / application.properties) has been contributed to the environment.
    return ConfigDataEnvironmentPostProcessor.ORDER + 1;
  }

  @Override
  public void postProcessEnvironment(final ConfigurableEnvironment environment, final SpringApplication application) {
    this.postProcessEnvironment(environment);
  }

  void postProcessEnvironment(final ConfigurableEnvironment environment) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }

    final Binder binder = Binder.get(environment);
    final OutboxProperties outboxProperties = binder.bind(OUTBOX_PREFIX, Bindable.of(OutboxProperties.class))
        .orElseGet(() -> new OutboxProperties(null));
    if (!outboxProperties.getBindings().getSyncProducers().isEnabled()) {
      this.log.debug("Automatic synchronous producer configuration is disabled by 'scs-outbox.bindings.sync-producers.enabled=false'");
      return;
    }

    final Map<String, BindingProperties> bindings = binder
        .bind(BINDINGS_PREFIX, Bindable.mapOf(String.class, BindingProperties.class))
        .orElseGet(Map::of);
    if (bindings.isEmpty()) {
      return;
    }

    final BinderTypeResolver binderTypeResolver = new BinderTypeResolver(environment);
    final Map<String, Object> contributed = new LinkedHashMap<>();

    for (final Map.Entry<String, BindingProperties> entry : bindings.entrySet()) {
      final String bindingName = entry.getKey();
      final BindingProperties bindingProperties = entry.getValue();

      if (!SyncProducerBindings.isProducerBinding(bindingName, bindingProperties)
          || !outboxProperties.getBindings().matches(bindingName)) {
        continue;
      }

      final Optional<SyncProducerMapping> mapping = binderTypeResolver.resolve(bindingName, bindingProperties.getBinder())
          .flatMap(SyncProducerBinderRegistry::findByBinderType);
      if (mapping.isEmpty()) {
        continue;
      }

      final SyncProducerMapping syncProducerMapping = mapping.get();
      final String bindingProperty = syncProducerMapping.bindingProperty(bindingName);
      if (isConfiguredByApplication(binder, bindingProperty, syncProducerMapping.binderDefaultProperty())) {
        this.log.debug("Binding [" + bindingName + "] already configures its synchronous producer mode; leaving it untouched");
        continue;
      }

      contributed.put(bindingProperty, syncProducerMapping.requiredValue());
    }

    if (contributed.isEmpty()) {
      return;
    }

    this.log.debug("Enabling synchronous producers for outbox-enabled bindings: " + contributed);
    environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, contributed));
  }

  /**
   * A binding is skipped when the application already declares the synchronous producer mode, either for this specific binding or as a
   * binder-wide default. Spring Cloud Stream resolves binding-scoped entries over binder-wide defaults regardless of the property source
   * order, so contributing a binding-scoped value would otherwise override an explicit binder-wide default owned by the application.
   */
  private static boolean isConfiguredByApplication(final Binder binder, final String bindingProperty, final String binderDefaultProperty) {
    return binder.bind(SyncProducerBindings.propertyName(bindingProperty), Bindable.of(String.class)).isBound()
        || binder.bind(SyncProducerBindings.propertyName(binderDefaultProperty), Bindable.of(String.class)).isBound();
  }
}
