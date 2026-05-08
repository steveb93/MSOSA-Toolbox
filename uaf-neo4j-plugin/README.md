# UAF 1.2 → Neo4j Knowledge Graph Exporter
## Catia Magic MSOSA 2022x Refresh2 Plugin

---

## Overview

This plugin exports UAF 1.2 architectural elements and relationships from a
Catia Magic MSOSA 2022x Refresh2 project into a Neo4j graph database running in Docker.

Exported instance nodes are automatically linked via `:INSTANCE_OF` relationships
to pre-existing UAF domain meta-model stereotype nodes already in your graph,
creating a live, queryable knowledge graph spanning both the meta-model and
instance-level architecture data.

```
MSOSA Project
    │
    │  [UAFModelTraverser]
    ▼
UAFElementDTO / UAFRelationshipDTO
    │
    │  [Neo4jCypherBuilder → parameterised MERGE]
    ▼
Neo4j (Docker :7687)
    ├── :SystemModel {id, name}
    │       └──[:DEFINES]──► :Capability      ──[:INSTANCE_OF]──► :Stereotype
    │       └──[:DEFINES]──► :OperationalPerformer ...
    └── [:PERFORMS] [:TRACES_TO] [:SATISFIES] ...
```

## Architecture

<img src="https://github.com/steveb93/MSOSA-Toolbox/blob/main/msosa_toolbox_architecture.svg" >

---

## Requirements

| Component | Version |
|---|---|
| Catia Magic MSOSA | 2022x Refresh2 |
| Java (plugin compile) | JDK 11+ |
| Neo4j | 4.4.x or 5.x |
| Docker | 20.10+ |
| Maven | 3.8+ |

---

## Project Structure

```
uaf-neo4j-plugin/
├── msosa-api/                              ← Full MSOSA 2022x SDK jar set (checked in)
│   ├── md.jar                              ← MagicDraw core API
│   ├── md_api.jar                          ← MagicDraw public API interfaces
│   └── com.nomagic.magicdraw.uml2-*.jar   ← UML2 / StereotypesHelper (+ ~100 transitive jars)
├── cypher/
│   ├── init_uaf_graph.cypher               ← DB schema + metamodel initialisation (run once)
│   └── query-cookbook.cypher               ← Example Cypher queries
├── src/
│   ├── assembly/
│   │   └── plugin-zip.xml                  ← Maven Assembly descriptor for deployable zip
│   ├── main/
│   │   ├── java/com/uaf/neo4j/plugin/
│   │   │   ├── UAFNeo4jPlugin.java              ← Plugin entry point + config lifecycle
│   │   │   ├── UAFExporterActionsConfigurator.java ← Injects Tools → UAF Neo4j Export menu
│   │   │   ├── ExportAction.java               ← Opens ExportConfigDialog
│   │   │   ├── GraphInspectorAction.java        ← Opens GraphInspectorDialog
│   │   │   ├── AboutAction.java
│   │   │   ├── model/
│   │   │   │   ├── UAFStereotypeRegistry.java   ← Single source of truth: stereotype → domain/layer
│   │   │   │   ├── UAFModelTraverser.java        ← Walks MSOSA project, extracts DTOs
│   │   │   │   ├── UAFElementDTO.java            ← Immutable node DTO (builder pattern)
│   │   │   │   └── UAFRelationshipDTO.java       ← Immutable edge DTO (28 type constants)
│   │   │   ├── neo4j/
│   │   │   │   ├── Neo4jCypherBuilder.java       ← Parameterised MERGE Cypher
│   │   │   │   └── Neo4jExportService.java       ← Bolt driver lifecycle; batched writes; graph queries
│   │   │   └── ui/
│   │   │       ├── ExportConfigDialog.java       ← Screen 1: package selection, options, connection
│   │   │       ├── ExportSummaryDialog.java      ← Post-export counts, errors, Browse Graph button
│   │   │       ├── GraphInspectorDialog.java     ← Screen 2: searchable node table + inspector tabs
│   │   │       ├── GraphPanel.java               ← JGraphX neighbourhood graph (Phase 2b)
│   │   │       └── ConnectionDialog.java         ← Edit URI / credentials / batch size
│   │   └── resources/
│   │       ├── plugin.xml                  ← MagicDraw plugin descriptor
│   │       └── neo4j-connection.properties ← Default connection settings
│   └── test/java/com/uaf/neo4j/plugin/
│       ├── model/
│       │   ├── UAFElementDTOTest.java
│       │   ├── UAFRelationshipDTOTest.java
│       │   └── UAFStereotypeRegistryTest.java
│       └── neo4j/
│           └── Neo4jCypherBuilderTest.java
├── install-msosa-jars.ps1                  ← One-time script to install SDK jars into local Maven repo
└── pom.xml                                 ← Maven build (fat jar + shaded Neo4j driver + JGraphX)
```

