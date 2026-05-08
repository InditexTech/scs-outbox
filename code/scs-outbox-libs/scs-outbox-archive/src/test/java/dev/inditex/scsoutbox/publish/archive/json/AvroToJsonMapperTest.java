package dev.inditex.scsoutbox.publish.archive.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AvroToJsonMapperTest {

  private AvroToJsonMapper avroToJsonMapper;

  @BeforeEach
  void setUp() {
    this.avroToJsonMapper = new AvroToJsonMapper();
  }

  @Test
  void map_complex_avro_message() {
    final Cycle avroMessage = createComplexAvroMessage();

    final String json = this.avroToJsonMapper.writeValueAsString(avroMessage);

    assertEquals(
        "{\"id\":\"e5e313ab-110e-4890-8da3-7547f119c281\",\"distribution_range_id\":\"e5e313ab-110e-4890-8da3-7547f119c281\",\"week_cycle\":2,\"start_date\":\"2024-04-23T12:08:58.733828203Z\",\"end_date\":\"2024-04-23T12:08:58.733828203Z\",\"arrival_store_date\":\"ArrivalStoreData\",\"closed\":true,\"products\":[{\"distributable_product_id\":\"e5e313ab-110e-4890-8da3-7547f119c281\",\"comment\":{\"string\":\"comment\"},\"assignment_type\":\"assignmentType\",\"published\":true}]}",
        json);
  }

  private static Cycle createComplexAvroMessage() {
    return Cycle.newBuilder()
        .setClosed(true)
        .setId(UUID.fromString("e5e313ab-110e-4890-8da3-7547f119c281"))
        .setProducts(List.of(
            Product.newBuilder()
                .setComment("comment")
                .setPublished(true)
                .setAssignmentType("assignmentType")
                .setDistributableProductId(UUID.fromString("e5e313ab-110e-4890-8da3-7547f119c281"))
                .build()))
        .setEndDate("2024-04-23T12:08:58.733828203Z")
        .setStartDate("2024-04-23T12:08:58.733828203Z")
        .setWeekCycle(2)
        .setArrivalStoreDate("ArrivalStoreData")
        .setDistributionRangeId(UUID.fromString("e5e313ab-110e-4890-8da3-7547f119c281"))
        .build();
  }

  @Disabled("We choose to use the apache avro library to map the payload to json, "
      + "the following test is left as a possible alternative using the jackson library")
  @Test
  void how_do_the_same_with_jackson_lib() {
    final BookCreatedMessage avroMessage = BookCreatedMessage.newBuilder()
        .setBookId(UUID.fromString("e5e313ab-110e-4890-8da3-7547f119c281"))
        .build();

    final String json = this.avroToJsonMapper.writeValueAsString(avroMessage);

    final ObjectMapper om = tools.jackson.databind.json.JsonMapper.builder()
        .addMixIn(SpecificRecordBase.class, JacksonIgnoreAvroProperties.class)
        .build();
    final String json2 = om.writeValueAsString(avroMessage);
    assertEquals(
        json,
        json2);
  }

  @JsonAutoDetect(
      fieldVisibility = JsonAutoDetect.Visibility.ANY,
      getterVisibility = JsonAutoDetect.Visibility.NONE,
      setterVisibility = JsonAutoDetect.Visibility.NONE,
      creatorVisibility = JsonAutoDetect.Visibility.NONE)
  abstract class JacksonIgnoreAvroProperties {

    @JsonIgnore
    public abstract org.apache.avro.Schema getClassSchema();

    @JsonIgnore
    public abstract org.apache.avro.specific.SpecificData getSpecificData();

    @JsonIgnore
    public abstract Object get(int fieldIndex);

    @JsonIgnore
    public abstract org.apache.avro.Schema getSchema();
  }

}
