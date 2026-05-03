# UAF 1.2 → Neo4j Knowledge Graph Exporter
## Catia Magic MSOSA 2022x Hotfix 2 Plugin

---

## Overview

This plugin exports UAF 1.2 architectural elements and relationships from a
Catia Magic MSOSA 2022x Hotfix 2 project into a Neo4j graph database running in Docker.

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
    ├── :UAFElement:Capability            ──[:INSTANCE_OF]──► :Stereotype {name:'Capability'}
    ├── :UAFElement:OperationalPerformer  ──[:INSTANCE_OF]──► :Stereotype {name:'OperationalPerformer'}
    ├── :UAFElement:SystemFunction        ──[:INSTANCE_OF]──► :Stereotype {name:'SystemFunction'}
    └── [:PERFORMS] [:TRACES_TO] [:SATISFIES] ...
```

---

## Requirements

| Component | Version |
|---|---|
| Catia Magic MSOSA | 2022x Hotfix 2 |
| Java (plugin compile) | JDK 11+ |
| Neo4j | 4.4.x or 5.x |
| Docker | 20.10+ |
| Maven | 3.8+ |

---

## Project Structure

```
uaf-neo4j-plugin/
├── msosa-api/                              ← MSOSA SDK jars (checked in)
│   ├── md.jar                              ← MagicDraw core API
│   ├── md_api.jar                          ← MagicDraw public API interfaces
│   └── com.nomagic.magicdraw.uml2-*.jar   ← UML2 / StereotypesHelper
├── plugin.xml                              ← MagicDraw plugin descriptor
├── pom.xml                                 ← Maven build
├── config/
│   └── neo4j-connection.properties         ← Connection settings (edit this)
├── cypher/
│   ├── init_uaf_graph.cypher               ← DB schema + metamodel initialisation (run once)
│   └── query-cookbook.cypher               ← Example Cypher queries
├── docker/
│   └── docker-compose.yml                  ← Neo4j Docker Compose
└── src/main/java/com/uaf/neo4j/plugin/
    ├── UAFNeo4jPlugin.java                 ← Plugin entry point + config lifecycle
    ├── UAFExporterActionsConfigurator.java ← Injects Tools → UAF Neo4j Export menu
    ├── ExportAction.java                   ← SwingWorker pipeline driver
    ├── ConfigureAction.java
    ├── AboutAction.java
    ├── model/
    │   ├── UAFStereotypeRegistry.java      ← Single source of truth: stereotype → domain/layer
    │   ├── UAFModelTraverser.java          ← Walks MSOSA project, extracts DTOs
    │   ├── UAFElementDTO.java              ← Immutable node DTO (builder pattern)
    │   └── UAFRelationshipDTO.java         ← Immutable edge DTO (28 type constants)
    ├── neo4j/
    │   ├── Neo4jCypherBuilder.java         ← Parameterised MERGE Cypher (no interpolation)
    │   └── Neo4jExportService.java         ← Bolt driver lifecycle + batched writes
    └── ui/
        ├── ConnectionDialog.java           ← Edit URI / credentials / batch size
        └── ExportSummaryDialog.java        ← Post-export counts + error list
```

---

## Step 1 — Start Neo4j in Docker

```powershell
cd docker
docker compose up -d
```

Wait ~30 seconds, then verify:
- Browser: http://localhost:7474  (login: `neo4j` / `Password123`)
- Bolt:    `bolt://localhost:7687`

### Initialise the UAF database (run once)

```powershell
cypher-shell -u neo4j -p Password123 -f cypher/init_uaf_graph.cypher
```

Or paste the contents of `cypher/init_uaf_graph.cypher` into the Neo4j Browser.

This creates constraints, full-text indexes, and the pre-existing metamodel nodes
(`:Stereotype`, `:Domain`, `:ArchitectureLayer`) that exported instances link back to.

---

## Step 2 — Register MSOSA SDK jars in Maven

The MSOSA SDK jars are checked into `msosa-api/` in this directory. Run the
following once from the `uaf-neo4j-plugin/` directory to install them into your
local Maven repository:

```powershell
mvn install:install-file -Dfile="msosa-api/md.jar" `
    -DgroupId=com.nomagic.magicdraw -DartifactId=md `
    -Dversion=2022x-hf2 -Dpackaging=jar

mvn install:install-file -Dfile="msosa-api/md_api.jar" `
    -DgroupId=com.nomagic.magicdraw -DartifactId=md-api `
    -Dversion=2022x-hf2 -Dpackaging=jar

mvn install:install-file `
    -Dfile="msosa-api/com.nomagic.magicdraw.uml2-2022.2.0-105-acd52bbc.jar" `
    -DgroupId=com.nomagic.magicdraw -DartifactId=uml2 `
    -Dversion=2022x-hf2 -Dpackaging=jar
