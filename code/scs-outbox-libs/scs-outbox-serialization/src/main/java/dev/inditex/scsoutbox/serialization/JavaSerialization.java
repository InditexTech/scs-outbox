package dev.inditex.scsoutbox.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import lombok.SneakyThrows;

public class JavaSerialization implements SerializationEngine {

  @Override
  @SneakyThrows
  public Object deserialize(final byte[] bytes) {
    try (
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        final ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
      return objectInputStream.readObject();
    }
  }

  @Override
  @SneakyThrows
  public byte[] serialize(final Object object) {
    try (
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
      objectOutputStream.writeObject(object);
      return outputStream.toByteArray();
    }
  }
}