---

## Quick Start

### Step 1 — Install the Plugin in MSOSA

**Option A — Plugin Manager:**
1. In MSOSA: **Help → Resource/Plugin Manager → Install Plugin from File**
2. Select `target/uaf-neo4j-plugin-0.5.0-plugin.zip`
3. Restart MSOSA when prompted

**Option B — Manual:**

Unzip `target/uaf-neo4j-plugin-0.5.0-plugin.zip` into `<MSOSA_HOME>/plugins/`:

```
<MSOSA_HOME>/plugins/uaf-neo4j-plugin/
    uaf-neo4j-plugin-0.5.0.jar
    plugin.xml
    neo4j-connection.properties
```

Restart MSOSA. The plugin appears under **Tools → UAF Neo4j Export**.

---

### Step 2 — Start Neo4j

```powershell
cd docker-compose
docker compose up -d
```

Then initialise the UAF metamodel schema once:

```powershell
cypher-shell -u neo4j -p Password123 -f ../uaf-neo4j-plugin/cypher/init_uaf_graph.cypher
```

---

### Step 3 — Export

1. Open your UAF 1.2 project in MSOSA
2. **Tools → UAF Neo4j Export → Export to Neo4j…**
3. The **Export Configuration** dialog opens:
   - **Left panel** — select which model packages to export (element counts load in background)
   - **Connection tab** — verify or update the Neo4j connection; use **Test Connection** to confirm
   - **Options tab** — toggle tagged values, relationships, and `INSTANCE_OF` metamodel links
4. Click **Export** — runs in a background thread, MSOSA stays responsive
5. The **Export Summary** dialog shows node/relationship/error counts on completion

---

### Step 4 — Browse the Graph

After export, click **Browse Graph…** in the summary dialog, or go to
**Tools → UAF Neo4j Export → Browse Graph…** at any time.

The **Graph Inspector** provides two ways to explore:

| Tab | What it shows |
|---|---|
| **Properties** | Core node properties (id, name, stereotype, domain, package, documentation) |
| **Graph** | JGraphX 1-hop neighbourhood — nodes colour-coded by UAF domain, gold border on the selected node |

- **Search** filters the node table by name, stereotype, or package in real time
- **Domain** dropdown narrows the table to a single UAF domain
- **Locate in MSOSA Model** navigates the MSOSA containment browser to the element
- Clicking a node in the Graph tab syncs the table selection and switches to Properties

---

## Connection Configuration

Connection settings are stored in:
```
<MSOSA_HOME>/plugins/uaf-neo4j-plugin/neo4j-connection.properties
```

Default values:
```properties
neo4j.uri=bolt://localhost:7687
neo4j.user=neo4j
neo4j.password=Password123
neo4j.database=neo4j
neo4j.batch.size=500
```

Settings can be edited at runtime on the **Connection** tab of the Export Configuration dialog.
Changes are saved immediately and take effect without restarting MSOSA.

---

## Node Structure in Neo4j

Each exported UAF element carries **only its stereotype label** (e.g. `:Capability`,
`:OperationalPerformer`). There is no generic `:UAFElement` label. To match all exported
elements regardless of stereotype, filter on the `stereotype` property:

```cypher
MATCH (n) WHERE n.stereotype IS NOT NULL ...
```

### Core Properties

