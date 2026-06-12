# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Exclusion rules for testing and build directories in ORT analyzer configuration.

### Changed

- Updated maven release CI workflow to use a GitHub App Token, enable GPG commit signing, and configure secure HTTPS git remote URLs.

### Fixed

- Replace project's long name with "Outbox for Spring Cloud Stream".

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

[Unreleased]: https://github.com/InditexTech/scs-outbox/compare/1.0.0...HEAD

[1.0.0]: https://github.com/InditexTech/scs-outbox/compare/0.2.0...1.0.0

[0.2.0]: https://github.com/InditexTech/scs-outbox/compare/0.1.0...0.2.0

[0.1.0]: https://github.com/InditexTech/scs-outbox/releases/tag/0.1.0
