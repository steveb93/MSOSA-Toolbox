# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Plugin Does

Exports UAF 1.2 architectural elements and relationships from a Catia Magic MSOSA 2022x Hotfix 2 project into a Neo4j graph database running in Docker. Exported instance nodes are linked via `:INSTANCE_OF` to pre-existing UAF domain meta-model stereotype nodes already in the graph, creating a queryable knowledge graph spanning both meta-model and instance-level architecture.

```
MSOSA Project
    │  [UAFModelTraverser]
    ▼
UAFElementDTO / UAFRelationshipDTO
    │  [Neo4jCypherBuilder → parameterised MERGE]
    ▼
Neo4j (Docker :7687)
    ├── :UAFElement:Capability      ──[:INSTANCE_OF]──► :Stereotype
    ├── :UAFElement:OperationalPerformer ...
    └── [:PERFORMS] [:TRACES_TO] [:SATISFIES] ...
```

---

## Build

### 1. Install MSOSA SDK jars into local Maven repo

The MSOSA SDK jars are checked into `/msosa-sdk/` at the repo root (shared across any future MSOSA plugins). Run the provided script once from `msosa-model-exporter/`:

```powershell
.\install-msosa-jars.ps1
```

### 2. Build

```powershell
mvn clean package
```

Outputs:
- `target/msosa-model-exporter-1.0.5-Preview-Preview.jar` — fat jar (Neo4j driver bundled + relocated)
- `target/msosa-model-exporter-1.0.5-Preview-Preview-plugin.zip` — drop into `<MSOSA_HOME>/plugins/`

### 3. Deploy to MSOSA

Either use **Help → Resource/Plugin Manager → Install Plugin from File**, or unzip manually:

```
<MSOSA_HOME>/plugins/msosa-model-exporter/
    msosa-model-exporter-1.0.5-Preview-Preview.jar
    plugin.xml
    neo4j-connection.properties
```

Restart MSOSA. Plugin appears under **Tools → UAF Neo4j Export**.

---

## Neo4j Setup (Docker)

```powershell
# from repo root
cd ../docker-compose
docker compose up -d

# initialise UAF metamodel schema (run once)
cypher-shell -u neo4j -p Password123 -f ../cypher/init_uaf_graph.cypher
```

`init_uaf_graph.cypher` creates constraints, full-text indexes, and the pre-existing metamodel nodes (`:Stereotype`, `:Domain`, `:ArchitectureLayer`) that exported instances link back to.

---

## Architecture

### Package layout

```
com.uaf.neo4j.plugin
    UAFNeo4jPlugin.java               plugin entry point, config lifecycle
    UAFExporterActionsConfigurator    injects Tools → UAF Neo4j Export menu
    ExportAction / ConfigureAction / AboutAction

com.uaf.neo4j.plugin.model
    UAFStereotypeRegistry             single source of truth: stereotype → {label, domain, layer}
    UAFModelTraverser                 walks MSOSA project tree, extracts DTOs
    UAFElementDTO                     immutable node DTO (builder pattern)
    UAFRelationshipDTO                immutable edge DTO (28 type constants)

com.uaf.neo4j.plugin.neo4j
    Neo4jCypherBuilder                parameterised MERGE Cypher (no string interpolation)
    Neo4jExportService                Bolt driver lifecycle, batched writes, INSTANCE_OF links

com.uaf.neo4j.plugin.ui
    ConnectionDialog                  edit URI / credentials / batch size
    ExportSummaryDialog               post-export counts + error list
```

`ExportAction` drives the pipeline in a `SwingWorker` — MSOSA stays responsive during export.

### Key design decisions

- **Fat jar with relocation** — Neo4j driver is shaded into `com.uaf.shaded.neo4j.driver` to avoid classpath collisions with MagicDraw's own bundled libraries.
- **Stereotype registry is the only place to change** when MSOSA renames a UAF stereotype or you add new ones. `UAFModelTraverser` consults it; elements whose stereotypes are not found are skipped silently (logged at WARNING).
- **Parameterised Cypher only** — `Neo4jCypherBuilder` never interpolates user data. Labels and rel-types (which cannot be parameterised) pass through `sanitiseLabel()` / `sanitiseRelType()` which strip everything except `[a-zA-Z0-9_]`.
- **MERGE on `id`** — all writes are idempotent. Re-export updates existing nodes but does not delete elements removed from the model.

