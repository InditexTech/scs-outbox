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

The configuration is applied by a `DefaultBinderFactory.Listener` (`SyncProducerBinderFactoryListener`), which Spring Cloud Stream invokes
once the binder child context has been refreshed and **before** the binder is cached or used to create any binding.

For every outbox-enabled producer binding served by that binder, the listener reads the effective producer properties through
`ExtendedPropertiesBinder#getExtendedProducerProperties(bindingName)` and, when they are not synchronous, either switches them or fails.
The returned object is the instance cached by `AbstractExtendedBindingProperties`, so the change is what the binder uses when it creates the
producer binding.

This is the only point where the effective producer configuration is known, because binder-specific properties may be declared:

- in the main environment, as `spring.cloud.stream.<binder>.bindings.<binding>.producer.*`, or
- in the **binder child environment**, as `spring.cloud.stream.binders.<name>.environment.spring.cloud.stream.<binder>.*`, which Spring
  Cloud Stream materialises only inside the child context it creates for that binder.

Binder support is keyed by the binder's own `ExtendedBindingProperties#getDefaultsPrefix()` (`spring.cloud.stream.kafka.default` for Kafka).
Keying on the prefix the binder reports itself removes any need to infer a binder *type* from the binding, the default binder or the
classpath, and works unchanged for named binder instances such as `kafka-pipe`.

Alternatives considered and rejected:

| Alternative | Why it was rejected |
|-------------|---------------------|
| `EnvironmentPostProcessor` contributing properties | **Tried first, and it does not work in general.** Two independent defects: (1) frameworks layered on top of Spring Boot commonly expose their own configuration namespace and relocate it into `spring.cloud.stream.*` / `scs-outbox.*` from an `EnvironmentPostProcessor` ordered at `Ordered.LOWEST_PRECEDENCE`; reading the environment earlier observes an empty set of bindings and silently configures nothing. (2) It cannot see `spring.cloud.stream.binders.<name>.environment.*`, so it neither notices that the application already enabled synchronous publishing nor avoids silently overriding an explicit decision to disable it. |
| `ApplicationContextInitializer` contributing properties | Fixes the ordering defect, because initializers run after every `EnvironmentPostProcessor`, but not the binder child environment defect. It would still have to scan `spring.cloud.stream.binders.*.environment.*` by hand to avoid overriding the application. |
| `ProducerMessageHandlerCustomizer` bean | Spring Cloud Stream resolves this bean **by type**. An application that declares its own customizer would break the context with `NoUniqueBeanDefinitionException`. It also requires compile-time access to binder-specific handler classes. |
| `BeanPostProcessor` on the binder-specific binding properties | Requires a hard dependency on the binder implementation, which `scs-outbox-core` does not have, and the properties live in the binder child context rather than the main one. |
| `DefaultBinderFactory.Listener` (**chosen**) | Sees the fully resolved configuration, is independent of how and when the application's properties reached the environment, and needs no binder dependency: the setting is read and written through a `BeanWrapper`. Listeners are injected as a `Collection`, so coexisting with an application's own listener is safe. |

The listener implements `Ordered` at `HIGHEST_PRECEDENCE`, but this is **best effort only**: Spring Cloud Stream injects the listeners as a
`Collection`, which Spring materialises as a `LinkedHashSet` and therefore does not sort. The relative order follows bean registration, so a
third-party listener that validates the producer configuration may run first and reject a binding this listener was about to configure.

### Supported binders

| Binder | Defaults prefix | Property | Required value |
|--------|-----------------|----------|----------------|
| Kafka | `spring.cloud.stream.kafka.default` | `spring.cloud.stream.kafka.bindings.<binding>.producer.sync` | `true` |

### Interaction with application-provided properties

The listener **never overrides a setting declared by the application**. A binding is left untouched when the binder child environment binds
either

- the binding-scoped property `spring.cloud.stream.kafka.bindings.<binding>.producer.sync`, or
- the binder-wide default `spring.cloud.stream.kafka.default.producer.sync`.

The second check is not redundant. Spring Cloud Stream resolves binding-scoped entries over binder-wide defaults **regardless of property
source ordering**, so setting a binding-scoped value would silently override an explicit `spring.cloud.stream.kafka.default.producer.sync=false`
declared by the application.

### Fail-fast validation

Because the listener stands aside for application-provided configuration, an explicit `producer.sync=false` on an outbox-enabled binding
would reintroduce exactly the silent message loss this feature exists to prevent. The listener therefore classifies each outbox-enabled
producer binding:

