# UAF 1.2 → Neo4j Knowledge Graph Exporter
## Catia Magic MSOSA 2022x Hotfix 1 Plugin

---

## Overview

This plugin exports UAF 1.2 architectural elements and relationships from a
Catia Magic MSOSA 2022x project into a Neo4j graph database running in Docker.

Exported instance nodes are automatically linked via `:INSTANCE_OF` relationships
to the pre-existing UAF domain meta-model stereotype nodes already in your graph,
creating a live, queryable knowledge graph spanning both the meta-model and
instance-level architecture data.

```
MSOSA Project
    │
    │  [UAFModelTraverser]
    ▼
UAFElement / UAFRelationship DTOs
    │
    │  [Neo4jCypherBuilder → MERGE Cypher]
    ▼
Neo4j (Docker)
    ├── :UAFElement:OperationalPerformer  ──[:INSTANCE_OF]──► :Stereotype {name:'OperationalPerformer'}
    ├── :UAFElement:SystemFunction        ──[:INSTANCE_OF]──► :Stereotype {name:'SystemFunction'}
    ├── :UAFElement:Capability            ──[:INSTANCE_OF]──► :Stereotype {name:'Capability'}
    └── [:PERFORMS] [:TRACES_TO] [:SATISFIES] ...
```

---

## Requirements

| Component | Version |
|---|---|
| Catia Magic MSOSA | 2022x Hotfix 1 |
| Java (plugin compile) | JDK 11+ |
| Neo4j | 4.4.x or 5.x |
| Docker | 20.10+ |
| Maven | 3.8+ (build only) |

---

## Project Structure

```
uaf-neo4j-plugin/
├── plugin.xml                          ← MagicDraw plugin descriptor
├── pom.xml                             ← Maven build
├── config/
│   └── neo4j-connection.properties     ← Connection settings (edit this)
├── docker/
│   ├── docker-compose.yml              ← Neo4j Docker Compose
│   └── neo4j/init/
│       └── init_uaf_graph.cypher       ← DB + schema initialisation
├── docs/
│   └── query-cookbook.cypher           ← Example Cypher queries
└── src/main/java/com/uaf/neo4j/exporter/
    ├── UAFNeo4jPlugin.java             ← Plugin entry point
    ├── actions/
    │   └── UAFExporterActionsConfigurator.java
    ├── model/
    │   ├── UAFElementDTO.java
    │   └── UAFRelationshipDTO.java
    ├── neo4j/
    │   ├── Neo4jCypherBuilder.java
    │   └── Neo4jExportService.java
    ├── uaf/
    │   ├── UAFStereotypeRegistry.java
    │   └── UAFModelTraverser.java
    └── ui/
        ├── ConnectionDialog.java
        └── ExportSummaryDialog.java
```

---

## Step 1 — Start Neo4j in Docker

```bash
cd docker
docker-compose up -d
```

Wait ~60 seconds for Neo4j to start, then verify:
- Browser: http://localhost:7474  (login: neo4j / uaf_password_change_me)
- Bolt:    bolt://localhost:7687

**Change the password** in `docker-compose.yml` before using in any shared environment.

### Initialise the UAF database

```bash
docker exec -it neo4j-docker cypher-shell \
  -u neo4j -p uaf_password_change_me \
  -f /var/lib/neo4j/import/init_uaf_graph.cypher
```

Or paste the contents of `docker/neo4j/init/init_uaf_graph.cypher` into
Neo4j Browser.

---

## Step 2 — Register MSOSA SDK jars in Maven

The MagicDraw/MSOSA SDK jars are not on Maven Central. Install them from
your local MSOSA installation:

```bash
MSOSA_HOME="/path/to/MagicDraw"  # adjust

mvn install:install-file \
  -Dfile="$MSOSA_HOME/lib/md.jar" \
  -DgroupId=com.nomagic.magicdraw \
  -DartifactId=md \
  -Dversion=2022x-hf1 -Dpackaging=jar

mvn install:install-file \
  -Dfile="$MSOSA_HOME/lib/md_api.jar" \
  -DgroupId=com.nomagic.magicdraw \
  -DartifactId=md_api \
  -Dversion=2022x-hf1 -Dpackaging=jar

mvn install:install-file \
  -Dfile="$MSOSA_HOME/lib/uml2.jar" \
  -DgroupId=com.nomagic.magicdraw \
  -DartifactId=uml2 \
  -Dversion=2022x-hf1 -Dpackaging=jar
```

---

## Step 3 — Build the Plugin

```bash
mvn clean package
```

Output: `target/uaf-neo4j-exporter-plugin-1.0.0.zip`

---

## Step 4 — Install the Plugin in MSOSA

1. In MSOSA: **Help → Resource/Plugin Manager → Install Plugin from File**
2. Select `target/uaf-neo4j-exporter-plugin-1.0.0.zip`
3. Restart MSOSA when prompted

Alternatively, unzip manually into `<MSOSA_HOME>/plugins/com.uaf.neo4j.exporter/`

---

## Step 5 — Configure the Connection

Edit `<MSOSA_HOME>/plugins/com.uaf.neo4j.exporter/config/neo4j-connection.properties`:

