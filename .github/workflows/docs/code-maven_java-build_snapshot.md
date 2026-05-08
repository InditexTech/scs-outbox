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
    - Sets a PR-specific snapshot version with pattern `X.Y.Z-PR<number>-SNAPSHOT`
    - Runs `mvn deploy` to publish snapshot to OSSRH
    - Comments on the PR with the result, including the published version

- ### `publish-snapshot-from-label`

  Publishes a snapshot version from a pull request automatically when the label `autopublish/snapshot-binaries` is added to it.

  - **Trigger**: `pull_request` event with `labeled` action and label name `autopublish/snapshot-binaries`
  - **Steps**
    - Validates that the user adding the label has write or admin permissions
    - Checks out PR branch using the PR head SHA
    - Sets up caches and asdf environment
    - Configures GPG and Git
    - Sets a PR-specific snapshot version with pattern `X.Y.Z-PR<number>-SNAPSHOT`
    - Runs `mvn deploy` to publish snapshot to OSSRH
    - Comments on the PR with the result, including the published version

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

### PR snapshot versioning

When publishing from a pull request (via comment or label), the artifact version is automatically rewritten to include the PR number, allowing snapshots from different PRs to coexist in the repository without overwriting each other:

| Context | Version pattern | Example |
|---|---|---|
| PR-triggered snapshot | `X.Y.Z-PR<number>-SNAPSHOT` | `1.5.0-PR42-SNAPSHOT` |
| Branch/dispatch snapshot | `X.Y.Z-SNAPSHOT` | `1.5.0-SNAPSHOT` |

The transformation is done using `mvn versions:set` with `-DprocessAllModules=true` to update all modules in the multi-module project.

