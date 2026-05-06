_# `code-maven-build_snapshot`

[`code-maven_java-build_snapshot.yml`](../code-maven_java-build_snapshot.yml) workflow deploys a snapshot version to the distribution platform

## Trigger

- Using GitFlow: Any push on `develop` / `develop-*` branches with changes in `code` path.
- Using TBD: Any push on `main` / `main-*` branches with changes in `code` path.

## Where does it run?

`ubuntu-24.04` GitHub infrastructure.

## Versions used

`asdf` and any `Java`, `Maven` and `Node`.

## How does it work?

This workflow relies on asdf to automatically load any tool version defined on the project's `code/.tool-versions` file.

## Jobs

- ### `publish-snapshot-from-pr`

  Publishes a snapshot version from a pull request when triggered by the `/publish-snapshot` comment.

  - **Steps**
    - Validates admin permissions
    - Checks out PR branch
    - Sets up caches and asdf environment
    - Configures GPG and Git
    - Runs `mvn deploy` to publish snapshot to OSSRH

- ### `publish-snapshot-from-dispatch`

  Publishes a snapshot version from a branch when manually triggered.

  - **Steps**
    - Validates admin permissions
    - Checks out specified branch
    - Sets up caches and asdf environment
    - Configures GPG and Git
    - Runs `mvn deploy` to publish snapshot to OSSRH
    - Writes job summary with version information

## Configuration

Snapshots are published to **OSSRH** (OSS Repository Hosting) at `https://s01.oss.sonatype.org/content/repositories/snapshots`.

**Note**: Maven Central's `central-publishing-maven-plugin` does not support snapshot deployments. Snapshots use the traditional `maven-deploy-plugin` with OSSRH repository configuration.
