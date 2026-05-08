package dev.inditex.scsoutbox.serialization;

import java.util.Map;

public interface HeadersMapper {

  Map<String, Object> read(String headers);

  String write(Map<String, Object> headers);
}