```

These jars are `provided` scope — they are never bundled into the fat jar.

---

## Step 3 — Build the Plugin

```powershell
mvn clean package
```

Outputs (in `target/`):

| File | Purpose |
|---|---|
| `uaf-neo4j-plugin-1.0.0.jar` | Fat jar — Neo4j driver bundled and relocated |
| `uaf-neo4j-plugin-1.0.0-plugin.zip` | Drop into `<MSOSA_HOME>/plugins/` |

The Neo4j driver is shaded into `com.uaf.shaded.neo4j.driver` to avoid classpath
collisions with MagicDraw's own bundled libraries.

---

## Step 4 — Install the Plugin in MSOSA

**Option A — Plugin Manager:**
1. In MSOSA: **Help → Resource/Plugin Manager → Install Plugin from File**
2. Select `target/uaf-neo4j-plugin-1.0.0-plugin.zip`
3. Restart MSOSA when prompted

**Option B — Manual:**

Unzip `target/uaf-neo4j-plugin-1.0.0-plugin.zip` into `<MSOSA_HOME>/plugins/`:

```
<MSOSA_HOME>/plugins/uaf-neo4j-plugin/
    uaf-neo4j-plugin-1.0.0.jar
    plugin.xml
    neo4j-connection.properties
```

Restart MSOSA. Plugin appears under **Tools → UAF Neo4j Export**.

---

## Step 5 — Configure the Connection

Edit `<MSOSA_HOME>/plugins/uaf-neo4j-plugin/neo4j-connection.properties`:

```properties
neo4j.uri=bolt://localhost:7687
neo4j.user=neo4j
neo4j.password=Password123
neo4j.database=neo4j
neo4j.batch.size=500
```

Or configure at runtime: **Tools → UAF Neo4j Export → Configure Connection**

Changes take effect without restarting MSOSA.

---

## Step 6 — Export

1. Open your UAF 1.2 project in MSOSA
2. **Tools → UAF Neo4j Export → Export Active Project to Neo4j**
3. Confirm connection settings → click **OK**
4. Export runs in a background thread — MSOSA stays responsive
5. An export summary dialog appears on completion showing node/relationship/error counts

---

## Node Structure in Neo4j

Each exported UAF element gets **dual labels**: `:UAFElement` + its stereotype label
(e.g. `:Capability`). This lets queries target all exported elements generically or
a specific type efficiently.

### Labels

- `UAFElement` — universal label for all exported instances
- Stereotype label — e.g. `Capability`, `OperationalPerformer`, `HardwareElement`

### Core Properties

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

### Tagged Value Properties

All UAF tagged values are flattened as `tv_<tagName>` properties (special characters
replaced with `_`), e.g. `tv_nationality`, `tv_capabilityLevel`.

### Metamodel Link

```cypher
(:UAFElement)-[:INSTANCE_OF]->(:Stereotype)-[:BELONGS_TO]->(:Domain)
                                            -[:IN_LAYER]->(:ArchitectureLayer)
```

---

## Relationship Structure

Relationships carry: `id`, `uafType` (UML metaclass), `name`, `domain`, plus any
`tv_*` tagged values.

### Supported Relationship Types (28)

`REALISES` · `TRACES_TO` · `ASSIGNED_TO` · `SATISFIES` · `REFINES` · `INFLUENCES` ·
`DEPENDS_ON` · `COMPOSED_OF` · `SPECIALISES` · `EXHIBITS` · `CONTRIBUTES_TO` ·
`EXPOSES` · `PROVIDES` · `PERFORMS` · `CONNECTED_TO` · `FLOWS_TO` · `TRIGGERS` ·
`PRECEDES` · `ENABLES` · `SUPPORTS` · `IMPLEMENTS` · `ALLOCATED_TO` · `INSTANCE_OF` ·
`CONTAINED_IN` · `ASSOCIATED_WITH` · `DEPENDENCY` · `GENERALIZATION` ·
`INFORMATION_FLOW` · `CONTROL_FLOW`

---

## Re-export Behaviour (Idempotency)

Exports are idempotent — re-running on the same or updated project:
- **Updates** existing nodes (name, documentation, tagged values, diagrams)
- **Adds** new elements and relationships
- **Does not delete** elements removed from the model (run a cleanup Cypher if needed)

---

## Example Queries

See `cypher/query-cookbook.cypher` for a full set. Quick start:

```cypher
// All exported elements by stereotype count
MATCH (n:UAFElement)
RETURN n.stereotype, count(*) AS total ORDER BY total DESC;

// Performers and their activities
MATCH (p:OperationalPerformer)-[:PERFORMS]->(a:OperationalActivity)
RETURN p.name, a.name;

// Cross-domain traceability: Strategic → Physical
MATCH path = (st:UAFElement {domain:'STRATEGIC'})
             -[:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|IMPLEMENTS*1..6]->
             (ph:UAFElement {layer:'PHYSICAL'})
RETURN st.name, ph.name, ph.stereotype, length(path) AS hops
ORDER BY hops LIMIT 50;
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| "No UAF elements found" | No UAF stereotype applied | Ensure UAF 1.2 profile is loaded; elements have UAF stereotypes |
| Connection refused | Neo4j container not running | `docker compose up -d`; check port 7687 |
| Authentication failed | Wrong credentials | Check `neo4j-connection.properties` |
| INSTANCE_OF links missing | Stereotype nodes not in DB | Run `cypher/init_uaf_graph.cypher` |
| Slow export | Large model + small batch | Increase `neo4j.batch.size` to 500–1000 |
| `ClassNotFoundException` on startup | SDK jars not installed in local Maven repo | Re-run the three `mvn install:install-file` commands (jars are in `msosa-api/`) |
| Stereotype skipped silently | Name mismatch in `UAFStereotypeRegistry` | Verify name via MSOSA scripting console — see CLAUDE.md |
