# ADR-0003: Automatic Synchronous Producer Configuration

| Field       | Value                        |
|-------------|------------------------------|
| **Status**  | Accepted                     |
| **Date**    | 2026-09-16                   |
| **Authors** | scs-outbox team              |

---

## Context

`OutboxMessagePublisher.publish(..)` treats the `boolean` returned by `StreamBridge.send(..)` as the single delivery signal: when it is
`true`, the outbox record is deleted inside the same transaction.

```java
final boolean sent = this.messageSender.send(message);
if (!sent) {
  throw new MessageNotPublishedException("message [" + message.getId() + "] not published.");
}
this.postSend(message);
this.outboxMessageRepository.delete(message);
```

With an **asynchronous** producer, `StreamBridge.send(..)` returns `true` as soon as the record is handed to the client library, *before*
the broker acknowledges it. A broker outage, retry exhaustion, a record-too-large error or a serialization failure occurring afterwards
loses the message with no trace left in the outbox table. The transactional outbox then provides no delivery guarantee at all.

Until now, synchronous publishing was a documented responsibility of the application:

```properties
spring.cloud.stream.kafka.bindings.<binding>.producer.sync=true
```

Nothing in the library read, injected or verified that property. An application could start perfectly with an asynchronous producer and
silently lose messages. The configuration is also binder-specific, which makes it easy to get wrong.