| Property | Description |
|---|---|
| `id` | MagicDraw element ID — stable MERGE key |
| `name` | Element name from model |
| `qualifiedName` | Fully qualified model path |
| `stereotype` | Applied UAF stereotype name (e.g. `"Capability"`) |
| `domain` | UAF domain (`STRATEGIC` / `OPERATIONAL` / `RESOURCE` / `SERVICE` / `PERSONNEL` / `ACQUISITION` / `SECURITY`) |
| `packageName` | Package hierarchy |
| `diagramId` / `diagramName` | Diagrams that include this element |
| `documentation` | Model comments / notes |
| `modelFile` | Last-exporting project name (convenience — authoritative provenance is via `[:DEFINES]`) |

### Tagged Value Properties (`tv_*`)

UAF stereotypes can define extra attributes on a model element beyond the standard UML
properties — these are called **tagged values**. Examples: `capabilityLevel` on a
`Capability`, `nationality` on an `Organization`, `dataType` on a `ResourceInformation`.

During export, every tagged value found on a model element is written to Neo4j as a
node property with a `tv_` prefix:

| Tagged value in MSOSA | Property in Neo4j |
|---|---|
| `capabilityLevel = 3` | `tv_capabilityLevel = 3` |
| `nationality = "UK"` | `tv_nationality = "UK"` |
| `data-type = "String"` | `tv_data_type = "String"` |

**Why the `tv_` prefix?**

- Prevents collisions with core properties — a tagged value named `name` or `domain`
  cannot overwrite the element's built-in properties.
- Groups all tagged values under one predictable namespace, so they can be retrieved
  or filtered as a set without knowing the individual attribute names in advance.

Special characters in the original tag name (hyphens, dots, spaces) are replaced with
`_` during export.

**Querying tagged values:**

```cypher
// Find all Capabilities with a specific level
MATCH (n:Capability) WHERE n.tv_capabilityLevel >= 3
RETURN n.name, n.tv_capabilityLevel ORDER BY n.tv_capabilityLevel;

// List all tagged value attributes on a ResourceInformation element
MATCH (ri:ResourceInformation {name: 'MyDataItem'})
RETURN [k IN keys(ri) WHERE k STARTS WITH 'tv_' | {attr: k, value: ri[k]}] AS dataModel;

// Find elements that have a specific tagged value key (any value)
MATCH (n) WHERE n.tv_nationality IS NOT NULL
RETURN n.name, n.stereotype, n.tv_nationality ORDER BY n.tv_nationality;
```

Tagged values on **relationships** are also exported with the same `tv_` prefix and can
be queried the same way:

```cypher
MATCH (src)-[r:PERFORMS]->(act:OperationalActivity)
WHERE r.tv_frequency IS NOT NULL
RETURN src.name, act.name, r.tv_frequency;
```

### Metamodel and Provenance Links

```cypher
// Each element is owned by the project that exported it
(:SystemModel {id, name})-[:DEFINES]->(:Capability)

// Each element links to the UAF metamodel
(:Capability)-[:INSTANCE_OF]->(:Stereotype)-[:BELONGS_TO]->(:Domain)
```

When two MSOSA projects share elements via project usage (same MagicDraw element IDs),
those elements merge into a single Neo4j node that accumulates `[:DEFINES]` relationships
from each project that exported it — so cross-model ownership is always queryable without
losing provenance.

---

## Relationship Structure

### SystemModel Relationships

| Relationship | Source | Target | Description |
|---|---|---|---|
| `DEFINES` | `:SystemModel` | stereotype node (e.g. `:Capability`) | Element was traversed during export of this project |

### UAF Instance Relationships

Relationships carry: `id`, `uafType` (UML metaclass), `name`, `domain`, plus any `tv_*` tagged values.

**Supported types (28):**

