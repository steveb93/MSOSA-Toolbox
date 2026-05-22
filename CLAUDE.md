# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Repo Does

This repo converts UAF 1.2 (Unified Architecture Framework) system models into a Neo4j knowledge graph. There are two pipelines:

1. **Java Maven plugin** (`msosa-model-exporter/`) — installs into MSOSA (MagicDraw) 2022x Hotfix 2 and exports directly to Neo4j over Bolt from within the modelling tool.
2. **Python MCP server** (`graph_mcp_driver/`) — exposes Neo4j Cypher and Fuseki SPARQL queries to Claude Desktop via the Model Context Protocol, enabling Claude to query the graph.

Neo4j runs in Docker (`docker-compose/docker-compose.yml`) on `bolt://localhost:7687`. The database user is always `neo4j`; the password and the host data directory are read from `docker-compose/.env` (`NEO4J_PASSWORD`, `NEO4J_DATA_DIR`). Copy `docker-compose/.env.example` to `docker-compose/.env` and fill in values before bringing the stack up — Compose will refuse to start otherwise.

---

## Build Commands

### Java Plugin (Maven)

The MSOSA 2022x SDK jars are checked into `/msosa-sdk/` at the repo root (shared classpath for any plugin in this toolbox). Register them once with the bundled script, then build:

```powershell
cd msosa-model-exporter
.\install-msosa-jars.ps1   # one-time: registers jars from ../msosa-sdk into your local Maven repo
mvn package                # produces fat jar + deployable zip in target/
```

The zip (`target/msosa-model-exporter-1.5.0-Preview-plugin.zip`) extracts to a folder you drop into `<MSOSA_HOME>/plugins/`.

### Python MCP Server

```powershell
cd graph_mcp_driver
pip install -e .
python -m graph_mcp_driver.server
```

### Docker — Neo4j

```powershell
cd docker-compose
cp .env.example .env   # one-time: edit .env and set NEO4J_PASSWORD + NEO4J_DATA_DIR
docker compose up -d
docker compose down
```

### Graph Initialisation (run once before first export)

```powershell
cypher-shell -u neo4j -p "$env:NEO4J_PASSWORD" -f cypher/init_uaf_graph.cypher
```

### Neo4j Connection Test

```powershell
python Test/test_neo4j_connection.py
```

---

## Architecture

### Java Plugin Data Flow

```
MSOSA Project
    └── UAFModelTraverser          walks packages, finds UAF-stereotyped elements
            ├── UAFStereotypeRegistry   resolves stereotype → {Neo4j label, domain, layer}
            ├── UAFElementDTO           immutable node DTO (builder pattern)
            └── UAFRelationshipDTO      immutable edge DTO (28 type constants)

UAFElementDTO list + UAFRelationshipDTO list
    └── Neo4jExportService         manages Bolt driver, batched MERGE writes
            └── Neo4jCypherBuilder      builds parameterised Cypher (no string interpolation)
```

`ExportAction` runs the whole pipeline in a `SwingWorker` so MSOSA stays responsive.

### Neo4j Graph Model

Every exported element carries **one label** — its stereotype (e.g. `:Capability`) — plus a `stereotype` property with the same name. There is no generic `:UAFElement` marker label; the documented universal filter for all exported elements is `WHERE n.stereotype IS NOT NULL` (see `cypher/query-cookbook.cypher`).

```
(:Capability {id, name, stereotype:'Capability', domain:'STRATEGIC', ...})
    -[:INSTANCE_OF]->
(:Stereotype {name:'Capability', domain:'STRATEGIC'})
    -[:BELONGS_TO]->  (:Domain {name:'STRATEGIC'})
```

The `:Stereotype` and `:Domain` nodes are the **pre-existing metamodel** — created by `init_uaf_graph.cypher`. The plugin merges instance nodes on top and wires them with `:INSTANCE_OF`.

### Key Architectural Decisions

- **Fat jar with relocation**: The Neo4j driver is shaded into `com.uaf.shaded.neo4j.driver` to avoid classpath collisions with MagicDraw's own bundled libraries.
- **Stereotype registry as single source of truth**: `UAFStereotypeRegistry` maps every MSOSA stereotype name to domain/layer enums. If MSOSA renames a stereotype or you add new ones, only this file changes.
- **Parameterised Cypher only**: `Neo4jCypherBuilder` never interpolates strings into Cypher. Neo4j labels and relationship types (which cannot be parameterised) are passed through `sanitiseLabel()` / `sanitiseRelType()` which strip everything except `[a-zA-Z0-9_]`.
- **Tagged values as `tv_*` properties**: All UAF tagged values are flattened onto the node with a `tv_` prefix (special characters replaced with `_`).

### Python MCP Server

`graph_mcp_driver/server.py` exposes two FastMCP tools:

