# SCS-OUTBOX

Spring Cloud Stream Outbox is a library that implements the [transactional outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html) for [Spring Cloud Stream](https://spring.io/projects/spring-cloud-stream) applications.

It intercepts outbound messages produced via `StreamBridge`, stores them inside the current application transaction, and publishes them later through a scheduled task — guaranteeing both **at-least-once delivery** and **message ordering**.

> [!NOTE]
> The library is tested with `StreamBridge`. Other Spring Cloud Stream programming models may work but have not been validated.

## Prerequisites

| Component    | Minimum Version |
|--------------|-----------------|
| Java         | 17              |
| Spring Boot  | 4.0.4           |
| Spring Cloud | 2025.1.1        |

## Quick Start

### Choose your storage backend

SCS-Outbox supports **JDBC** and **MongoDB** backends. Add the corresponding starter to your project:

**JDBC**

```xml
<dependency>
  <groupId>dev.inditex.scsoutbox</groupId>
  <artifactId>scs-outbox-jdbc-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

> [!NOTE]
> JDBC should be compatible with any SQL database. SCS-Outbox has been tested with **MariaDB** and **PostgreSQL**.

**MongoDB**

```xml
<dependency>
  <groupId>dev.inditex.scsoutbox</groupId>
  <artifactId>scs-outbox-mongodb-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

> [!CAUTION]
> MongoDB requires transaction support. See the [Spring Data MongoDB transactions guide](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html#mongo.transactions).

> [!NOTE]
> SCS-Outbox does not support reactive programming in any backend.

### Prepare your database (JDBC only)

Create the outbox table where messages are stored transactionally:

<details>
<summary>PostgreSQL</summary>

```sql
CREATE TABLE IF NOT EXISTS SCS_OUTBOX (
  ID            varchar(36)  NOT NULL,
  BINDING_NAME  varchar(256) NOT NULL,
  CAPTURED_AT   timestamp    NOT NULL,
  DESTINATION   varchar(256) NOT NULL,
  HEADERS       text         NOT NULL,
  PAYLOAD       bytea        NOT NULL,
  CONSTRAINT PK_OUTBOX PRIMARY KEY (ID)
);
```

</details>

<details>
<summary>MariaDB</summary>

```sql
CREATE TABLE IF NOT EXISTS SCS_OUTBOX (
  ID            varchar(36)  NOT NULL,
  BINDING_NAME  varchar(256) NOT NULL,
  CAPTURED_AT   timestamp    NOT NULL,
  DESTINATION   varchar(256) NOT NULL,
  HEADERS       text         NOT NULL,
  PAYLOAD       blob         NOT NULL,
  CONSTRAINT PK_OUTBOX PRIMARY KEY (ID)
);
```

</details>

scs-outbox also requires a [ShedLock](https://github.com/lukas-krecan/ShedLock) table for distributed lock coordination:

```sql
CREATE TABLE IF NOT EXISTS shedlock (
  name       VARCHAR(64),
  lock_until TIMESTAMP(3) NULL,
  locked_at  TIMESTAMP(3) NULL,
  locked_by  VARCHAR(255),
  PRIMARY KEY (name)
);
```

> [!NOTE]
> For high-throughput scenarios, create a non-unique index on the `captured_at` column:
>
> <details>
> <summary>PostgreSQL</summary>
>
> ```sql
> CREATE INDEX scs_outbox_captured_at_idx ON scs_outbox USING btree (captured_at);
> ```
>
> </details>
> <details>
> <summary>MariaDB</summary>
>
> ```sql
> CREATE INDEX scs_outbox_captured_at_idx ON scs_outbox(captured_at);
> ```
>
> </details>
> <details>
> <summary>MongoDB</summary>
>
> ```javascript
> db.SCS_OUTBOX.createIndex({"capturedAt": 1});
> ```
>
> </details>

### Configure your application

SCS-Outbox works with zero additional configuration, but requires:

- **Scheduling enabled**: SCS-Outbox uses a `@Scheduled` task to publish messages. Enable scheduling in your application with [`@EnableScheduling`](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-enable-annotation-support).
- **JDBC**: a `DataSource` bean configured to access the database containing the outbox and ShedLock tables.
- **MongoDB**: a `MongoTemplate` and `MongoClient` bean available in the application context.

### Verify it works

Start your application and produce messages as usual. By default, SCS-Outbox is enabled for all bindings.

> [!WARNING]
> - `StreamBridge.send(...)` will return `false` when outbox is enabled — this is expected. The message is captured for later publishing.
> - Sending messages **outside** an active transaction will fail with `IllegalTransactionStateException`. SCS-Outbox requires an open transaction to guarantee atomicity:
>   ```java
>   @Transactional
>   public void doWork() {
>     repository.save(entity);
>     streamBridge.send("my-binding-out-0", message);
>   }
>   ```
> - `ErrorMessage` instances are automatically filtered out and never captured.

## Architecture

### How it works

scs-outbox operates in two phases:

**1. Capture** — during your application transaction, a `GlobalChannelInterceptor` intercepts outbound messages and stores them in the database within the same transaction as your business data.

**2. Publishing** — a scheduled task (or an after-commit trigger) fetches pending messages, groups them to preserve message ordering, and publishes them through `StreamBridge`. Successfully published messages are then deleted from the outbox.

```mermaid
sequenceDiagram
    participant App as Application
    participant Int as OutboxChannelInterceptor
    participant Cap as MessageCaptureTxService
    participant DB as Database
    participant Sch as OutboxScheduledService
    participant Pub as OutboxPublishingTask
    participant SB as StreamBridge
    participant Broker as Message Broker

    rect rgb(230, 245, 255)
        Note over App,DB: Capture Phase (inside application transaction)
        App->>SB: streamBridge.send(binding, message)
        SB->>Int: preSend(message, channel)
        Int->>Cap: capture(message)
        Cap->>DB: save(outboxMessage)
        Int-->>SB: null (message not sent yet)
    end

    rect rgb(230, 255, 230)
        Note over Sch,Broker: Publishing Phase (scheduled / after-commit)
        Sch->>Pub: run()
        Pub->>DB: findPending(batchSize)
        DB-->>Pub: List<OutboxMessage>
        Pub->>Pub: group by ordering strategy
        loop Per group (parallel across groups)
            Pub->>SB: send(message)
            SB->>Broker: publish
            Pub->>DB: delete(outboxMessage)
        end
    end
```

> [!IMPORTANT]
> To guarantee message ordering and delivery, configure your Spring Cloud Stream producers in **synchronous** mode. See [Synchronous Producers](#synchronous-producers).

### Module overview

```mermaid
graph TD
    STARTER_JDBC[scs-outbox-jdbc-starter] --> JDBC[scs-outbox-jdbc]
    STARTER_JDBC --> ARCHIVE_JDBC[scs-outbox-archive-jdbc]
    STARTER_JDBC --> METRICS[scs-outbox-metrics]

    STARTER_MONGO[scs-outbox-mongodb-starter] --> MONGODB[scs-outbox-mongodb]
    STARTER_MONGO --> ARCHIVE_MONGO[scs-outbox-archive-mongodb]
    STARTER_MONGO --> METRICS

    JDBC --> CORE[scs-outbox-core]
    JDBC --> SERIALIZATION[scs-outbox-serialization]
    MONGODB --> CORE
    MONGODB --> SERIALIZATION

    ARCHIVE_JDBC --> ARCHIVE[scs-outbox-archive]
    ARCHIVE_JDBC --> JDBC
    ARCHIVE_MONGO --> ARCHIVE
    ARCHIVE_MONGO --> MONGODB

    ARCHIVE --> CORE
    ARCHIVE --> SERIALIZATION
    METRICS --> CORE

    style STARTER_JDBC fill:#4CAF50,color:#fff
    style STARTER_MONGO fill:#4CAF50,color:#fff
    style CORE fill:#2196F3,color:#fff
```

| Module | Purpose |
|--------|---------|
| `scs-outbox-core` | Core abstractions: message capture interceptor, scheduled publishing, ordering strategies, parallel publisher |
| `scs-outbox-serialization` | Message and header serialization/deserialization (Java, Avro, JSON headers) |
| `scs-outbox-jdbc` | JDBC-based `OutboxMessageRepository` with MariaDB and PostgreSQL support |
| `scs-outbox-mongodb` | MongoDB-based `OutboxMessageRepository` |
| `scs-outbox-archive` | Base archive functionality: interceptor, service, and JSON mapper |
| `scs-outbox-archive-jdbc` | JDBC archive repository implementation |
| `scs-outbox-archive-mongodb` | MongoDB archive repository implementation |
| `scs-outbox-metrics` | Micrometer metrics: pending count, capture time, publishing delay and time |
| `scs-outbox-jdbc-starter` | Starter: pulls JDBC + archive-jdbc + metrics |
| `scs-outbox-mongodb-starter` | Starter: pulls MongoDB + archive-mongodb + metrics |

## Configuration Reference

### Core properties

> All properties in this section use the prefix **`scs-outbox`**.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bindings.inclusions` | `List<String>` | `[]` (all bindings) | Bindings to enable outbox for. Supports regex with `regex:` prefix (see [Regex binding inclusions/exclusions](#regex-binding-inclusionsexclusions)) |
| `bindings.exclusions` | `List<String>` | `[]` | Bindings to exclude from outbox. Supports regex with `regex:` prefix (see [Regex binding inclusions/exclusions](#regex-binding-inclusionsexclusions)). **Exclusions take precedence** |

### Publishing properties

> All properties in this section use the prefix **`scs-outbox.publishing`**.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `batch-size` | `Integer` | `1000` | Max messages per publishing cycle. Supports `@RefreshScope` |
| `grouping-strategy` | `String` | `DESTINATION` | Message grouping strategy for ordering guarantees. See [Message grouping strategies](#message-grouping-strategies). Supports: `DESTINATION`, `KAFKA_MESSAGE_KEY`, `CUSTOM_GROUPING_KEY` |
| `paused` | `Boolean` | `false` | Globally pauses all message publishing. Supports `@RefreshScope` |
| `paused-destinations` | `Set<String>` | `[]` | Destinations to pause. Supports `@RefreshScope` |
| `after-commit` | `Boolean` | `false` | Trigger publishing after transaction commit (requires `@EnableAsync`) |

### Scheduler properties

> All properties in this section use the prefix **`scs-outbox.publishing.scheduler`**.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `task-name` | `String` | `outboxPublishingTask` | Name for the scheduled task (used for distributed locking) |
| `fixed-rate` | `Long` (ms) | `5000` | Fixed rate in milliseconds between publishing cycles |
| `cron-expression` | `String` | — | Cron expression (overrides `fixed-rate`). Use `"-"` to disable scheduling |
| `initial-delay` | `Long` (ms) | — | Initial delay before first execution (only with `fixed-rate`) |
| `lock-at-most-for` | `String` | `5m` | Max lock duration for ShedLock (ISO 8601 duration) |

### Archive properties

> All properties in this section use the prefix **`scs-outbox.publishing.archive`**.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `false` | Enable archiving of published messages |
| `json-payload-enabled` | `Boolean` | `false` | Store payload as JSON alongside raw bytes (for troubleshooting) |
| `jdbc.table-name` | `String` | `SCS_OUTBOX_ARCHIVE` | JDBC archive table name |
| `jdbc.schema` | `String` | `""` | JDBC archive table schema |
| `mongodb.collection-name` | `String` | `SCS_OUTBOX_ARCHIVE` | MongoDB archive collection name |

### Metrics properties

> All properties in this section use the prefix **`scs-outbox.metrics`**.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | `Boolean` | `false` | Enable Micrometer metrics (requires `MeterRegistry` bean) |

### JDBC properties

> All properties in this section use the prefix **`scs-outbox.jdbc`**.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `table-name` | `String` | `SCS_OUTBOX` | Name of the outbox table |
| `schema` | `String` | `""` | Database schema for the outbox table |

### MongoDB properties

> All properties in this section use the prefix **`scs-outbox.mongodb`**.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `collection-name` | `String` | `SCS_OUTBOX` | Name of the outbox collection |

## Features

### Serialization engine

By default, scs-outbox uses **Java serialization** to serialize message payloads. Payloads that are already `byte[]` are stored as raw bytes without invoking the serialization engine.

To use a different engine, create a Spring bean implementing `dev.inditex.scsoutbox.serialization.SerializationEngine`.

Built-in engines:
- `JavaSerialization` — default, requires payloads to implement `Serializable`

### Message grouping strategies

scs-outbox guarantees message order **within each group**. The grouping strategy determines what constitutes a group:

> **Note on terminology:** The grouping strategy (configured via `grouping-strategy`) determines how messages are *grouped* before publishing. SCS-Outbox guarantees message order *within each group*, while allowing parallel publishing across groups.

**Configuration:** Set the `scs-outbox.publishing.grouping-strategy` property. See [Publishing properties](#publishing-properties) in the Configuration Reference.

```yaml
scs-outbox:
  publishing:
    grouping-strategy: KAFKA_MESSAGE_KEY  # Group messages by Kafka message key
```

| Strategy | Property value | Behavior |
|----------|---------------|-----------|
| By destination | `DESTINATION` (default) | Assigns one thread per destination topic; preserves message order within each destination. |
| By Kafka key | `KAFKA_MESSAGE_KEY` | Assigns one thread per `kafka_messageKey` header, enabling higher parallelism. |
| Custom | `CUSTOM_GROUPING_KEY` | Uses a custom `GroupingKeyGenerator` bean to define grouping logic. |

> [!WARNING]
> Changing the grouping strategy may alter message ordering. Do not modify this setting unless you understand the implications.

**Custom grouping example:**

```java
@Component
public class MyGroupingKeyGenerator implements GroupingKeyGenerator {

  @Override
  public GroupingKey generate(GroupingValues values) {
    final String header = (String) values.getMessageHeaders().get("tenant-id");
    return GroupingKey.of(values.getDestination() + "-" + header);
  }
}
```

### Executor service

The publishing task uses an `ExecutorService` to publish messages in parallel across groups. The default is `Executors.newCachedThreadPool()`.

To provide a custom executor, define a Spring bean of type `ExecutorService` qualified with the name `outboxExecutorService`.

**Recommendations:**
- `DESTINATION` grouping: set max threads >= number of destinations.
- `KAFKA_MESSAGE_KEY` grouping: use a `ThreadPoolExecutor` with queue size ~ batch size, and tune max threads based on message volume.
- `CUSTOM_GROUPING_KEY` grouping: set max threads based on the expected cardinality of your custom keys.

### Archive messages

scs-outbox can archive published messages to a separate table or collection for auditing and troubleshooting.

Enable in configuration:

```yaml
scs-outbox:
  publishing:
    archive:
      enabled: true
```

For JDBC, create the archive table:

<details>
<summary>PostgreSQL</summary>

```sql
CREATE TABLE IF NOT EXISTS SCS_OUTBOX_ARCHIVE (
  ID              varchar(36)  NOT NULL,
  ARCHIVED_AT     timestamptz  NOT NULL,
  CAPTURED_AT     timestamptz  NOT NULL,
  DESTINATION     varchar(256) NOT NULL,
  CONTENT_TYPE    varchar(256) NOT NULL,
  HEADERS         text         NOT NULL,
  PAYLOAD         bytea        NOT NULL,
  SERIALIZATION   varchar(256) NOT NULL,
  JSON_PAYLOAD    jsonb,
  CONSTRAINT PK_OUTBOX_ARCHIVE PRIMARY KEY (ID)
);
```

</details>

<details>
<summary>MariaDB</summary>

```sql
CREATE TABLE IF NOT EXISTS SCS_OUTBOX_ARCHIVE (
  ID              varchar(36)  NOT NULL,
  ARCHIVED_AT     timestamp    NOT NULL,
  CAPTURED_AT     timestamp    NOT NULL,
  DESTINATION     varchar(256) NOT NULL,
  CONTENT_TYPE    varchar(256) NOT NULL,
  HEADERS         text         NOT NULL,
  PAYLOAD         blob         NOT NULL,
  SERIALIZATION   varchar(256) NOT NULL,
  JSON_PAYLOAD    text,
  CONSTRAINT PK_OUTBOX_ARCHIVE PRIMARY KEY (ID)
);
```

</details>

#### JSON payload

Set `json-payload-enabled: true` to store a human-readable JSON representation of the payload alongside the raw bytes.

> [!IMPORTANT]
> Converting the payload to its JSON representation occurs during the **publishing phase** and adds CPU/memory overhead.

> [!NOTE]
> The JSON representation is for troubleshooting only — it is not suitable for re-injection. If a payload cannot be serialized to JSON, the field will be `null`.

To add custom JSON serialization for specific types, create a Spring bean implementing `dev.inditex.scsoutbox.publish.archive.json.JsonMapper`.

### Metrics

scs-outbox provides [Micrometer](https://docs.micrometer.io/micrometer/reference/overview.html) metrics when `scs-outbox.metrics.enabled=true` and a `MeterRegistry` bean is present.

| Metric | Type | Description |
|--------|------|-------------|
| `outbox.capture.time` | Timer | Time taken to capture a message (via `@Timed`) |
| `outbox.publishing.time` | Timer | Time taken for the publishing task execution (via `@Timed`) |
| `outbox.publishing.delay` | Timer | Delay between message capture and publishing |
| `outbox.publishing.messages` | Counter | Number of messages published |
| `outbox.messages.pending` | Gauge | Estimated number of messages pending publishing |

### Pause message publishing

#### Global pause

Stop all message publishing:

```yaml
scs-outbox:
  publishing:
    paused: true
```

#### Per-destination pause

Pause specific destinations while keeping others active:

```yaml
scs-outbox:
  publishing:
    paused-destinations:
      - destination1
      - destination2
```

#### Dynamic updates

Publishing properties (`batch-size`, `paused`, `paused-destinations`) support `@RefreshScope`. Update them at runtime via Spring Cloud Config:

```bash
curl -X POST http://localhost:8080/actuator/refresh
```

> [!NOTE]
> Global pause takes precedence over per-destination pause.

### Regex binding inclusions/exclusions

The `inclusions` and `exclusions` properties (see [Core properties](#core-properties)) support both plain binding names and Java-style regular expressions prefixed with `regex:`.

```yaml
scs-outbox:
  bindings:
    inclusions:
      - "produce-book-created-out-0"
      - "regex:produce-.*-out-\\d+"
    exclusions:
      - "my-test-binding-out-0"
      - "regex:test-.*-out-\\d+"
```

**Rules:**
- Exclusions always take precedence over inclusions.
- Plain binding names are validated at startup against declared bindings (fail-fast if not found).
- Regex patterns are not validated against declared bindings (a warning is logged if no bindings match).
- Invalid regex patterns cause a fail-fast error on startup.

### Dedicated database connection pool

By default, scs-outbox shares the application's connection pool for both message capture and publishing. Under high load or broker connectivity issues, the publishing process may exhaust the shared pool. A dedicated pool isolates publishing from the application.

> [!IMPORTANT]
> The dedicated connection pool **must** connect to the **same** database as the primary pool. Both capture and publishing must access the same outbox data.

#### JDBC

Define a `DataSource` bean named `outboxPublishingDataSource`:

```java
@Configuration
public class OutboxDataSourceConfig {

  @Bean
  @ConfigurationProperties("app.datasource.outbox-publishing")
  public DataSourceProperties outboxPublishingDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  public DataSource outboxPublishingDataSource() {
    return this.outboxPublishingDataSourceProperties()
        .initializeDataSourceBuilder()
        .type(HikariDataSource.class)
        .build();
  }
}
```

```yaml
app:
  datasource:
    outbox-publishing:
      url: jdbc:postgresql://localhost:5432/mydb
      username: db_user
      password: ${DB_PASSWORD}
      hikari:
        maximum-pool-size: 5
        minimum-idle: 2
        connection-timeout: 30000
```

#### MongoDB

Define a `MongoTemplate` bean named `outboxPublishingMongoTemplate`:

```java
@Configuration
public class OutboxMongoConfig {

  @Bean
  public MongoClient outboxPublishingMongoClient() {
    final ConnectionString cs = new ConnectionString("mongodb://db_user:${DB_PASSWORD}@localhost:27017/myDatabase");
    final MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(cs)
        .applyToConnectionPoolSettings(b -> b.maxSize(5).minSize(2))
        .build();
    return MongoClients.create(settings);
  }

  @Bean
  public MongoTemplate outboxPublishingMongoTemplate(
      @Qualifier("outboxPublishingMongoClient") MongoClient mongoClient) {
    return new MongoTemplate(mongoClient, "myDatabase");
  }
}
```

#### Fallback

If no dedicated bean is defined, scs-outbox uses the default application `DataSource` or `MongoTemplate` for both capture and publishing.

### Lock provider

scs-outbox uses [ShedLock](https://github.com/lukas-krecan/ShedLock) to coordinate the publishing process across instances. A default `LockProvider` is registered automatically.

To override it, define a custom `LockProvider` Spring bean.

## Recommendations

### Synchronous producers

To avoid message loss, configure your Spring Cloud Stream producers in synchronous mode:

**Kafka:**

```properties
spring.cloud.stream.kafka.bindings.<binding>.producer.sync=true
```

See the [Kafka binder documentation](https://docs.spring.io/spring-cloud-stream/reference/kafka/kafka_overview.html#kafka-producer-properties).

**RabbitMQ:**

```properties
spring.cloud.stream.rabbit.bindings.<binding>.producer.producerType=STREAM_SYNC
```

See the [RabbitMQ binder documentation](https://docs.spring.io/spring-cloud-stream/reference/rabbit/rabbit_overview/prod-props.html).

### Kafka linger property

scs-outbox sends messages individually. Set `linger.ms=0` to avoid unnecessary batching delays:

```properties
spring.cloud.stream.kafka.binder.configuration.linger.ms=0
```

## Known Issues

- **Deserialization errors block publishing**: if a message cannot be deserialized (e.g., class changes or corrupted data), publishing stops at that message to preserve ordering. All messages preceding it in the current batch are published. An error is logged with the message ID and destination. **Resolution**: manually remove or fix the problematic message in the database.
- **Duplicated `spring.integration.send` metric count**: Spring Integration counts the message twice — once during capture and once during the publishing step. This is expected behavior.
- **ShedLock contention under high throughput**: ShedLock ensures only one instance publishes at a time. For extremely high message volumes, this can become a bottleneck. Evaluate whether the outbox pattern is the right fit for your throughput requirements.
- **PostgreSQL table names must be lowercase**: scs-outbox does not use quoted identifiers. If you customize table names, ensure they are lowercase to avoid case-sensitivity issues.
- **Integration test lock cleanup**: when running integration tests that trigger publishing, the publishing task may not finish before the test tears down, leaving the ShedLock lock unreleased. Restart the database between tests to avoid this.

