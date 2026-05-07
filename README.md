# MSOSA-Toolbox

![MSOSA](https://img.shields.io/badge/MSOSA-2022x%20HF2-0076A8)
![UAF](https://img.shields.io/badge/UAF-1.2-orange)
![Java](https://img.shields.io/badge/Java-11-yellowgreen?logo=openjdk&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white)
![Neo4j](https://img.shields.io/badge/Neo4j-5.x-008CC1?logo=neo4j&logoColor=white)

> [!IMPORTANT]
> All plugins in this toolbox target **No Magic MSOSA 2022x Hotfix 2** (MagicDraw). They are not tested against earlier or later MSOSA releases.

A curated collection of open-source plugins and tooling that extend **MSOSA 2022x HF2** for teams working with the [UAF 1.2](https://www.omg.org/spec/UAF/) profile defined by the Object Management Group (OMG).

---

## Plugins

| Plugin | Description | Status |
|--------|-------------|--------|
| [uaf-neo4j-plugin](uaf-neo4j-plugin/) | Exports UAF 1.2 model elements and relationships to a Neo4j knowledge graph over Bolt | [![Build](https://github.com/steveb93/UAF-Repo/actions/workflows/uaf-neo4j-build.yml/badge.svg)](https://github.com/steveb93/UAF-Repo/actions/workflows/packaging.yml) |

> New plugins can be added as subdirectories following the conventions in [Contributing](#contributing).

---

## Repository Structure

```
MSOSA-Toolbox/
├── uaf-neo4j-plugin/       # MSOSA plugin — exports UAF model to Neo4j
│   ├── src/
│   ├── cypher/             # Graph initialisation scripts
│   └── pom.xml
├── neo4j_mcp_driver/       # Python MCP server — exposes Neo4j to Claude Desktop
├── docker-compose/         # Neo4j container setup
└── Test/                   # Connection and smoke tests
```

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| MSOSA (MagicDraw) | **2022x HF2** | UAF 1.2 profile must be installed |
| Java JDK | 11 | Required to build the Maven plugin |
| Apache Maven | 3.8+ | |
| Python | 3.12 | For the MCP server |
| Docker Desktop | Latest | Runs the Neo4j container |
| Neo4j | 5.x | Provided via Docker Compose |

---

## Contributing

### Adding a new plugin

1. Create a subdirectory at the repo root: `<plugin-name>/`
2. Include a `README.md` inside it describing what the plugin does, its build steps, and any MSOSA version constraints.
3. Add the plugin to the [Plugins](#plugins) table above.
4. If the plugin has a CI workflow, link its badge in the table.

### Conventions

- **Java plugins** use Maven with the MagicDraw API jars as `provided` scope.
- **Fat jars** must shade all non-MagicDraw dependencies to avoid classpath collisions.
- **Cypher** must use parameterised queries only — no string interpolation into Cypher statements.
- Label and relationship-type identifiers must be sanitised to `[a-zA-Z0-9_]` before use.

### Releasing a new version

The CI pipeline handles building and publishing. Contributors own the version number and the git tag — CI does the rest.

#### Step 1 — Bump the version

Use the Maven Versions Plugin to update the `revision` property in `pom.xml`:

```powershell
cd uaf-neo4j-plugin
mvn versions:set-property -Dproperty=revision -DnewVersion=0.4.1
```

Commit and push to `main`:

```powershell
git add uaf-neo4j-plugin/pom.xml
git commit -m "chore: bump version to 0.4.1"
git push origin main
```

CI will automatically update any hardcoded version strings in the docs to match.

#### Step 2 — Tag the release

Tags drive the release pipeline. The tag name **must** match the `revision` in `pom.xml` (with a `v` prefix).

```powershell
# Full release from main
git tag v0.4.1
git push origin v0.4.1

# Preview release from preview branch
git tag v0.4.1-Preview
git push origin v0.4.1-Preview
```

Pushing the tag triggers:

1. Build and test
2. Branch verification (release tags must come from `main`; `-Preview` tags from `preview`)
3. Version string sync committed back to the base branch
4. A **draft** GitHub Release created with the plugin zip attached and auto-generated release notes

#### Step 3 — Publish the draft release

Open the draft at **GitHub → Releases**, review the notes, then click **Publish release**.

> The release type (major / minor / patch) is auto-detected from the version number:
> `v1.0.0` → major, `v0.5.0` → minor, `v0.4.1` → patch.

---

#### Alternative: trigger via workflow_dispatch

Use this when you need to create a release without pushing a tag manually (e.g. from a CI environment or to control the draft flag explicitly).

Go to **Actions → Build & Release → Run workflow** and fill in:

| Input | Example | Notes |
|---|---|---|
| `version` | `v0.4.1` | Must match `revision` in `pom.xml` exactly |
| `release_type` | `minor` | Informational — appears in the release title |
| `draft` | `true` | Uncheck to publish immediately |

The workflow verifies the version matches `pom.xml` before building and will fail fast if they differ.

---

## Licence

> [!NOTE]
> Repository currently in development — licence to be confirmed.