- **`run_cypher(query)`** — executes arbitrary Cypher over Bolt and returns records as `list[dict]`.
- **`run_sparql(query)`** — POSTs SPARQL 1.1 SELECT/ASK to the Fuseki endpoint (`http://localhost:3030/uaf/sparql` by default; override via `NEO4J_SPARQL_URL`) and returns one dict per binding row.

Credentials come from environment variables; defaults match the Docker setup. Claude Desktop loads the server via `Claude_Desktop_Config/claude_desktop_config.json`.

### SPARQL / Ontology Overlay (Stage 2)

The MCP server speaks SPARQL because an **Apache Jena Fuseki** sidecar runs alongside Neo4j, loaded from a periodic RDF dump of the Neo4j graph plus the UAF MVO T-Box. The Java plugin still writes Cypher to Neo4j — Neo4j is the system of record; Fuseki is the SPARQL query overlay with RDFS reasoning.

**Why Fuseki and not an in-Neo4j SPARQL layer.** We tried neosemantics (n10s) first; it provides Cypher→RDF translation and ontology import but **no SPARQL query endpoint**. We then tried Ontop with the Neo4j JDBC driver; Ontop 5.x ships with Java 11 while Neo4j JDBC v6 (the one with SQL→Cypher translation) requires Java 17 — hard incompatibility. Fuseki sidesteps both: stock image, full SPARQL 1.1, RDFS reasoning out of the box. The trade-off is freshness — Fuseki sees the snapshot from the last dump. Since MSOSA exports are themselves manual, refreshing the dump after each export is a natural pipeline step.

**Pieces and where they live**:

| Artefact | Role |
|---|---|
| `ontology/uaf-mvo.ttl` | OWL T-Box (Strategic + Operational + Resource) — generated by `ontology/codegen/generate_mvo.py` from the seeded `:Stereotype` metamodel + `UAFRelationshipDTO.REL_*` constants. |
| `ontology/dump/uaf-instance.ttl` | A-Box — generated by `ontology/codegen/dump_to_rdf.py` walking `:UAFElement` nodes via Bolt. |
| `ontology/fuseki/configuration/uaf.ttl` | Fuseki assembler config — in-memory `uaf` dataset, RDFS-Exp reasoner, auto-loads both TTLs from `/staging/`. |
| `docker-compose/docker-compose.fuseki.yml` | Overlay that adds the `fuseki-uaf` container to the main compose stack. |
| `ontology/queries/semantic-search-examples.sparql` | Anchor SPARQL queries that ground Claude's semantic-search answers. |

**End-to-end stand-up**:

```powershell
cd F:/OneDrive/_VSCode/MSOSA-Toolbox/docker-compose

# 0. One-time: copy .env.example to .env and set NEO4J_PASSWORD,
#    FUSEKI_ADMIN_PASSWORD, NEO4J_DATA_DIR
cp .env.example .env

# 1. Bring up Neo4j (plus Fuseki via overlay)
docker compose -f docker-compose.yml -f docker-compose.fuseki.yml up -d

# 2. Seed the UAF metamodel into Neo4j (idempotent)
cypher-shell -u neo4j -p "$env:NEO4J_PASSWORD" -f ../cypher/init_uaf_graph.cypher

# 3. Generate the T-Box from the seeded metamodel
python ../ontology/codegen/generate_mvo.py

# 4. After any MSOSA export — dump the live graph and reload Fuseki
python ../ontology/codegen/dump_to_rdf.py
docker compose -f docker-compose.yml -f docker-compose.fuseki.yml restart fuseki

# 5. Smoke test
pytest ../Test/test_sparql_endpoint.py -v -m neo4j
```

The strategic rationale, MVO/MVG cadence, and migration roadmap (Stages 1–4) are in `Ontology-Approach-to-Knowledge.md`. The Java plugin is unchanged in this stage.

---

## Deployment

To deploy the plugin to MSOSA:

1. Run `mvn package` in `msosa-model-exporter/`
2. Unzip `target/msosa-model-exporter-1.5.0-Preview-plugin.zip` into `<MSOSA_HOME>/plugins/`
3. Restart MSOSA
4. Plugin appears under **Tools → UAF Neo4j Export**

Connection settings are stored in `<MSOSA_HOME>/plugins/msosa-model-exporter/neo4j-connection.properties` and editable via the **Configure Connection** dialog without restarting.

---

## Stereotype Names

Stereotype names in `UAFStereotypeRegistry` must exactly match what the MSOSA UAF 1.2 profile reports. To verify the names in your installation, run this in MSOSA's scripting console:

```groovy
com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getAllStereotypes(
    com.nomagic.magicdraw.core.Application.getInstance().getProject()
).each { println it.getName() }
```

If a stereotype is applied to a model element but not found in the registry, the traverser skips the element silently (logged at WARNING level).
