package dev.inditex.scsoutbox.serialization;

public interface SerializationEngine {

  Object deserialize(final byte[] bytes);

  byte[] serialize(Object object);
}