| Situation | Behaviour | Rationale |
|-----------|-----------|-----------|
| Supported binder, the application declares a non-synchronous value | **Startup fails** with an `IllegalStateException` naming the binder, the binding, the property, its value and the two documented opt-outs | scs-outbox *knows* the configuration is unsafe |
| Supported binder, nothing declared | The producer is switched to synchronous and the affected bindings are reported at `INFO` | The application expressed no intent, so scs-outbox applies the guarantee it needs |
| Binder is unknown, or is not an `ExtendedPropertiesBinder` | `WARN` listing the binder and the supported binders | scs-outbox *cannot tell* whether the configuration is unsafe. Failing would break every non-Kafka user |

Failing rather than silently overriding was chosen deliberately. Both options provide the same guarantee, but overriding discards an
explicit, intentional application setting and makes the runtime behaviour diverge from what the application declared. Failing
surfaces the misconfiguration immediately with an actionable message. This is consistent with `MessageCaptureTxService`, which already uses
`Propagation.MANDATORY` to fail loudly when a message is sent outside a transaction rather than silently coping with it.

The blast radius of the fail-fast behaviour is small and deliberate: applications that never configured the property keep working, because
the listener configures it for them. Startup only fails for an application that explicitly selected an asynchronous producer on an
outbox-enabled binding, which is precisely the configuration that was already losing messages.

### Scope of the affected bindings

Only bindings that are **both** outbox-enabled and producer candidates are affected.

Outbox membership reuses `OutboxProperties.Bindings#matches(..)`, the same inclusion/exclusion logic used by `OutboxChannelInterceptor`
through `OutboxServiceProperties.isOutboxEnabledFor(..)`. The logic lives in `Bindings` so both call sites share exactly one implementation.

A binding is considered a producer candidate when it declares a destination, its name does not follow the Spring Cloud Stream convention for
function inputs (`<function>-in-<index>`), and it does not declare consumer-only settings. Configuring an inbound binding would be harmless
for the binder, but would make the listener report inbound bindings as violations.

Bindings that name a *different* binder are skipped, so a multi-binder application is only ever evaluated against the binder that actually
serves each binding.

### Opt-out

| Opt-out | Effect |
|---------|--------|
| `scs-outbox.bindings.exclusions` | The binding is no longer managed by the outbox at all, so no synchronous producer constraint applies. This is the correct opt-out when a binding must publish asynchronously |
| `scs-outbox.bindings.sync-producers.enabled=false` | Disables both the configuration and the validation globally. The application becomes fully responsible for configuring synchronous producers; message loss is possible. A `WARN` is logged |

---

## Consequences

### Positive

- Applications using the Kafka binder get the outbox delivery guarantee by default, without having to know a binder-specific property.
- A configuration that breaks the guarantee can no longer start silently.
- It works the same whether the application configures Spring Cloud Stream directly or through a framework that owns its own configuration
  namespace and relocates it late.
- Properties declared in a binder child environment are honoured.
- `scs-outbox-core` gains no new dependency; binder support is a data-only entry in `SyncProducerBinderRegistry`, and the setting is read
  and written through a `BeanWrapper`.

### Negative

- An application that explicitly sets `producer.sync=false` on an outbox-enabled binding will stop starting after upgrading. This is
  intentional, and the error message lists both opt-outs.
- The applied configuration is a mutation of the binder's properties object rather than a property source, so it does not appear in
  `/actuator/env`. The affected bindings are reported at `INFO` instead.
- The listener mutates a `@ConfigurationProperties` bean of the binder child context. A re-binding of that context would discard the
  change; binder child contexts are not refresh-scoped, so this does not occur in practice.

### Limitations

- **Validation happens when the binder is created, not at context refresh.** Declared output bindings are bound during startup, so the check
  still fails the application. A binding whose binder is never created is never validated; for a destination first used by `StreamBridge` at
  runtime, the check runs at that moment instead.
- **Bindings absent from `BindingServiceProperties`** — a destination created on the fly by `StreamBridge` without any declared binding is
  not enumerated and is therefore not configured automatically.
- **Binders other than Kafka** — RabbitMQ is *not* supported in this iteration. Its synchronous producer type (`producerType=STREAM_SYNC`)
  only applies to the RabbitMQ Stream binder and requires the RabbitMQ stream plugin; the default `AMQP` producer type exposes no
  synchronous mode at all, so no honest one-property mapping exists. RabbitMQ bindings fall into the unsupported-binder `WARN` path.
- **Binders that are not `ExtendedPropertiesBinder`** — a binder without binder-specific extended properties exposes no such setting to
  configure, and its bindings are reported with a `WARN`.
- **Other binder factory listeners** — because the listener order is not sortable, an application or framework that registers its own
  `DefaultBinderFactory.Listener` validating the producer configuration may run first and fail the context before this listener has
  configured the bindings.
