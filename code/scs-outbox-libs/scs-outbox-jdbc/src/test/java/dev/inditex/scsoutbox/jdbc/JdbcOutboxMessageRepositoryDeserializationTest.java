package dev.inditex.scsoutbox.jdbc;

import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.jdbc.config.JdbcProperties;
import dev.inditex.scsoutbox.serialization.HeadersMapper;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;
import dev.inditex.scsoutbox.serialization.SerializationEngine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Tests for deserialization error handling in JdbcOutboxMessageRepository.
 */
class JdbcOutboxMessageRepositoryDeserializationTest {

  private JdbcTemplate jdbcTemplate;

  private Table table;

  private JavaSerialization realSerialization;

  private HeadersMapper headersMapper;

  private JdbcOutboxMessageRepository insertRepository;

  @BeforeEach
  void setUp() {
    final DataSource dataSource = new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .addScript("classpath:scripts/mariadb-outbox-table.sql")
        .generateUniqueName(true)
        .build();
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    final DataSourceMetadata datasourceMetadata = new DataSourceMetadata(dataSource);
    final JdbcProperties properties = new JdbcProperties();
    final String schema = new DbSchemaResolver(datasourceMetadata).resolve(properties.getSchema());
    this.table = new Table(new SchemaName(schema), new TableName(properties.getTableName()));
    this.realSerialization = new JavaSerialization();
    this.headersMapper = new JsonHeadersMapper();
    final OutboxMessageSerializer serializer = new OutboxMessageSerializer(
        this.realSerialization,
        this.headersMapper);
    this.insertRepository = new JdbcOutboxMessageRepository(
        this.jdbcTemplate,
        this.table,
        serializer);
  }

  @Test
  void findAllOrderByCapturedAt_shouldReturnEmptyList_whenFirstMessageFailsDeserialization() {
    this.insertMessage("destination1", Instant.now().minusSeconds(30));
    this.insertMessage("destination1", Instant.now().minusSeconds(20));
    this.insertMessage("destination1", Instant.now().minusSeconds(10));
    final JdbcOutboxMessageRepository repository = this.createRepositoryWithFailingDeserialization(1);

    final List<OutboxMessage> result = repository.findAllOrderByCapturedAt(UNLIMITED);

    assertTrue(result.isEmpty());
  }

  @Test
  void findAllOrderByCapturedAt_shouldReturnFirstMessages_whenMiddleMessageFailsDeserialization() {
    this.insertMessage("destination1", Instant.now().minusSeconds(50));
    this.insertMessage("destination1", Instant.now().minusSeconds(40));
    this.insertMessage("destination1", Instant.now().minusSeconds(30));
    this.insertMessage("destination1", Instant.now().minusSeconds(20));
    this.insertMessage("destination1", Instant.now().minusSeconds(10));
    final JdbcOutboxMessageRepository repository = this.createRepositoryWithFailingDeserialization(3);

    final List<OutboxMessage> result = repository.findAllOrderByCapturedAt(UNLIMITED);

    assertEquals(2, result.size());
  }

  @Test
  void findAllOrderByCapturedAt_shouldReturnAllButLast_whenLastMessageFailsDeserialization() {
    this.insertMessage("destination1", Instant.now().minusSeconds(30));
    this.insertMessage("destination1", Instant.now().minusSeconds(20));
    this.insertMessage("destination1", Instant.now().minusSeconds(10));
    final JdbcOutboxMessageRepository repository = this.createRepositoryWithFailingDeserialization(3);

    final List<OutboxMessage> result = repository.findAllOrderByCapturedAt(UNLIMITED);

    assertEquals(2, result.size());
  }

  @Test
  void findAllOrderByCapturedAt_shouldReturnAllMessages_whenAllDeserializationsSucceed() {
    this.insertMessage("destination1", Instant.now().minusSeconds(30));
    this.insertMessage("destination1", Instant.now().minusSeconds(20));
    this.insertMessage("destination1", Instant.now().minusSeconds(10));
    final JdbcOutboxMessageRepository repository = this.createRepositoryWithFailingDeserialization(Integer.MAX_VALUE);

    final List<OutboxMessage> result = repository.findAllOrderByCapturedAt(UNLIMITED);

    assertEquals(3, result.size());
  }

  @Test
  void findAllOrderByCapturedAtExcludingDestinations_shouldReturnFirstMessages_whenMiddleMessageFailsDeserialization() {
    this.insertMessage("excluded-dest", Instant.now().minusSeconds(100));
    this.insertMessage("destination1", Instant.now().minusSeconds(50));
    this.insertMessage("destination1", Instant.now().minusSeconds(40));
    this.insertMessage("destination1", Instant.now().minusSeconds(30));
    final JdbcOutboxMessageRepository repository = this.createRepositoryWithFailingDeserialization(2);

    final Set<String> excludedDestinations = Set.of("excluded-dest");
    final List<OutboxMessage> result = repository.findAllOrderByCapturedAtExcludingDestinations(excludedDestinations, UNLIMITED);

    assertEquals(1, result.size());
  }

  private JdbcOutboxMessageRepository createRepositoryWithFailingDeserialization(int failOnCall) {
    final SerializationEngine failingEngine = new FailingSerializationEngine(this.realSerialization, failOnCall);
    final OutboxMessageSerializer serializer = new OutboxMessageSerializer(
        failingEngine,
        this.headersMapper);
    return new JdbcOutboxMessageRepository(
        this.jdbcTemplate,
        this.table,
        serializer);
  }

  private void insertMessage(String destination, Instant capturedAt) {
    final OutboxMessage message = OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(capturedAt)
        .destination(destination)
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of())
        .build();
    this.insertRepository.save(message);
  }

  /**
   * SerializationEngine that delegates to another engine but fails on a specific call number.
   */
  private static class FailingSerializationEngine implements SerializationEngine {

    private final SerializationEngine delegate;

    private final int failOnCall;

    private final AtomicInteger callCount = new AtomicInteger(0);

    FailingSerializationEngine(SerializationEngine delegate, int failOnCall) {
      this.delegate = delegate;
      this.failOnCall = failOnCall;
    }

    @Override
    public Object deserialize(byte[] bytes) {
      if (this.callCount.incrementAndGet() >= this.failOnCall) {
        throw new RuntimeException("Simulated deserialization error on call " + this.callCount.get());
      }
      return this.delegate.deserialize(bytes);
    }

    @Override
    public byte[] serialize(Object object) {
      return this.delegate.serialize(object);
    }
  }
}
