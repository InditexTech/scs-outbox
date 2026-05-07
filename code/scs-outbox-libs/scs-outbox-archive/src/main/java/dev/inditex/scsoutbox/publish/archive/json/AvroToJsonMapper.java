package dev.inditex.scsoutbox.publish.archive.json;

import java.io.ByteArrayOutputStream;

import lombok.SneakyThrows;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

public class AvroToJsonMapper implements JsonMapper<SpecificRecordBase> {

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  @SneakyThrows
  public String writeValueAsString(final SpecificRecordBase avroMessage) {
    try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      final JsonEncoder jsonEncoder = EncoderFactory.get().jsonEncoder(
          avroMessage.getSchema(), baos);
      final DatumWriter writer = new SpecificDatumWriter<>(avroMessage.getClass());

      writer.write(avroMessage, jsonEncoder);
      jsonEncoder.flush();

      return baos.toString();
    }
  }

}