Issue [#91](https://github.com/InditexTech/scs-outbox/issues/91) asks whether scs-outbox can configure synchronous producers automatically
for the bindings it manages.

---

## Decision

scs-outbox configures synchronous producers automatically for outbox-enabled producer bindings, and refuses to start when an outbox-enabled
binding is explicitly configured as asynchronous.

### Mechanism

An `EnvironmentPostProcessor` (`SyncProducerEnvironmentPostProcessor`) contributes the binder-specific property for every outbox-enabled
producer binding, through a `MapPropertySource` named `scs-outbox-sync-producers` appended **last**.

Alternatives considered and rejected:

| Alternative | Why it was rejected |
|-------------|---------------------|
| `ProducerMessageHandlerCustomizer` bean | Spring Cloud Stream resolves this bean **by type**. An application that declares its own customizer would break the context with `NoUniqueBeanDefinitionException`. It also requires compile-time access to binder-specific handler classes. |
| `BeanPostProcessor` on `KafkaExtendedBindingProperties` | Requires a hard dependency on `spring-cloud-stream-binder-kafka-core`, which `scs-outbox-core` does not have. It also mutates state that is invisible to `/actuator/env` and `/actuator/configprops`. |
| `EnvironmentPostProcessor` (**chosen**) | Contributes plain string properties, so no binder dependency is added to `scs-outbox-core`. The resulting configuration is fully visible through the standard actuator endpoints, and the regular property precedence rules apply. |

### Supported binders

Binder support is declared in `SyncProducerBinderRegistry` as a mapping from binder type to property key and required value:

| Binder type | Property | Required value |
|-------------|----------|----------------|
| `kafka` | `spring.cloud.stream.kafka.bindings.<binding>.producer.sync` | `true` |

The binder type of a binding is resolved by `BinderTypeResolver`, in this order:

1. `spring.cloud.stream.bindings.<binding>.binder`
2. `spring.cloud.stream.default-binder`
3. the single binder declared on the classpath through `META-INF/spring.binders`

A binder *name* is translated into a binder *type* through `spring.cloud.stream.binders.<name>.type`, falling back to the name itself, which
mirrors Spring Cloud Stream's own behaviour. When several binders are on the classpath and the binding declares none, the binder type is
reported as unresolved rather than guessed.

### Interaction with application-provided properties

The post-processor **never overrides a property owned by the application**. A binding is skipped when the application binds either

- the binding-scoped property `spring.cloud.stream.kafka.bindings.<binding>.producer.sync`, or
- the binder-wide default `spring.cloud.stream.kafka.default.producer.sync`.

The second check is not redundant. Spring Cloud Stream resolves binding-scoped entries over binder-wide defaults **regardless of property
source ordering**, so contributing a binding-scoped `true` would silently override an explicit `spring.cloud.stream.kafka.default.producer.sync=false`
set by the application. Appending the property source last is therefore necessary but not sufficient; the explicit check is what makes the
precedence rule honest.

### Fail-fast validation

Because the post-processor stands aside for application-provided configuration, an explicit `producer.sync=false` on an outbox-enabled
binding would reintroduce exactly the silent message loss this feature exists to prevent. `SyncProducerValidator` therefore inspects the
effective configuration at startup and classifies each outbox-enabled producer binding:

| Situation | Behaviour | Rationale |
|-----------|-----------|-----------|
| Supported binder, effective value is not synchronous | **Startup fails** with an `IllegalStateException` naming the binding, the property, its value and the two documented opt-outs | scs-outbox *knows* the configuration is unsafe |
| Supported binder, property not set at all | `WARN` | Only reachable when the binding is not declared through `spring.cloud.stream.bindings.*`, or when the context was not bootstrapped through `SpringApplication` (Spring Boot does not run `EnvironmentPostProcessor`s then). Failing would break valid test contexts |
| Unsupported or unresolvable binder | `WARN` listing the affected bindings and the supported binder types | scs-outbox *cannot tell* whether the configuration is unsafe. Failing would break every non-Kafka user |

Failing rather than silently overriding was chosen deliberately. Both options provide the same guarantee, but overriding discards an
explicit, intentional application setting and makes the runtime behaviour diverge from `application.yml` and `/actuator/env`. Failing
surfaces the misconfiguration immediately with an actionable message. This is consistent with `MessageCaptureTxService`, which already uses
`Propagation.MANDATORY` to fail loudly when a message is sent outside a transaction rather than silently coping with it.

The blast radius of the fail-fast behaviour is small and deliberate: applications that never configured the property keep working, because
the post-processor injects it for them. Startup only fails for an application that explicitly selected an asynchronous producer on an
outbox-enabled binding, which is precisely the configuration that was already losing messages.

### Scope of the affected bindings

Only bindings that are **both** outbox-enabled and producer candidates are affected.

Outbox membership reuses `OutboxProperties.Bindings#matches(..)`, the same inclusion/exclusion logic used by `OutboxChannelInterceptor`
through `OutboxServiceProperties.isOutboxEnabledFor(..)`. The logic was moved into `Bindings` so it can be evaluated before the application
context exists.

A binding is considered a producer candidate when it declares a destination, its name does not follow the Spring Cloud Stream convention for
function inputs (`<function>-in-<index>`), and it does not declare consumer-only settings. Contributing producer properties to an inbound
binding would be harmless for the binder, but would make the validator report inbound bindings as violations.

### Opt-out

| Opt-out | Effect |
|---------|--------|
| `scs-outbox.bindings.exclusions` | The binding is no longer managed by the outbox at all, so no synchronous producer constraint applies. This is the correct opt-out when a binding must publish asynchronously |
| `scs-outbox.bindings.sync-producers.enabled=false` | Disables both the injection and the validation globally. The application becomes fully responsible for configuring synchronous producers; message loss is possible. A `WARN` is logged at startup |

---

## Consequences

### Positive

- Applications using the Kafka binder get the outbox delivery guarantee by default, without having to know a binder-specific property.
- A configuration that breaks the guarantee can no longer start silently.
- The injected configuration is plain properties, visible through `/actuator/env` and `/actuator/configprops`.
- `scs-outbox-core` gains no new dependency; binder support is a data-only entry in `SyncProducerBinderRegistry`.

### Negative

- An application that explicitly sets `producer.sync=false` on an outbox-enabled binding will stop starting after upgrading. This is
  intentional, and the error message lists both opt-outs.
- `scs-outbox-core` now ships a `META-INF/spring.factories` file, which it did not before.

### Limitations

- **Bindings not declared through `spring.cloud.stream.bindings.*`** — dynamic destinations created on the fly by `StreamBridge`, and
  bindings derived from functions without explicit configuration, are invisible to the post-processor. They are reported by the validator
  with a `WARN` when they reach `BindingServiceProperties`, and must be configured manually.
- **Binders other than Kafka** — RabbitMQ is *not* supported in this iteration. Its synchronous producer type (`producerType=STREAM_SYNC`)
  only applies to the RabbitMQ Stream binder and requires the RabbitMQ stream plugin; the default `AMQP` producer type exposes no
  synchronous mode at all, so no honest one-property mapping exists. RabbitMQ bindings fall into the unsupported-binder `WARN` path.
- **Multi-binder ambiguity** — when several binders are on the classpath and a binding declares none explicitly, the binder type is left
  unresolved and the binding is reported with a `WARN` rather than configured with a guess.