`REALISES` · `TRACES_TO` · `ASSIGNED_TO` · `SATISFIES` · `REFINES` · `INFLUENCES` ·
`DEPENDS_ON` · `COMPOSED_OF` · `SPECIALISES` · `EXHIBITS` · `CONTRIBUTES_TO` ·
`EXPOSES` · `PROVIDES` · `PERFORMS` · `CONNECTED_TO` · `FLOWS_TO` · `TRIGGERS` ·
`PRECEDES` · `ENABLES` · `SUPPORTS` · `IMPLEMENTS` · `ALLOCATED_TO` · `INSTANCE_OF` ·
`CONTAINED_IN` · `ASSOCIATED_WITH` · `DEPENDENCY` · `GENERALIZATION` ·
`INFORMATION_FLOW` · `CONTROL_FLOW`

### Metamodel Relationships

| Relationship | Source | Target |
|---|---|---|
| `INSTANCE_OF` | stereotype node (e.g. `:Capability`) | `:Stereotype` |
| `BELONGS_TO` | `:Stereotype` | `:Domain` |

---

## Re-export Behaviour (Idempotency)

Exports are idempotent — re-running on the same or updated project:
- **Updates** existing nodes (name, documentation, tagged values, diagrams)
- **Adds** new elements and relationships
- **Does not delete** elements removed from the model (run a cleanup Cypher if needed)
- **Accumulates provenance** — `[:DEFINES]` relationships are MERGED, so re-exporting a project never removes another project's claim on a shared element

---

## Example Queries

See `cypher/query-cookbook.cypher` for a full set. Quick start:

```cypher
// All exported elements by stereotype count
MATCH (n) WHERE n.stereotype IS NOT NULL
RETURN n.stereotype, count(*) AS total ORDER BY total DESC;

// Performers and their activities
MATCH (p:OperationalPerformer)-[:PERFORMS]->(a:OperationalActivity)
RETURN p.name, a.name;

// Cross-domain traceability: Strategic → Resource
MATCH path = (st {domain: 'STRATEGIC'})
             -[:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|IMPLEMENTS*1..6]->
             (rs {domain: 'RESOURCE'})
WHERE st.stereotype IS NOT NULL AND rs.stereotype IS NOT NULL
RETURN st.name, rs.name, rs.stereotype, length(path) AS hops
ORDER BY hops LIMIT 50;

// All elements owned by a specific system model
MATCH (m:SystemModel {name: 'MyProject'})-[:DEFINES]->(n)
WHERE n.stereotype IS NOT NULL
RETURN n.name, n.stereotype, n.domain ORDER BY n.domain, n.stereotype;

// Elements shared across two or more models
MATCH (m:SystemModel)-[:DEFINES]->(n)
WHERE n.stereotype IS NOT NULL
WITH n, collect(m.name) AS models, count(m) AS modelCount
WHERE modelCount > 1
RETURN n.name, n.stereotype, models ORDER BY modelCount DESC;

// 1-hop neighbourhood of a named element (mirrors the Graph Inspector view)
MATCH (n:Capability {name: 'MyCapability'})-[r]-(m)
WHERE m.stereotype IS NOT NULL
RETURN n, r, m;
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| "No UAF elements found" | No UAF stereotype applied | Ensure UAF 1.2 profile is loaded; elements have UAF stereotypes |
| Connection refused | Neo4j container not running | `docker compose up -d`; check port 7687 |
| Authentication failed | Wrong credentials | Update on the Connection tab of the export dialog |
| INSTANCE_OF links missing | Stereotype nodes not in DB | Run `cypher/init_uaf_graph.cypher` |
| Slow export | Large model + small batch | Increase `neo4j.batch.size` to 500–1000 on the Connection tab |
| `ClassNotFoundException` on startup | SDK jars not in local Maven repo | Re-run `.\install-msosa-jars.ps1` from `uaf-neo4j-plugin/` |
| Stereotype skipped silently | Name mismatch in `UAFStereotypeRegistry` | Verify name via MSOSA scripting console — see CLAUDE.md |
| Graph tab shows placeholder after selection | Node has no UAF relationships in Neo4j | Export relationships (Options tab) or check that init Cypher was run |
| Graph Inspector shows 0 nodes | Plugin JAR not rebuilt after a code change, or database empty | Run `MATCH (n) WHERE n.stereotype IS NOT NULL RETURN count(n)` in Neo4j Browser; if > 0, redeploy the plugin JAR |