```properties
neo4j.uri=bolt://localhost:7687
neo4j.username=neo4j
neo4j.password=uaf_password_change_me
neo4j.database=uaf
export.batchSize=200
```

Or configure at runtime: **Tools → UAF Neo4j Export → Configure Neo4j Connection**

---

## Step 6 — Export

1. Open your UAF 1.2 project in MSOSA
2. **Tools → UAF Neo4j Export → Export Active Project to Neo4j**
3. Confirm connection settings → click **OK**
4. Progress is shown in a MagicDraw progress dialog
5. An export summary appears on completion

---

## Node Structure in Neo4j

Each exported UAF element becomes a node with:

### Labels
- `UAFElement` — universal label for all exported instances
- Stereotype label, e.g. `OperationalPerformer`, `SystemFunction`, `Capability`
- Domain label, e.g. `OPERATIONAL`, `SYSTEMS`, `SERVICES`

### Core Properties
| Property | Description |
|---|---|
| `externalId` | MagicDraw element ID (stable MERGE key) |
| `name` | Element name from model |
| `uafStereotype` | Applied UAF stereotype name |
| `uafDomain` | UAF domain (OPERATIONAL / SYSTEMS / etc.) |
| `architectureLayer` | CONCEPTUAL / LOGICAL / PHYSICAL |
| `qualifiedName` | Fully qualified model path |
| `packagePath` | Package hierarchy |
| `umlMetaClass` | UML base metaclass |
| `documentation` | Model comments / notes |
| `isAbstract` | Boolean |
| `isLeaf` | Boolean |
| `containingDiagrams` | Diagrams that show this element |

### Tagged Value Properties
All UAF tagged values are stored as `tv.<stereotypeName>.<tagName>` properties,
e.g. `tv.OperationalPerformer_nationality` or `tv.Capability_capabilityLevel`.

### Provenance Properties
| Property | Description |
|---|---|
| `exportedAt` | ISO-8601 export timestamp |
| `projectName` | Source MSOSA project name |
| `pluginVersion` | Plugin version that wrote this node |
| `createdAt` | Timestamp of first MERGE (never updated) |

### Meta-model Link
```cypher
(n:UAFElement)-[:INSTANCE_OF]->(s:Stereotype)
```
Links each instance to its pre-existing meta-model stereotype node.

---

## Relationship Structure

Relationships carry:
- `externalId` — MagicDraw relationship ID
- `uafStereotype` — Applied UAF stereotype
- `uafDomain` — UAF domain
- `umlMetaClass` — UML relationship metaclass
- `documentation` — Notes
- `exportedAt`, `projectName`, `pluginVersion` — Provenance
- `tv.*` — Tagged values on the relationship

---

## Supported UAF 1.2 Relationship Types (Neo4j)

`PERFORMS` · `REALIZES` · `SUPPORTS` · `DEPENDS_ON` · `ASSIGNED_TO` ·
`TRACES_TO` · `CONNECTED_TO` · `EXPOSES` · `USES` · `DECOMPOSES` ·
`REFINES` · `SATISFIES` · `FLOWS_TO` · `GENERALIZES` · `REALIZES_CAPABILITY` ·
`LOCATED_AT` · `GOVERNED_BY` · `IMPLEMENTS` · `PROVIDES` · `CONSUMES` ·
`OCCURS_IN` · `OCCURS_BEFORE` · `OCCURS_AFTER` · `COMPOSED_OF` · `INFLUENCES` ·
`MITIGATES` · `EXHIBITS_CAPABILITY` · `AUTHORIZED_TO` · `RESPONSIBLE_FOR`

---

## Example Queries

See `docs/query-cookbook.cypher` for a full set of queries. Quick start:

```cypher
// Everything exported
MATCH (n:UAFElement)
RETURN n.uafStereotype, count(*) ORDER BY count(*) DESC;

// Performers and their activities
MATCH (p:OperationalPerformer)-[:PERFORMS]->(a:OperationalActivity)
RETURN p.name, a.name;

// Full cross-domain traceability
MATCH (op:OperationalPerformer)-[:PERFORMS]->(oa:OperationalActivity)
OPTIONAL MATCH (oa)<-[:TRACES_TO]-(sf:SystemFunction)
OPTIONAL MATCH (sf)<-[:TRACES_TO]-(svc:Service)
RETURN op.name, oa.name, sf.name, svc.name;
```

---

## Re-export Behaviour (Idempotency)

Exports are idempotent. Re-running the export on the same or updated project:
- **Updates** existing nodes (name, documentation, tagged values, diagrams)
- **Preserves** `createdAt` timestamp
- **Adds** new elements and relationships
- **Does not delete** elements removed from the model (run a cleanup query if needed)

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| "No UAF elements found" | No UAF stereotype applied | Ensure UAF 1.2 profile applied; elements have UAF stereotypes |
| Connection refused | Neo4j container not running | `docker-compose up -d`; check port 7687 is exposed |
| Authentication failed | Wrong credentials | Check `neo4j-connection.properties` |
| Slow export | Large model + small batch | Increase `export.batchSize` to 500 |
| INSTANCE_OF links missing | Stereotype nodes not in DB | Run `init_uaf_graph.cypher`; verify your meta-model load |
| ClassNotFoundException | SDK jar not installed | Re-run `mvn install:install-file` for MSOSA jars |
