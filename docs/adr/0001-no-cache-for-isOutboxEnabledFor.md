# ADR-0001: No In-Memory Cache for `isOutboxEnabledFor`

| Field       | Value                        |
|-------------|------------------------------|
| **Status**  | Accepted                     |
| **Date**    | 2026-03-06                   |
| **Authors** | scs-outbox team              |

---

## Context

As part of an issue, the `inclusions` and `exclusions` binding properties were extended to support Java-style regular expressions (prefixed with `regex:`). This change introduced `BindingMatcher`, a class that wraps either an exact `String` comparison or a pre-compiled `java.util.regex.Pattern`.

The method `OutboxServiceProperties.isOutboxEnabledFor(String bindingName)` is now evaluated by streaming over a list of `BindingMatcher` objects and calling `matcher.matches(bindingName)` on each. This raised the question of whether results should be cached in memory, given that:

- The method sits on the **hot path**: it is invoked on every call to `OutboxChannelInterceptor.preSend()`, which is triggered by each `StreamBridge.send()` in the application.
- A secondary call-site exists in `SpringIntegrationMeterFilter.map()`, invoked at meter registration time (cold path, negligible frequency).
- The set of possible `bindingName` values is finite, small (typically 1–10), and immutable during the application lifecycle.

To make an evidence-based decision, benchmarks were run measuring 1,000,000 invocations after JIT warm-up across representative scenarios.

---

## Benchmark Results

All measurements were taken on the same JVM instance with 50,000 warm-up iterations before timing.

| Scenario | Avg / call | Total (1 M calls) |
|---|---:|---:|
| Empty lists (default, no config) | ~6 ns | 6 ms |
| 3 exact inclusion names, match found | ~24 ns | 24 ms |
| 1 regex inclusion, match found | ~110 ns | 110 ms |
| Mixed: 3 inclusions + 2 exclusions with regex (match) | ~137 ns | 137 ms |
| Worst case: 21 regex inclusions + 10 regex exclusions | ~585 ns | 585 ms |
| `ConcurrentHashMap` cache (mixed 3+2, same key repeated) | ~54 ns | 54 ms |
| `HashMap` cache (mixed 3+2, same key repeated) | ~60 ns | 60 ms |
| Realistic: 5 bindings round-robin, **no cache** | ~85 ns | 85 ms |
| Realistic: 5 bindings round-robin, **with cache** | ~9 ns | 9 ms |

### Key observation

In the most realistic scenario (5 bindings rotating, with regex), a cache delivers a **~9× speedup** in relative terms (85 ns → 9 ns). However, the **absolute saving is 76 ns per message**.

To put this in perspective: a full `StreamBridge.send()` with the outbox pattern (message serialisation + transactional DB write + potential I/O) costs **1–50 ms**. The contribution of `isOutboxEnabledFor` in the worst realistic case (~137 ns) represents **less than 0.01%** of the total operation cost.

At 10,000 messages/second (a high-throughput scenario), caching would save approximately **0.76 ms per second** of CPU time — a saving that is not measurable in any real production profile.

---

## Decision

**A cache is NOT added to `isOutboxEnabledFor`.**

The method will continue to evaluate `BindingMatcher.matches()` on every invocation without memoising results.

---

## Rationale

### 1. The cost is already negligible by design

`BindingMatcher` pre-compiles the `Pattern` object **once** at construction time (in `afterPropertiesSet` startup phase). The per-call cost is limited to `Pattern.matcher(input).matches()`, which is a highly JIT-optimised native operation. There is no repeated regex compilation on the hot path.

### 2. The absolute saving is not observable

76 ns saved per message is below the noise floor of any meaningful production metric. No real application would detect the difference in latency, throughput, or CPU usage.

### 3. A cache introduces correctness risk

`PublicationProperties` already uses `@RefreshScope`, enabling dynamic configuration updates at runtime. If `OutboxProperties` ever adopts `@RefreshScope` (a natural future step), a cached result map would silently serve stale values after a configuration refresh — a hard-to-detect correctness bug.

### 4. Increased complexity for zero observable benefit

A cache requires:
- Choosing a thread-safe map type (`ConcurrentHashMap`) and its initialisation strategy.
- Defining an invalidation or rebuild strategy for the cache lifecycle.
- Documenting the caching behaviour.
- Adapting unit tests to cover cached vs. non-cached states.

None of this complexity yields a measurable improvement in any realistic workload.

### 5. Preserves the method as a pure function

Without a cache, `isOutboxEnabledFor` is a **pure function**: same input always produces the same output with no side effects. This maximises testability, predictability, and composability.

### 6. YAGNI / premature optimisation

There is no profiling evidence, no bug report, and no performance requirement that identifies this method as a bottleneck. Optimising it pre-emptively violates the YAGNI principle and the general guideline against premature optimisation.

---

## Considered Alternatives

### Alternative A: Lazy `ConcurrentHashMap` cache

```java
private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

public boolean isOutboxEnabledFor(final String bindingName) {
    return this.cache.computeIfAbsent(bindingName, this::evaluate);
}
```

**Rejected** because:
- `computeIfAbsent` on a hot-path `ConcurrentHashMap` costs ~54 ns on a warm cache — actually **slower** than the uncached evaluation for the common case (exact names, ~24 ns) and only marginally faster for regex.
- Silently breaks if `OutboxProperties` becomes `@RefreshScope`-aware in the future.

### Alternative B: Eager `HashMap` populated in `afterPropertiesSet`

Pre-compute the result for every declared SCS binding at startup:

```java
private Map<String, Boolean> eagerCache;

@Override
public void afterPropertiesSet() {
    // ... existing validation ...
    this.eagerCache = bindingServiceProperties.getBindings().keySet().stream()
        .collect(toMap(identity(), this::evaluate));
}
```

**Rejected** because:
- Same negligible absolute gain.
- Does not cover bindings registered after startup (dynamic binding scenarios).
- Adds state that must be managed and documented.

### Alternative C: Status quo (chosen)

Evaluate `BindingMatcher.matches()` inline on every call with no caching.

**Accepted** because it is simple, correct, future-proof, and the performance cost is demonstrably negligible.

---

## Consequences

- `OutboxServiceProperties.isOutboxEnabledFor` remains a stateless, pure function.
- No additional state or lifecycle management is required in `OutboxServiceProperties`.
- If, in the future, profiling under real production load demonstrates this method to be a bottleneck, **Alternative B** (eager cache in `afterPropertiesSet`) should be revisited as the preferred approach, provided that `OutboxProperties` is not `@RefreshScope`-scoped.

