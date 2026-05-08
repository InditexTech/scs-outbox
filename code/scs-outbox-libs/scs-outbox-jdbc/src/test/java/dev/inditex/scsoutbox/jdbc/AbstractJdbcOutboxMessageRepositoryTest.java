package dev.inditex.scsoutbox.jdbc;

import static dev.inditex.scsoutbox.OutboxMessageRepository.UNLIMITED;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import dev.inditex.scsoutbox.OutboxMessage;
import dev.inditex.scsoutbox.jdbc.config.JdbcProperties;
import dev.inditex.scsoutbox.serialization.JavaSerialization;
import dev.inditex.scsoutbox.serialization.JsonHeadersMapper;
import dev.inditex.scsoutbox.serialization.OutboxMessageSerializer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class AbstractJdbcOutboxMessageRepositoryTest {

  private JdbcOutboxMessageRepository repository;

  @BeforeEach
  void setUp() {
    final JdbcTemplate jdbcTemplate = new JdbcTemplate(this.getDataSource());
    final DataSourceMetadata datasourceMetadata = new DataSourceMetadata(this.getDataSource());
    final JdbcProperties properties = new JdbcProperties();
    final String schema = new DbSchemaResolver(datasourceMetadata).resolve(properties.getSchema());
    final Table table = new Table(new SchemaName(schema), new TableName(properties.getTableName()));
    final OutboxMessageSerializer serializer = new OutboxMessageSerializer(
        new JavaSerialization(),
        new JsonHeadersMapper());
    this.repository = JdbcOutboxMessageRepositoryFactory.create(
        jdbcTemplate,
        datasourceMetadata,
        table,
        serializer);
    jdbcTemplate.execute("DELETE FROM " + table.getQualifiedTableName());
  }

  public abstract DataSource getDataSource();

  @Test
  void count() {
    final int expectedNumOfMessages = 3;
    for (int i = 0; i < expectedNumOfMessages; i++) {
      this.repository.save(this.anOutboxMessage());
    }
    assertEquals(expectedNumOfMessages, this.repository.count());
  }

  @Test
  void estimatedCount() {
    final int expectedNumOfMessages = 3;
    for (int i = 0; i < expectedNumOfMessages; i++) {
      this.repository.save(this.anOutboxMessage());
    }
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertEquals(expectedNumOfMessages, this.repository.estimatedCount()));
  }

  @Test
  void find_all() {
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAt(UNLIMITED);
    assertTrue(result.isEmpty());
  }

  @Test
  void find_all_max_results() {
    final int maxResults = 2;
    this.repository.save(this.anOutboxMessage());
    this.repository.save(this.anOutboxMessage());
    this.repository.save(this.anOutboxMessage());
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAt(maxResults);
    assertEquals(maxResults, result.size());
  }

  @Test
  void save() {
    final OutboxMessage aOutboxMessage = this.anOutboxMessage();
    this.repository.save(aOutboxMessage);
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAt(UNLIMITED);
    assertEquals(1, result.size());
    assertEquals(aOutboxMessage, result.get(0));
  }

  private OutboxMessage anOutboxMessage() {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.now())
        .destination("destination")
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of())
        .build();
  }

  @Test
  void delete() {
    final OutboxMessage aOutboxMessage = this.anOutboxMessage();
    this.repository.save(aOutboxMessage);
    this.repository.delete(aOutboxMessage);
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAt(UNLIMITED);
    assertTrue(result.isEmpty());
  }

  @Test
  void findAllOrderByCapturedAtExcludingDestinations_shouldExcludeSpecifiedDestinations() {
    // Given: Save messages with different destinations
    final OutboxMessage message1 = this.anOutboxMessageWithDestination("destination1");
    final OutboxMessage message2 = this.anOutboxMessageWithDestination("destination2");
    final OutboxMessage message3 = this.anOutboxMessageWithDestination("destination3");
    final OutboxMessage message4 = this.anOutboxMessageWithDestination("destination4");

    this.repository.save(message1);
    this.repository.save(message2);
    this.repository.save(message3);
    this.repository.save(message4);

    // When: Exclude destinations 1 and 2
    final Set<String> excludedDestinations = Set.of("destination1", "destination2");
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAtExcludingDestinations(excludedDestinations, UNLIMITED);

    // Then: Should only return messages for destinations 3 and 4
    assertEquals(2, result.size());
    assertTrue(result.stream().anyMatch(msg -> msg.getDestination().equals("destination3")));
    assertTrue(result.stream().anyMatch(msg -> msg.getDestination().equals("destination4")));
    assertTrue(result.stream().noneMatch(msg -> msg.getDestination().equals("destination1")));
    assertTrue(result.stream().noneMatch(msg -> msg.getDestination().equals("destination2")));
  }

  @Test
  void findAllOrderByCapturedAtExcludingDestinations_shouldRespectMaxResults() {
    // Given: Save multiple messages with non-excluded destinations
    final OutboxMessage message1 = this.anOutboxMessageWithDestination("destination1");
    final OutboxMessage message2 = this.anOutboxMessageWithDestination("destination2");
    final OutboxMessage message3 = this.anOutboxMessageWithDestination("destination3");
    final OutboxMessage message4 = this.anOutboxMessageWithDestination("excluded");

    this.repository.save(message1);
    this.repository.save(message2);
    this.repository.save(message3);
    this.repository.save(message4);

    // When: Exclude one destination and limit results to 2
    final Set<String> excludedDestinations = Set.of("excluded");
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAtExcludingDestinations(excludedDestinations, 2);

    // Then: Should return exactly 2 messages (not the excluded one)
    assertEquals(2, result.size());
    assertTrue(result.stream().noneMatch(msg -> msg.getDestination().equals("excluded")));
  }

  @Test
  void findAllOrderByCapturedAtExcludingDestinations_shouldFallbackWhenNoExclusions() {
    // Given: Save some messages
    final OutboxMessage message1 = this.anOutboxMessageWithDestination("destination1");
    final OutboxMessage message2 = this.anOutboxMessageWithDestination("destination2");

    this.repository.save(message1);
    this.repository.save(message2);

    // When: Call with empty exclusions
    final Set<String> excludedDestinations = Set.of();
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAtExcludingDestinations(excludedDestinations, UNLIMITED);

    // Then: Should return all messages (same as normal findAll)
    assertEquals(2, result.size());

    // Compare with normal findAll to ensure same behavior
    final List<OutboxMessage> normalResult = this.repository.findAllOrderByCapturedAt(UNLIMITED);
    assertEquals(normalResult.size(), result.size());
  }

  @Test
  void findAllOrderByCapturedAtExcludingDestinations_shouldFallbackWhenNullExclusions() {
    // Given: Save some messages
    final OutboxMessage message1 = this.anOutboxMessageWithDestination("destination1");
    final OutboxMessage message2 = this.anOutboxMessageWithDestination("destination2");

    this.repository.save(message1);
    this.repository.save(message2);

    // When: Call with null exclusions
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAtExcludingDestinations(null, UNLIMITED);

    // Then: Should return all messages (same as normal findAll)
    assertEquals(2, result.size());

    // Compare with normal findAll to ensure same behavior
    final List<OutboxMessage> normalResult = this.repository.findAllOrderByCapturedAt(UNLIMITED);
    assertEquals(normalResult.size(), result.size());
  }

  @Test
  void findAllOrderByCapturedAtExcludingDestinations_shouldReturnEmptyWhenAllDestinationsExcluded() {
    // Given: Save messages
    final OutboxMessage message1 = this.anOutboxMessageWithDestination("destination1");
    final OutboxMessage message2 = this.anOutboxMessageWithDestination("destination2");

    this.repository.save(message1);
    this.repository.save(message2);

    // When: Exclude all destinations
    final Set<String> excludedDestinations = Set.of("destination1", "destination2");
    final List<OutboxMessage> result = this.repository.findAllOrderByCapturedAtExcludingDestinations(excludedDestinations, UNLIMITED);

    // Then: Should return empty list
    assertTrue(result.isEmpty());
  }

  private OutboxMessage anOutboxMessageWithDestination(String destination) {
    return OutboxMessage.builder()
        .id(UUID.randomUUID())
        .capturedAt(Instant.now())
        .destination(destination)
        .bindingName("bindingName")
        .payload("payload")
        .headers(Map.of())
        .build();
  }

}
