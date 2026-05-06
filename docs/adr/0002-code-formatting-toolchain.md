# ADR-0002: Code Formatting and Style Enforcement Toolchain

| Field       | Value                        |
|-------------|------------------------------|
| **Status**  | Accepted                     |
| **Date**    | 2026-04-13                   |
| **Authors** | scs-outbox team              |
| **Issue**   | [#520](https://github.com/inditex/lib-springcloudstreamoutbox/issues/520) — Replace amiga-javaformat with open-source code formatter |

---

## Context

The project previously relied on `com.inditex.libamfmt:amiga-javaformat-maven-plugin`, a proprietary plugin that enforced code formatting during the `validate` Maven phase. This plugin is not available in public repositories, which prevents the project from being built and maintained in open-source environments.

The goal is to replace it with an equivalent toolchain composed entirely of open-source, publicly available plugins, while preserving the existing style rules and IDE configuration files already present in `src/main/config/`.

---

## Decision

The proprietary plugin is replaced by three open-source Maven plugins, each with a clearly bounded responsibility:

| Plugin | Group ID / Artifact ID | Version | Responsibility |
|--------|------------------------|---------|----------------|
| **Spotless** | `com.diffplug.spotless:spotless-maven-plugin` | 2.44.5 | Java source code formatting and import ordering |
| **maven-checkstyle-plugin** | `org.apache.maven.plugins:maven-checkstyle-plugin` | 3.6.0 | Java structural and naming style rules |
| **sortpom-maven-plugin** | `com.github.ekryd.sortpom:sortpom-maven-plugin` | 4.0.0 | POM element ordering |

All three run during the `validate` Maven phase and are enforced in CI.

### Developer commands

```bash
# Auto-format Java source files
mvn -f code/pom.xml spotless:apply

# Sort POM files
mvn -f code/pom.xml com.github.ekryd.sortpom:sortpom-maven-plugin:sort

# Full check (same as CI)
mvn -f code/pom.xml validate
```

---

## Rationale

### Spotless

- Reuses the existing `src/main/config/eclipse-java-google-style.xml` formatter configuration (140-character line limit already encoded) and `src/main/config/eclipse-java-google-style.importorder`, so no new style definitions are needed.
- Provides a `spotless:apply` goal that **auto-corrects** formatting issues locally. This is the key differentiator: developers do not need to manually fix formatting violations before committing.
- Handles the two aspects that require a formatter (not just a linter): code layout and import ordering.

### maven-checkstyle-plugin

- Reuses the existing `src/main/config/checkstyle-java-google-style-17.xml` ruleset, which already encodes the project's style conventions.
- Covers rules that a formatter cannot enforce: member naming patterns, declaration order, Javadoc structure, parameter naming, and others.
- Operates as a **linter**: it reports violations but does not modify files, which complements Spotless.

### sortpom-maven-plugin

- Reuses the existing `src/main/config/pom-code-convention.xml` sort order definition.
- Operates on `pom.xml` files, a scope entirely separate from the Java source tools.
- Provides a `sortpom:sort` goal for local auto-correction, analogous to `spotless:apply`.

---

## Overlaps and How They Are Avoided

Spotless and maven-checkstyle-plugin share conceptual territory over Java source files, which creates two specific areas of potential conflict:

### 1. Import ordering

Both tools are capable of checking import order. Spotless applies the order via the Eclipse formatter; Checkstyle's `ImportOrder` module validates it statically.

**Resolution**: The `ImportOrder` module has been **removed from the Checkstyle configuration**. Import ordering is exclusively Spotless's responsibility. This eliminates the risk of the two tools disagreeing (which was observed during implementation: Eclipse groups `java.*` and `javax.*` together using prefix matching, while Checkstyle's `ImportOrder` treated them as separate groups).

### 2. Line length for Java files

Both tools can enforce the 140-character line limit on `.java` files. Spotless wraps long lines via the Eclipse formatter (`lineSplit=140`); Checkstyle's `LineLength` module checks after the fact.

**Resolution**: The `LineLength` module in Checkstyle has been **restricted to non-Java file extensions** (`json`, `yaml`, `xml`, `sql`, etc.), which Spotless does not process. Line length for Java files is exclusively Spotless's responsibility.

### Resulting responsibility matrix

| Rule | Spotless | Checkstyle | sortpom |
|------|:--------:|:----------:|:-------:|
| Java code formatting (braces, indentation, wrapping) | ✅ | — | — |
| Import ordering | ✅ | — | — |
| Line length (Java) | ✅ | — | — |
| Line length (XML, YAML, JSON…) | — | ✅ | — |
| Naming conventions (members, parameters, methods) | — | ✅ | — |
| Declaration order | — | ✅ | — |
| Javadoc structure | — | ✅ | — |
| Trailing whitespace | ✅ | ✅ | — |
| Tab characters | ✅ | ✅ | — |
| POM element ordering | — | — | ✅ |

> **Note**: trailing whitespace and tab characters are checked by both tools. This is intentional: Spotless removes them during `apply`, while Checkstyle acts as a safety net for files Spotless may not cover (e.g., non-Java resources). There is no risk of the two producing contradictory results.

---

## Considered Alternatives

### Alternative A: Google Java Format via `fmt-maven-plugin`

**Rejected** because Google Java Format enforces a fixed 100-character column limit with no configuration option to change it. The project requires 140 characters, which made this option incompatible without forking or patching.

### Alternative B: Spotless alone (without maven-checkstyle-plugin)

**Rejected** because Spotless is a formatter, not a linter. It cannot enforce naming conventions, declaration order, Javadoc rules, or other structural constraints. Dropping Checkstyle would remove existing style enforcement that has no equivalent in Spotless.

### Alternative C: maven-checkstyle-plugin alone (without Spotless)

**Rejected** because Checkstyle only **reports** violations; it cannot auto-correct them. Developers would need to fix formatting manually on every commit. Spotless's `apply` goal significantly reduces friction.

### Alternative D: Spotless + Checkstyle with full overlap (no deduplication)

**Rejected** because it was observed during implementation that the two tools disagree on import grouping (the `java`/`javax` case). Maintaining duplicate rules in two tools with subtly different semantics creates a maintenance burden and a source of confusing CI failures.

---

## Consequences

- The build is fully reproducible in any public Maven repository environment.
- Developers have a single command (`spotless:apply`) to auto-fix all Java formatting issues locally.
- The Checkstyle ruleset is narrowed to rules that are purely structural or naming-related, reducing the risk of future conflicts with Spotless.
- The three configuration files already present in `src/main/config/` continue to serve as the single source of truth for style rules.
- If the Eclipse formatter version bundled with Spotless produces different output in a future upgrade, a `spotless:apply` run is sufficient to realign all files.
