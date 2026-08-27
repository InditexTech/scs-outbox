# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.0] - 2026-08-27

### Added

- Exclusion rules for testing and build directories in ORT analyzer configuration.
- [#46](https://github.com/InditexTech/scs-outbox/pull/46) Allow configuring a dedicated executor for after-commit triggers

### Changed

- Updated maven release CI workflow to use a GitHub App Token, enable GPG commit signing, and configure secure HTTPS git remote URLs.

### Fixed

- [#64](https://github.com/InditexTech/scs-outbox/pull/64) Use `asdf set` instead of the removed `asdf local` command in the SonarCloud analysis workflow, which failed on asdf 0.16+.
- [#39](https://github.com/InditexTech/scs-outbox/pull/39) Replace project's long name with "Outbox for Spring Cloud Stream".
- Restore git-level credentials for the maven-release-plugin push in the release workflow; the GitHub App Token migration removed the credential helper, which broke `release:prepare`.

### Dependencies

- [#17](https://github.com/InditexTech/scs-outbox/pull/17) chore(deps): bump org.apache.maven.plugins:maven-javadoc-plugin from 3.10.0 to 3.12.0 in /code
- [#18](https://github.com/InditexTech/scs-outbox/pull/18) chore(deps-dev): bump org.apache.maven.plugins:maven-compiler-plugin from 3.13.0 to 3.15.0 in /code
- [#19](https://github.com/InditexTech/scs-outbox/pull/19) chore(deps-dev): bump org.apache.maven.plugins:maven-surefire-plugin from 3.5.5 to 3.5.6 in /code
- [#21](https://github.com/InditexTech/scs-outbox/pull/21) chore(deps): bump org.mariadb.jdbc:mariadb-java-client from 3.5.5 to 3.5.8 in /code
- [#27](https://github.com/InditexTech/scs-outbox/pull/27) chore(deps-dev): bump org.apache.maven.plugins:maven-failsafe-plugin from 3.5.5 to 3.5.6 in /code
- [#28](https://github.com/InditexTech/scs-outbox/pull/28) chore(deps-dev): bump org.apache.maven.plugins:maven-gpg-plugin from 3.2.5 to 3.2.8 in /code
- [#29](https://github.com/InditexTech/scs-outbox/pull/29) chore(deps): bump org.apache.maven.plugins:maven-source-plugin from 3.3.1 to 3.4.0 in /code
- [#30](https://github.com/InditexTech/scs-outbox/pull/30) chore(deps-dev): bump org.sonatype.central:central-publishing-maven-plugin from 0.5.0 to 0.11.0 in /code
- [#31](https://github.com/InditexTech/scs-outbox/pull/31) chore(deps): bump org.postgresql:postgresql from 42.7.5 to 42.7.11 in /code
- [#35](https://github.com/InditexTech/scs-outbox/pull/35) chore(deps): bump org.jacoco:jacoco-maven-plugin from 0.8.14 to 0.8.15 in /code
- [#36](https://github.com/InditexTech/scs-outbox/pull/36) chore(deps): bump de.flapdoodle.embed:de.flapdoodle.embed.mongo.spring4x from 4.24.0 to 4.33.0 in /code
- [#59](https://github.com/InditexTech/scs-outbox/pull/59) chore(deps): bump org.springframework.boot:spring-boot-dependencies from 4.0.6 to 4.1.1 in /code
- [#60](https://github.com/InditexTech/scs-outbox/pull/60) chore(deps): bump shedlock.version from 7.7.0 to 7.9.0 in /code
- [#62](https://github.com/InditexTech/scs-outbox/pull/62) chore(deps): bump org.springframework.cloud:spring-cloud-dependencies from 2025.1.1 to 2025.1.3 in /code

## [1.0.1] - 2026-07-28

### Fixed

- [#51](https://github.com/InditexTech/scs-outbox/pull/51) outboxExecutorService silently adopts any ExecutorService bean instead of requiring the exact bean name

## [1.0.0] - 2026-06-03

## [0.2.0] - 2026-06-03

### Added

- Dependabot support for Maven dependencies in the `/code` module.
- Third-party license files in `THIRD-PARTY-LICENSES/`.
- New onboarding documentation with a minimal end-to-end setup example.
- Complete configuration examples for PostgreSQL + Kafka, MariaDB + Kafka, and MongoDB + Kafka.

### Changed

- Improved `README.md` with clearer installation and configuration guidance.
- Documented customization options for JDBC table names, schema, and MongoDB collection names.
- Updated `CODEOWNERS` to the `scs-outbox` maintainers team.
- Refined licensing and reuse metadata in `NOTICE` and `REUSE.toml`.

### Fixed

- Fixed Sonar project key formatting in the PR verification workflow.

### Dependencies

- Updated `org.springframework.boot:spring-boot-dependencies` from `4.0.4` to `4.0.6`.
- Updated `org.apache.avro:avro` from `1.11.4` to `1.12.0`.
- Updated `org.apache.maven.plugins:maven-enforcer-plugin` from `3.5.0` to `3.6.3`.

## [0.1.0] - 2026-05-11

### Added

- Initial outbox library implementation
- Maven build configuration with javadocs and sources plugins
- CI/CD workflows for release and testing

[Unreleased]: https://github.com/InditexTech/scs-outbox/compare/1.1.0...HEAD

[1.1.0]: https://github.com/InditexTech/scs-outbox/compare/1.0.1...1.1.0

[1.0.1]: https://github.com/InditexTech/scs-outbox/compare/1.0.0...1.0.1

[1.0.0]: https://github.com/InditexTech/scs-outbox/compare/0.2.0...1.0.0

[0.2.0]: https://github.com/InditexTech/scs-outbox/compare/0.1.0...0.2.0

[0.1.0]: https://github.com/InditexTech/scs-outbox/releases/tag/0.1.0
