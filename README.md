# MSOSA-Toolbox

![MSOSA](https://img.shields.io/badge/MSOSA-2022x%20HF2-0076A8)
![Java](https://img.shields.io/badge/Java-11-yellowgreen?logo=openjdk&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white)
![Neo4j](https://img.shields.io/badge/Neo4j-5.26-008CC1?logo=neo4j&logoColor=white)
![Fuseki](https://img.shields.io/badge/Fuseki-SPARQL%201.1-d22128)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)

> [!IMPORTANT]
> All plugins in this toolbox target **No Magic MSOSA 2022x Hotfix 2** (MagicDraw). They are not tested against earlier or later MSOSA releases.

A curated collection of open-source plugins and tooling that extend **MSOSA 2022x HF2** for teams working with **UAF 1.2**, **SysML 1.6**, and **BPMN 2.0** in MSOSA. The toolbox builds a **hybrid knowledge graph** — Neo4j is the system of record (labelled property graph + Cypher), and Apache Jena Fuseki adds a SPARQL 1.1 endpoint with **OWL FB reasoning** (Stage 3) over a UAF Minimum Viable Ontology with SHACL governance shapes across all 7 UAF domains. The two stores are kept in step by the Java plugin's RDF emitter, which writes Turtle and PUTs to Fuseki via SPARQL 1.1 Graph Store Protocol in the same export action; `ontology/codegen/dump_to_rdf.py` remains as the recovery path.

References:
- [UAF 1.2 specification](https://www.omg.org/spec/UAF/) — OMG
- [`Ontology-Approach-to-Knowledge.md`](Ontology-Approach-to-Knowledge.md) — strategic rationale for the LPG-plus-RDF approach (Gartner-anchored, ISO/IEC/IEEE 15288 aligned)
- [`ontology/NEXT-STEPS.md`](ontology/NEXT-STEPS.md) — Stage 3+ migration roadmap

---

## Components

| Component | Description | Status |
|---|---|---|
| [msosa-model-exporter](msosa-model-exporter/) | MSOSA plugin — exports UAF 1.2 / SysML 1.6 / BPMN 2.0 elements and relationships to a Neo4j knowledge graph over Bolt | [![Build](https://github.com/steveb93/UAF-Repo/actions/workflows/msosa-model-exporter-build.yml/badge.svg)](https://github.com/steveb93/UAF-Repo/actions/workflows/msosa-model-exporter-build.yml) |
| [graph_mcp_driver](graph_mcp_driver/) | Python MCP server — exposes `run_cypher` (Neo4j), `run_sparql` (Fuseki), SHACL validation, GDS-driven capability-gap recommender, and decision-analytics tools to MCP-capable LLM hosts | — |
| [dashboard](dashboard/) | Streamlit decision-intelligence dashboard — four panels over the SPARQL view (coverage gaps, top-N influence, gap recommendations, domain composition). Stage 5. | — |
| [ontology](ontology/) | Generated OWL T-Box, Fuseki configuration, dump script, anchor SPARQL queries | — |
| [docker-compose](docker-compose/) | Neo4j stack + `docker-compose.fuseki.yml` SPARQL overlay. Copy `docker-compose/.env.example` to `docker-compose/.env` and set passwords + `NEO4J_DATA_DIR` + `FUSEKI_HEAP` before first run. | — |

> New plugins can be added as subdirectories following the conventions in [Contributing](#contributing).

## Ontology overlay — Stages 2, 3, 4, 5 status

Per the staged migration in `Ontology-Approach-to-Knowledge.md`:

- **Stage 2 (live)** — Apache Jena Fuseki provides a real SPARQL 1.1 endpoint with **OWL FB reasoning** over a UAF Minimum Viable Ontology covering the **7 UAF domains plus Shared + SysML 1.6 + BPMN 2.0** — **193 OWL classes, 35 ObjectProperties**, code-generated from the seeded `:Stereotype` metamodel in Neo4j.
- **Stage 3 (whole-ontology complete)** — Stage-3 governance now spans all 7 UAF domains: **24 SHACL NodeShapes**, **12 `owl:someValuesFrom` restrictions**, **16 `owl:inverseOf` pairs**, pairwise domain disjointness, `uaf:dominates` transitivity wired to the `<<Dominates>>` UML Dependency stereotype for architectural Security domain reasoning, an ERD→Resource bridge (`uaf:Entity rdfs:subClassOf uaf:ResourceInformation`) so ERD content participates in resource-exchange traversals, and a separate UML data-marking slice (`ontology/uml-data-marking.ttl`) with `dm:dominates` over the DoD TS/S/C/U lattice — joins via `skos:notation` against `uaftv:securityClassification` literals on ERD Entities. Validator at `ontology/codegen/validate_shacl.py`; MCP server exposes the live conformance report.
- **Stage 4 emitter-side (live)** — Java plugin emits RDF directly via `RDFExportService` alongside the Cypher path, and optionally PUTs to Fuseki's Graph Store Protocol. `dump_to_rdf.py` retained as recovery path.
- **Stage 5 (in progress)** — **Composite AI** layer on top of the SPARQL view. Neo4j GDS bootstrapped (`cypher/gds-cookbook.cypher` + smoke test) with PageRank / betweenness / WCC / Louvain projections; GDS write-back materialised as `uafgds:*` triples (typed-literal parity locked across Python `dump_to_rdf.py` and Java `RDFTripleBuilder`); content-based capability-gap recommender exposed as MCP tools (`find_capability_gaps`, `recommend_resources_for_gap`, `find_top_n_by_pagerank`, `count_nodes_by_domain`); Streamlit decision-intelligence dashboard with four panels for the "Decision makers" persona. **Fuseki query-architecture split**: new `/uaf-raw/sparql` non-reasoning endpoint for analytical queries that consume directly-emitted triples — ~3 orders of magnitude faster than the OWL FB endpoint for predicate-alternation `FILTER NOT EXISTS` patterns. Remaining: agent-memory demo (`#119`).

Remaining outside Stage 5: outbound `SERVICE` federation templates and the ExportSummaryDialog SHACL row. See `ontology/NEXT-STEPS.md` for the open backlog and decision log.

**Endpoints**:
- Bolt (system of record): `bolt://localhost:7687`
- SPARQL with OWL FB reasoning (semantic queries, SHACL, subsumption): `http://localhost:3030/uaf/sparql`
- SPARQL non-reasoning (analytical queries, GDS recommender, dashboard): `http://localhost:3030/uaf-raw/sparql`
- Fuseki web UI: `http://localhost:3030/`
- Decision dashboard: `http://localhost:8501` (after `streamlit run dashboard/app.py`)

**Refresh cadence after each MSOSA export**:

The Stage 4 dual-emitter rollout (`v1.3.0-Preview` onwards) gives the plugin two write targets: the existing **Neo4j (LPG)** target and a new **RDF** target that writes Turtle to disk and optionally PUTs it to Fuseki via SPARQL 1.1 Graph Store Protocol — refreshing the SPARQL view in a single click with no docker restart.

**Preferred path** (`v1.3.0-Preview` onwards):

1. **Tools → MSOSA Model Exporter** in MagicDraw.
2. Tick both **Neo4j (LPG via Cypher)** and **RDF Turtle file (and optionally PUT to Fuseki)** under *Export Targets*.
3. Tick the **Also PUT to Fuseki /data endpoint** sub-option to push directly.
4. Click **Export**. Both stores refresh in one operation.

**Fallback / recovery path** (rebuild Fuseki from Neo4j when the plugin RDF emitter is off or has misbehaved):

```powershell
# Re-dump Neo4j → ontology/dump/uaf-instance.ttl and reload Fuseki.
# Compose reads docker-compose/.env automatically because the first -f file
# is in docker-compose/ — see docker-compose/.env.example for required vars.
python ontology/codegen/dump_to_rdf.py
docker compose -f docker-compose/docker-compose.yml `
               -f docker-compose/docker-compose.fuseki.yml restart fuseki
```

The post-export summary dialog has a **Copy SPARQL Refresh Cmd** button that copies the fallback two-line sequence to the clipboard. The Java plugin's **Tools → MSOSA Model Exporter → Open SPARQL Endpoint** menu item opens the Fuseki UI directly.

See [`ontology/NEXT-STEPS.md`](ontology/NEXT-STEPS.md) for Stage 3 (native triplestore, OWL 2 RL reasoning, SHACL validation) and Stage 5 (composite AI / decision intelligence) gating criteria.

**Visualisation**: Fuseki only exposes SPARQL. For browsing the OWL T-Box (classes, properties, restrictions) use the upstream-blessed hosted instance at <https://service.tib.eu/webvowl/> — choose **Ontology → Select ontology file** and upload `ontology/uaf-mvo.ttl` (and optionally `uaf-mvo-axioms.ttl`). The ontology is already public in this repo, so a third-party upload is fine. Self-hosting was explored and rejected: the upstream Docker image is broken, and a from-source rebuild needs WebVOWL (JS frontend) **and** OWL2VOWL (Java backend) plus an nginx proxy — more intermediary steps than the GraphML pipeline this stack already dropped. See `ontology/NEXT-STEPS.md` for the decision log.

---

## Repository Structure

```
MSOSA-Toolbox/
├── msosa-model-exporter/            # MSOSA plugin — exports UAF/SysML/BPMN model to Neo4j (LPG)
│   ├── src/
│   └── pom.xml
├── cypher/                          # Graph schema + metamodel seed (init_uaf_graph.cypher) + query cookbook
├── msosa-sdk/                       # MSOSA 2022x SDK jars (shared build classpath for any plugin)
├── graph_mcp_driver/                # Python MCP server — run_cypher, run_sparql, validate_shacl, GDS recommender, decision-analytics tools
├── dashboard/                       # Streamlit decision-intelligence dashboard (Stage 5)
├── docker-compose/
│   ├── docker-compose.yml           # Neo4j 5.26 + n10s + APOC + GDS
│   └── docker-compose.fuseki.yml    # Fuseki SPARQL overlay (Stage 2) — exposes /uaf/sparql (OWL FB) + /uaf-raw/sparql (non-reasoning, Stage 5)
├── ontology/
│   ├── uaf-mvo.ttl                  # AUTO-GENERATED T-Box (UAF + SysML + BPMN)
│   ├── uaf-mvo-axioms.ttl           # Hand-authored OWL axioms (Stage 3: inverses, disjointness, restrictions)
│   ├── shapes/uaf-shapes.ttl        # SHACL governance shapes (Stage 3, all 7 UAF domains)
│   ├── codegen/
│   │   ├── generate_mvo.py          # T-Box codegen from the seeded :Stereotype metamodel
│   │   ├── dump_to_rdf.py           # Neo4j → Turtle A-Box dump (rdflib) — recovery path, emits uafgds:* triples for GDS write-back
│   │   └── validate_shacl.py        # pyshacl validator against the live Fuseki dataset
│   ├── fuseki/configuration/uaf.ttl # Fuseki assembler config (two services share one base model: reasoning + non-reasoning)
│   ├── queries/                     # Anchor SPARQL queries grounding semantic-search use case
│   ├── dump/                        # Placeholder A-Box only; populated copy is local-only and must never be committed
│   └── NEXT-STEPS.md                # Stage 3+ roadmap (decision log records n10s/Ontop/GraphDB rejection)
├── cypher/
│   ├── init_uaf_graph.cypher        # Seeds :Stereotype + :Domain metamodel
│   ├── query-cookbook.cypher        # Read-only LPG patterns
│   └── gds-cookbook.cypher          # GDS projections + PageRank / WCC / Louvain + write-back (Stage 5)
├── Test/                            # Python tests (connection, MCP tools, SPARQL endpoint, recommender)
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

The CI pipeline handles building and publishing. Contributors own the version number and the git tag — CI does the rest. Two channels:

- **Full releases** ship from `main` with tags like `v1.3.2`.
- **Preview releases** ship from `preview` with tags like `v1.3.2-Preview`. Use these for incremental work that hasn't been promoted to `main` yet.

#### Step 1 — Bump the version on the target branch

`<branch>` is `main` for a full release or `preview` for a preview. Make sure you're on the right one:

```powershell
git checkout <branch>
git pull origin <branch>

# Bumps <revision> in msosa-model-exporter/pom.xml via the Maven Versions Plugin
# AND propagates the new version into msosa-model-exporter-X.Y.Z artefact
# references inside repo .md files. Run from the repo root.
# Pass -DryRun first to preview the changes without writing.
.\bump-version.ps1 -Version 1.3.2-Preview   # or 1.3.2 for main
```

Commit and push:

```powershell
git add msosa-model-exporter/pom.xml CLAUDE.md msosa-model-exporter/CLAUDE.md msosa-model-exporter/README.md
git commit -m "chore: bump version to 1.3.2-Preview"
git push origin <branch>
```

The push triggers the build workflow. The `sync-version-refs` CI job re-runs the same `*.md` rewrite as a safety net — because `bump-version.ps1` already did the sweep locally, that CI job is normally a no-op (no follow-up bot commit). If you ever bump `pom.xml` without the script, the bot's `chore: sync version references…` commit kicks in instead. The loop is broken either way via the `github.event.head_commit.author.name` filter on the build and sync jobs.

#### Step 2 — Tag the release

Tags drive the release pipeline. The tag name **must** match the `revision` in `pom.xml` (with a `v` prefix). Pull first so your local branch includes the bot's sync commit, then tag the tip:

```powershell
git pull origin <branch>

# Full release from main
git tag v1.3.2
git push origin v1.3.2

# Preview release from preview
git tag v1.3.2-Preview
git push origin v1.3.2-Preview
```

The tag may legitimately land on the bot's `chore: sync version references…` commit — that's fine. The release workflow fires regardless of whether the tagged commit was bot- or human-authored (the author filter only applies to branch pushes, not tag pushes).

Pushing the tag triggers:

1. Build and test (against the tagged commit's source tree).
2. Branch verification — `v*-Preview` tags must originate from `preview`; non-`-Preview` tags from `main`.
3. A **draft** GitHub Release with the plugin zip attached and auto-generated release notes.

#### Step 3 — Publish the draft

Open the draft at **GitHub → Releases**, review the notes, then click **Publish release**.

> The release type (major / minor / patch) is auto-detected from the version number:
> `v2.0.0` → major, `v1.4.0` → minor, `v1.3.2` → patch.

---

#### Alternative: trigger via `workflow_dispatch`

Use this when you need to cut a release without pushing a tag — e.g. re-running a release that failed mid-stream, or producing a non-draft release directly.

Go to **Actions → MSOSA Model Exporter — Build & Release → Run workflow** (the workflow file is `.github/workflows/msosa-model-exporter-build.yml`; GitHub's sidebar may cache the previous workflow display name `Build & Release` — same workflow either way) and fill in:

| Input | Example | Notes |
|---|---|---|
| `version` | `v1.3.2-Preview` | Must match `revision` in `pom.xml` exactly |
| `release_type` | `patch` | Informational — appears in the release title |
| `draft` | `true` | Uncheck to publish immediately |

The workflow verifies the version matches `pom.xml` before building and fails fast if they differ.

`workflow_dispatch` is only available when the workflow file exists on the **default branch** (`main`). If you've renamed the workflow on `preview` but not promoted to `main`, the dispatch UI won't surface the new file — use a tag push instead, or promote `preview → main` first.

---

## Licence

> [!NOTE]
> Repository currently in development — licence to be confirmed.
