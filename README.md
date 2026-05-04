# MSOSA-Toolbox

[![Build](https://github.com/steveb93/UAF-Repo/actions/workflows/packaging.yml/badge.svg)](https://github.com/steveb93/UAF-Repo/actions/workflows/packaging.yml)
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
| [uaf-neo4j-plugin](uaf-neo4j-plugin/) | Exports UAF 1.2 model elements and relationships to a Neo4j knowledge graph over Bolt | [![Build](https://github.com/steveb93/UAF-Repo/actions/workflows/packaging.yml/badge.svg)](https://github.com/steveb93/UAF-Repo/actions/workflows/packaging.yml) |

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

---

## Licence

> [!NOTE]
> Repository currently in development — licence to be confirmed.
