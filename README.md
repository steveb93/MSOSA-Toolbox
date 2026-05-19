# MSOSA-Toolbox

![MSOSA](https://img.shields.io/badge/MSOSA-2022x%20HF2-0076A8)
![UAF](https://img.shields.io/badge/UAF-1.2-orange)
![SysML](https://img.shields.io/badge/SysML-1.6-blueviolet)
![BPMN](https://img.shields.io/badge/BPMN-2.0-yellow)
![Java](https://img.shields.io/badge/Java-11-yellowgreen?logo=openjdk&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white)
![Neo4j](https://img.shields.io/badge/Neo4j-5.26-008CC1?logo=neo4j&logoColor=white)
![Fuseki](https://img.shields.io/badge/Fuseki-SPARQL%201.1-d22128)
![Ontology](https://img.shields.io/badge/Ontology-MVO%20v0.2-5b8fb9)

> [!IMPORTANT]
> All plugins in this toolbox target **No Magic MSOSA 2022x Hotfix 2** (MagicDraw). They are not tested against earlier or later MSOSA releases.

A curated collection of open-source plugins and tooling that extend **MSOSA 2022x HF2** for teams working with **UAF 1.2**, **SysML 1.6**, and **BPMN 2.0** in MSOSA. The toolbox builds a **hybrid knowledge graph** — Neo4j is the system of record (labelled property graph + Cypher), and Apache Jena Fuseki adds a SPARQL 1.1 endpoint with RDFS reasoning over a UAF Minimum Viable Ontology. The two stores are kept in step by a Python dump script that runs after each MSOSA export.

References:
- [UAF 1.2 specification](https://www.omg.org/spec/UAF/) — OMG
- [`Ontology-Approach-to-Knowledge.md`](Ontology-Approach-to-Knowledge.md) — strategic rationale for the LPG-plus-RDF approach (Gartner-anchored, ISO/IEC/IEEE 15288 aligned)
- [`ontology/NEXT-STEPS.md`](ontology/NEXT-STEPS.md) — Stage 3+ migration roadmap

---

## Components

| Component | Description | Status |
|---|---|---|
| [uaf-neo4j-plugin](uaf-neo4j-plugin/) | MSOSA plugin — exports UAF 1.2 / SysML 1.6 / BPMN 2.0 elements and relationships to a Neo4j knowledge graph over Bolt | [![Build](https://github.com/steveb93/UAF-Repo/actions/workflows/uaf-neo4j-build.yml/badge.svg)](https://github.com/steveb93/UAF-Repo/actions/workflows/packaging.yml) |
| [neo4j_mcp_driver](neo4j_mcp_driver/) | Python MCP server — exposes `run_cypher` and `run_sparql` tools to Claude Desktop | — |
| [ontology](ontology/) | Generated OWL T-Box, Fuseki configuration, dump script, anchor SPARQL queries | — |
| [docker-compose](docker-compose/) | Neo4j stack + Fuseki SPARQL overlay (overlay file `docker-compose.fuseki.yml`) | — |

> New plugins can be added as subdirectories following the conventions in [Contributing](#contributing).

## Stage 2 ontology overlay (SPARQL)

Stage 2 of the migration described in `Ontology-Approach-to-Knowledge.md` is live: Apache Jena Fuseki provides a real SPARQL 1.1 endpoint with RDFS reasoning over a UAF Minimum Viable Ontology (MVO) covering **all 8 UAF domains plus SysML and BPMN** — 103 OWL classes, 31 ObjectProperties, code-generated from the seeded `:Stereotype` metamodel in Neo4j.

**Endpoints**:
- Bolt (system of record): `bolt://localhost:7687`
- SPARQL (overlay): `http://localhost:3030/uaf/sparql`
- Fuseki web UI: `http://localhost:3030/`

**Refresh cadence after each MSOSA export**:

```powershell
# Re-dump Neo4j → ontology/dump/uaf-instance.ttl and reload Fuseki
python ontology/codegen/dump_to_rdf.py
docker compose -f docker-compose/docker-compose.yml `
               -f docker-compose/docker-compose.fuseki.yml restart fuseki
```

The Java plugin's **Tools → UAF Neo4j Export → Open SPARQL Endpoint** menu item opens the Fuseki UI directly; the post-export summary dialog has a **Copy SPARQL Refresh Cmd** button that copies the two-line refresh sequence above to the clipboard.

See [`ontology/NEXT-STEPS.md`](ontology/NEXT-STEPS.md) for Stage 3 (native triplestore, OWL 2 RL reasoning, SHACL validation) and Stage 4 (full RDF migration) gating criteria.

---

## Repository Structure

```
MSOSA-Toolbox/
├── uaf-neo4j-plugin/                # MSOSA plugin — exports UAF model to Neo4j
│   ├── src/
│   └── pom.xml
├── cypher/                          # Graph schema + metamodel seed (init_uaf_graph.cypher) + query cookbook
├── msosa-sdk/                       # MSOSA 2022x SDK jars (shared build classpath for any plugin)
├── neo4j_mcp_driver/                # Python MCP server — run_cypher + run_sparql tools
├── docker-compose/
│   ├── docker-compose.yml           # Neo4j 5.26 + n10s + APOC + GDS
│   └── docker-compose.fuseki.yml    # Fuseki SPARQL overlay (Stage 2)
├── ontology/
│   ├── uaf-mvo.ttl                  # AUTO-GENERATED T-Box (UAF + SysML + BPMN)
│   ├── codegen/
│   │   ├── generate_mvo.py          # T-Box codegen from the seeded :Stereotype metamodel
│   │   └── dump_to_rdf.py           # Neo4j → Turtle A-Box dump (rdflib)
│   ├── fuseki/configuration/uaf.ttl # Fuseki assembler config (in-mem dataset + RDFS reasoner)
│   ├── queries/                     # Anchor SPARQL queries grounding semantic-search use case
│   ├── dump/                        # (gitignored) latest A-Box dump
│   └── NEXT-STEPS.md                # Stage 3+ roadmap (decision log records n10s/Ontop rejection)
├── Test/                            # Python tests (connection, MCP tools, SPARQL endpoint)
├── Ontology-Approach-to-Knowledge.md # Strategy doc — Gartner-anchored, ISO 15288 aligned
└── CLAUDE.md                        # End-to-end stand-up + architectural decisions
```

---

## Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| MSOSA (MagicDraw) | **2022x HF2** | UAF 1.2 + SysML 1.6 + BPMN 2.0 profiles |
| Java JDK | 11 | Required to build the Maven plugin |
| Apache Maven | 3.8+ | |
| Python | 3.12 | MCP server, codegen, dump script |
| Python deps | `mcp`, `neo4j`, `httpx`, `rdflib` | Install via `pip install -e .[dev]` |
| Docker Desktop | Latest | Neo4j + Fuseki containers |
| Neo4j | **5.26** (pinned) | n10s pins us to 5.x for now; see decision log in `ontology/NEXT-STEPS.md` for n10s/Ontop rejection rationale |
| Apache Jena Fuseki | latest stable (`stain/jena-fuseki:latest`) | SPARQL 1.1 + RDFS reasoner |

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