---

## Neo4j Node Model

Every exported UAF element gets **dual labels**: `:UAFElement` + its stereotype label (e.g. `:Capability`), so queries can target all UAF elements generically or a specific type efficiently.

### Core properties

| Property | Description |
|---|---|
| `id` | MagicDraw element ID — stable MERGE key |
| `name` | Element name from model |
| `qualifiedName` | Fully qualified model path |
| `stereotype` | Applied UAF stereotype name |
| `domain` | UAF domain (`STRATEGIC` / `OPERATIONAL` / `RESOURCE` / `SERVICE` / `PERSONNEL` / `ACQUISITION` / `SECURITY`) |
| `layer` | Architecture layer (`CONCEPTUAL` / `LOGICAL` / `PHYSICAL`) |
| `packageName` | Package hierarchy |
| `diagramId` / `diagramName` | Diagrams that include this element |
| `documentation` | Model comments / notes |
| `modelFile` | Source MSOSA project name |

### Tagged values

All UAF tagged values are flattened as `tv_<tagName>` properties (special characters replaced with `_`), e.g. `tv_nationality`, `tv_capabilityLevel`.

### Metamodel link

```cypher
(:UAFElement)-[:INSTANCE_OF]->(:Stereotype)-[:BELONGS_TO]->(:Domain)
                                           -[:IN_LAYER]->(:ArchitectureLayer)
```

---

## Neo4j Relationship Model

Relationships carry: `id`, `uafType` (UML metaclass), `name`, `domain`.

**Supported Neo4j relationship types** (28):

`REALISES` · `TRACES_TO` · `ASSIGNED_TO` · `SATISFIES` · `REFINES` · `INFLUENCES` · `DEPENDS_ON` · `COMPOSED_OF` · `SPECIALISES` · `EXHIBITS` · `CONTRIBUTES_TO` · `EXPOSES` · `PROVIDES` · `PERFORMS` · `CONNECTED_TO` · `FLOWS_TO` · `TRIGGERS` · `PRECEDES` · `ENABLES` · `SUPPORTS` · `IMPLEMENTS` · `ALLOCATED_TO` · `INSTANCE_OF` · `CONTAINED_IN` · `ASSOCIATED_WITH` · `DEPENDENCY` · `GENERALIZATION` · `INFORMATION_FLOW` · `CONTROL_FLOW`

---

## Stereotype Names

Names in `UAFStereotypeRegistry` must exactly match what the MSOSA UAF 1.2 profile reports. To verify:

```groovy
// Run in MSOSA scripting console
com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper.getAllStereotypes(
    com.nomagic.magicdraw.core.Application.getInstance().getProject()
).each { println it.getName() }
```

---

## Connection Configuration

Editable via **Tools → UAF Neo4j Export → Configure Connection** or directly in:
`<MSOSA_HOME>/plugins/msosa-model-exporter/neo4j-connection.properties`

```properties
neo4j.uri=bolt://localhost:7687
neo4j.user=neo4j
neo4j.password=Password123
neo4j.database=neo4j
neo4j.batch.size=500
```

Changes take effect without restarting MSOSA.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "No UAF elements found" | No UAF stereotypes applied | Confirm UAF 1.2 profile is loaded; elements have stereotypes |
| Connection refused | Neo4j container not running | `docker compose up -d`; check port 7687 |
| INSTANCE_OF links missing | Stereotype nodes absent | Re-run `init_uaf_graph.cypher` |
| Slow export on large model | Batch size too small | Increase `neo4j.batch.size` to 500–1000 |
| `ClassNotFoundException` on startup | SDK jars not in local Maven repo | Re-run `.\install-msosa-jars.ps1` (jars live in `/msosa-sdk/` at the repo root) |
| Stereotype skipped silently | Name mismatch in registry | Verify name via MSOSA scripting console (see above) |
