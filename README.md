# GitHub Migration CLI

A command-line tool for migrating repositories, teams, and members from one GitHub organisation to another. The tool first produces a human-readable **plan** you can review before anything is changed, then applies the migration with a single confirmation prompt.

---

## Features

- Transfers all repositories from a source organisation to a destination organisation
- Invites organisation members (skips members who already belong to the destination)
- Recreates teams, including their members, repository permissions, and optional parent-team nesting
- Preserves direct collaborator permissions on each repository
- Two conflict-resolution strategies for resources that already exist in the destination
- Dry-run `plan` command — review exactly what will happen before committing
- Optional `--output` flag to save the plan to a file
- Repository blacklist to exclude specific repos from migration
- Colourised terminal output via [Mordant](https://github.com/ajalt/mordant)

---

## Requirements

| Dependency | Version |
|------------|---------|
| JDK        | 21      |
| Kotlin     | 2.2.0   |
| Gradle     | 8.7     |

A GitHub **Personal Access Token** (classic) with the following scopes is required:

- `repo` — read/transfer repositories
- `admin:org` — read/write organization members and teams

When migrating to an organisation that uses **Enterprise Managed Users (EMU)** or other provisioned accounts, a single token may not have access to both organisations. In that case provide separate tokens with `--source-token` and `--target-token` instead of `--token`.

---

## Installation

### Pre-built binary (recommended)

Download the latest release archive for your platform from the [Releases](https://github.com/Ordina-Group/github-migration/releases) page, unzip it, and place the `github-migration` binary on your `PATH`.

### Build from source

```bash
./gradlew nativeCompile
# Output: build/native/nativeCompile/github-migration
```

The `package` task wraps the binary in a versioned zip:

```bash
./gradlew package
# Output: build/distributions/github-migration-<version>-<os>.zip
```

> **Note:** GraalVM Native Image must be installed for `nativeCompile`. The standard JVM distribution is built with `./gradlew installDist`.

---

## Configuration

The tool authenticates against the GitHub API using a token. You can provide it via the `--token` flag or store it in `~/.gradle/gradle.properties` for local development:

```properties
gpr.user=your-github-username
gpr.key=your-github-token
```

The library dependency is published to GitHub Packages and resolved at build time using the same credentials:

```properties
# ~/.gradle/gradle.properties
gpr.user=<github-username>
gpr.key=<github-token>
```

---

## Usage

All commands share a common set of options:

| Option | Short | Required | Description |
|---|---|---|---|
| `--token` | `-t` | ✅ * | GitHub Personal Access Token for both organisations |
| `--source-token` | | ✅ * | Token for the source organisation (use instead of `--token` when tokens differ) |
| `--target-token` | | ✅ * | Token for the target organisation (use instead of `--token` when tokens differ) |
| `--source` | `-s` | ✅ | Source organisation login |
| `--destination` | `-d` | ✅ | Destination organisation login |
| `--strategy` | | | Conflict strategy: `Merge` (default) or `Prefix` |
| `--blacklist` | | | Repository name to exclude (repeatable) |
| `--parent-team` | | | Slug of an existing team in the destination to nest all migrated teams under |

\* Provide either `--token` **or** both `--source-token` and `--target-token`.

### `plan` — preview the migration

Generates and prints the migration plan without making any changes.

```bash
github-migration plan \
  --token ghp_... \
  --source my-source-org \
  --destination my-destination-org
```

Save the plan to a file for review or archiving:

```bash
github-migration plan \
  --token ghp_... \
  --source my-source-org \
  --destination my-destination-org \
  --output migration-plan.txt
```

### `apply` — execute the migration

Generates the plan, displays it, and prompts for confirmation before proceeding.

```bash
github-migration apply \
  --token ghp_... \
  --source my-source-org \
  --destination my-destination-org
```

You will be asked:

```
Do you wish to apply these changes (yes/no):
```

Type `yes` to proceed; anything else cancels the migration safely.

---

## Conflict resolution strategies

When a resource (team or repository) with the same name already exists in the destination organisation, the `--strategy` option controls what happens:

| Strategy | Behaviour |
|---|---|
| `Merge` (default) | Reuses the existing resource — new members/repos are added to the existing team; a repository keeps its original name |
| `Prefix` | Creates a new resource prefixed with the source org login, e.g. `source-org-my-repo` |

---

## Example

Migrate from `acme-corp` to `acme-global`, excluding `legacy-monolith`, and nest all teams under an existing `migrated` parent team:

```bash
github-migration apply \
  --token ghp_... \
  --source acme-corp \
  --destination acme-global \
  --blacklist legacy-monolith \
  --parent-team migrated \
  --strategy Prefix
```

---

## Development

### Commit messages

This project follows [Conventional Commits](https://www.conventionalcommits.org/). JReleaser uses the commit history to auto-generate a grouped changelog on each release.

| Prefix | When to use |
|--------|-------------|
| `feat:` | New user-facing feature (triggers a **minor** version bump) |
| `fix:` | Bug fix (triggers a **patch** version bump) |
| `chore:` | Build, tooling, or dependency changes |
| `ci:` | CI/CD pipeline changes |
| `test:` | Adding or updating tests |
| `docs:` | Documentation only |
| `feat!:` / `fix!:` | Breaking change (triggers a **major** version bump) |



```bash
./gradlew test
```

Tests are written with [Kotest](https://kotest.io/) and [MockK](https://mockk.io/).

### Linting

```bash
./gradlew ktlintCheck   # check style
./gradlew ktlintFormat  # auto-fix
./gradlew detekt        # static analysis
```

---

## Architecture

```
command/
  MainCommand        # Root Clikt command (no-op, owns subcommands)
  PlanCommand        # plan subcommand
  ApplyCommand       # apply subcommand
  MigrationOptions   # Shared Clikt OptionGroup

domain/
  Plan               # Pure data class — the migration plan
  PlanFactory        # createPlan() — builds Plan from raw GitHub data
  PlanRenderer       # Formats a Plan for terminal output
  Repository         # Domain repo with originalName + planned name
  Team               # Domain team with members and repo memberships
  Member             # Domain member
  MigrationError     # Sealed error hierarchy (Arrow Either)

service/
  PlanService        # Fetches GitHub state, returns Either<MigrationError, Plan>
  ApplyService       # Executes the plan, collects and reports partial failures
```

Error handling uses [Arrow](https://arrow-kt.io/) `Either` throughout — `PlanService` short-circuits on the first fatal error (missing organisation), while `ApplyService` collects all partial failures so as much of the migration completes as possible.

---

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
