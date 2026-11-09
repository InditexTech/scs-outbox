package dev.inditex.scsoutbox.config.producer;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;

/**
 * Stub binder reporting a configurable defaults prefix and handing out one cached producer properties instance per binding, exactly like
 * {@code AbstractExtendedBindingProperties} does. Shared by every test that needs a binder-like collaborator without a real Kafka
 * dependency.
 */
class KafkaLikeStubBinder implements ExtendedPropertiesBinder<Object, Object, KafkaLikeStubBinder.StubProducerProperties> {

  private final Map<String, StubProducerProperties> producerProperties = new LinkedHashMap<>();

  private final String defaultsPrefix;

  KafkaLikeStubBinder(final String defaultsPrefix) {
    this.defaultsPrefix = defaultsPrefix;
  }

  @Override
  public StubProducerProperties getExtendedProducerProperties(final String bindingName) {
    return this.producerProperties.computeIfAbsent(bindingName, name -> new StubProducerProperties());
  }

  @Override
  public Object getExtendedConsumerProperties(final String bindingName) {
    return null;
  }

  @Override
  public String getDefaultsPrefix() {
    return this.defaultsPrefix;
  }

  @Override
  public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
    return StubBindingProperties.class;
  }

  @Override
  public Binding<Object> bindConsumer(final String name, final String group, final Object target,
      final ExtendedConsumerProperties<Object> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Binding<Object> bindProducer(final String name, final Object target,
      final ExtendedProducerProperties<StubProducerProperties> properties) {
    throw new UnsupportedOperationException();
  }

  /** Minimal stand-in for a binder-specific producer properties object, exposing the same {@code sync} JavaBean property. */
  public static class StubProducerProperties {

    private boolean sync;

    public boolean isSync() {
      return this.sync;
    }

    public void setSync(final boolean sync) {
      this.sync = sync;
    }
  }

  public static class StubBindingProperties implements BinderSpecificPropertiesProvider {

    private final StubProducerProperties producer = new StubProducerProperties();

    @Override
    public StubProducerProperties getProducer() {
      return this.producer;
    }

    @Override
    public Object getConsumer() {
      return null;
    }
  }
}
