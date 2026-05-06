package dev.inditex.scsoutbox.publish.archive.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface JsonMapper<T> {

  String writeValueAsString(T value);

  default Class<T> getValueType() {
    final Type[] typeArguments = ((ParameterizedType) this.getClass().getGenericInterfaces()[0]).getActualTypeArguments();
    return (Class<T>) typeArguments[typeArguments.length - 1];
  }
}
