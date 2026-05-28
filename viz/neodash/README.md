# UAF starter dashboard for NeoDash

NeoDash translation of the eight search phrases originally drafted as a Neo4j Bloom perspective in [`bloom/uaf-perspective.json`](../../bloom/uaf-perspective.json). Bloom itself was abandoned for this repo (the Docker plugin needs a non-public licence; Neo4j Desktop's free Explore only works against Desktop-managed local instances, not the Dockerised Community DB). NeoDash fills the slot — Apache-2.0, Community-edition compatible.

The dashboard organises the eight phrases across three pages:

| Page | Reports | Parameters consumed |
|---|---|---|
| **Strategic** | All capabilities · Capability traceability · Resources realising capability · Capability gap | `$neodash_capability` |
| **Operational** | Activities & performers · Decommissioning impact · AS-IS / TO-BE filter | `$neodash_element`, `$neodash_domain` |
| **Security** | Dominance chain from enclave | `$neodash_enclave` |

## Quick start

Bring up Neo4j + NeoDash (Fuseki optional):

```powershell
docker compose -f docker-compose/docker-compose.yml `
               -f docker-compose/docker-compose.neodash.yml up -d
```

Open <http://localhost:5005/>, connect with `neo4j://localhost:7687`, the `neo4j` user, your `.env` password, and the `neo4j` database.

## Loading `uaf-starter.json`

NeoDash dashboard JSON formats shift across versions. Two paths — the second is the reliable one if your NeoDash build rejects the file:

### Path A — try the import

1. Top-right header → **folder/upload icon** → **Load dashboard from file**.
2. Pick `viz/neodash/uaf-starter.json`.
3. If everything imports, you'll see three pages and twelve reports. Save it back to Neo4j (header → **save icon** → **Save to Neo4j**) so it survives restarts.

If the import rejects any field (most common: the `select` report's `settings.type` differs slightly between NeoDash builds), fall through to Path B and rebuild — it's quick and yields a clean export.

### Path B — build it in the UI (authoritative)

For each report below: in NeoDash, click the **+** to add a card, set the **Title**, **Type**, **Query**, and (for Select cards) the **Parameter Name** / **Selection Type** as listed. Save the dashboard to Neo4j when done, then **export to file** and overwrite `uaf-starter.json` so the JSON in this repo is the canonical artefact going forward.

#### Page 1 — Strategic

1. **Show all capabilities** — Type: `Graph` — Query: `MATCH (c:Capability) RETURN c LIMIT 200`
2. **Pick a capability** — Type: `Parameter Select` — Selection Type: `Node Property` — Node Label: `Capability` — Property: `name` — Parameter Name: `capability`
3. **Capability traceability for $neodash_capability** — Type: `Graph` —
   ```cypher
   MATCH (c:Capability {name: $neodash_capability})
         <-[r:REALISES|REALISED_BY|TRACES_TO|EXHIBITS|IMPLEMENTS|PERFORMS*1..4]-(n)
   RETURN c, r, n
   ```
4. **Performers realising $neodash_capability** — Type: `Graph` —
   ```cypher
   MATCH (c:Capability {name: $neodash_capability})
         <-[r:REALISES|EXHIBITS*1..3]-(rp)
   RETURN c, r, rp
   ```
5. **Capability gap — capabilities with no realising performer** — Type: `Table` —
   ```cypher
   MATCH (c:Capability)
   WHERE NOT EXISTS { (c)<-[:REALISES|EXHIBITS]-() }
   RETURN c.name AS Capability, c.qualifiedName AS QualifiedName
   ORDER BY Capability
   ```

#### Page 2 — Operational

1. **Operational activities and their performers** — Type: `Graph` —
   ```cypher
   MATCH (p)-[r:PERFORMS]->(a:OperationalActivity) RETURN p, r, a
   ```
2. **Pick an element by name** — Type: `Parameter Select` — Selection Type: `Free Text` — Parameter Name: `element`. The selector is `Free Text` rather than `Node Property` because the schema labels every node by its stereotype (no `:UAFElement` umbrella label exists). A `Node Property` selector forces a single `entityType`, which would defeat the cross-stereotype intent. Paste an exact `name` value (case- and whitespace-sensitive) — grab one from the "Operational activities and their performers" graph above, or run `MATCH (n) WHERE n.stereotype IS NOT NULL RETURN n.name LIMIT 50` in Neo4j Browser. In typical exports inbound traceability lives at the `:Capability` level, so the downstream "Decommissioning impact" graph is most useful when you paste a capability name; operational activities often have only structural edges and will render empty.
3. **Decommissioning impact for $neodash_element** — Type: `Graph` —
   ```cypher
   MATCH (e {name: $neodash_element})
         <-[r:REALISES|EXHIBITS|IMPLEMENTS|PERFORMS|DEPENDENCY
            |RESOURCEEXCHANGE|MESSAGE_FLOW|ITEMFLOW*1..5]-(n)
   WHERE e.stereotype IS NOT NULL
   RETURN e, r, n
   ```
   The relationship filter covers every inbound edge type the Java exporter emits that represents a real dependency (something would break if `e` were retired). `DEFINES` and `HAS_ATTRIBUTE` are excluded — `DEFINES` is the SystemModel→element ownership edge (every element has one, so it floods the view) and `HAS_ATTRIBUTE` is structural rather than a dependency. Earlier drafts used `TRACES_TO` and `DEPENDS_ON`; neither name is actually emitted (the dependency edge is `DEPENDENCY`).
4. **Pick a domain** — Type: `Parameter Select` — Selection Type: `Free Text` — Parameter Name: `domain` — Default value: `Operational`
5. **AS-IS vs TO-BE elements in $neodash_domain** — Type: `Graph` —
   ```cypher
   MATCH (n)-[r:REALISES|EXHIBITS|IMPLEMENTS|PERFORMS|DEPENDENCY
             |RESOURCEEXCHANGE|MESSAGE_FLOW|ITEMFLOW]-(m)
   WHERE n.stereotype IS NOT NULL
     AND n.qualifiedName CONTAINS $neodash_domain
     AND (n.qualifiedName CONTAINS 'AS-IS' OR n.qualifiedName CONTAINS 'TO-BE')
   RETURN n, r, m LIMIT 200
   ```
   Earlier draft was `RETURN n` only, which renders as a disconnected starfield when the domain contains hundreds of AS-IS/TO-BE elements. The current shape returns only the connected subgraph (AS-IS/TO-BE elements participating in real traceability edges) plus their immediate neighbours, so transition stories — TO-BE performer realising the same capability as an AS-IS performer, AS-IS service being replaced by a TO-BE service via flow rewiring — are visible. Isolated AS-IS/TO-BE elements are intentionally dropped from this view; if gap analysis matters, surface them in a separate Table card with `MATCH (n) WHERE n.stereotype IS NOT NULL AND n.qualifiedName CONTAINS $neodash_domain AND (n.qualifiedName CONTAINS 'AS-IS' OR n.qualifiedName CONTAINS 'TO-BE') AND NOT EXISTS { (n)-[:REALISES|EXHIBITS|IMPLEMENTS|PERFORMS|DEPENDENCY|RESOURCEEXCHANGE|MESSAGE_FLOW|ITEMFLOW]-() } RETURN n.qualifiedName ORDER BY n.qualifiedName`.

#### Page 3 — Security

1. **Pick a security enclave** — Type: `Parameter Select` — Selection Type: `Node Property` — Node Label: `SecurityEnclave` — Property: `name` — Parameter Name: `enclave`
2. **Security dominance chain from $neodash_enclave** — Type: `Graph` —
   ```cypher
   MATCH (e:SecurityEnclave {name: $neodash_enclave})-[r:DOMINATES*1..]->(d)
   RETURN e, r, d
   ```

## Why these queries drop `:OperationalPerformer` and `:ResourcePerformer`

The Bloom perspective JSON treats `OperationalPerformer` and `ResourcePerformer` as if they were applied labels. In UAF 1.2 they are **abstract** stereotypes — concrete real-world models almost never apply them directly. Modellers stereotype with the concrete subtypes: `OperationalRole`, `Organization`, `Post`, `OperationalInformationRole`, `ResourceRole`, `ResourceArtifact`, `HardwareElement`, `SoftwareElement`, and so on. Querying for `(:OperationalPerformer)` therefore returns zero rows against any real export.

The three Performer-related queries above drop the source-label constraint. The cost is breadth — the result includes whatever performs the activity / realises the capability, with the actual labels visible in NeoDash's legend. If you want to tighten a query to just the operationally-meaningful concrete labels in *your* export, run this diagnostic in a Table card to see what's actually there:

```cypher
MATCH (p)-[r:PERFORMS]->(a:OperationalActivity)
RETURN labels(p) AS source_labels, count(*) AS edges
ORDER BY edges DESC
```

Then narrow the dashboard query with an explicit `WHERE p:OperationalRole OR p:Organization OR ...` clause. The unconstrained form is the safer default for a starter dashboard — it works against any UAF export, whatever the modeller chose to stereotype.

## Mapping to the perspective JSON

The Bloom perspective encodes three layers of intent. They translate to NeoDash differently:

| Bloom intent | NeoDash mechanism |
|---|---|
| **30 categories** (label-keyed colours/sizes/captions) | Per-Graph-card **Advanced Settings → Node Color Scheme** (override colour per label). No global perspective in NeoDash. |
| **Hidden labels** (Stereotype, Domain, ModellingLanguage, SystemModel) | None needed for these eight phrases — every query targets stereotyped instance nodes via `n.stereotype IS NOT NULL` or specific labels, which excludes the metamodel anchors. |
| **Hidden relationship types** (`INSTANCE_OF`, `BELONGS_TO`) | Same — none of the eight queries traverse those edges. |
| **25 relationship types** with colours | Per-Graph-card **Advanced Settings → Relationship Color Scheme**. Worth one pass once the report list is stable. |
| **8 search phrases** | Twelve report cards (5 Select + 7 Graph + 1 Table) across three pages — encoded above. |

The UAF domain colour scheme (Strategic blue, Operational green, Resource orange, Service purple, Personnel yellow, Acquisition red, Security dark-red, Shared/ERD grey) is in [`bloom/uaf-perspective.json`](../../bloom/uaf-perspective.json) `categories[*].color`. Copy those hex codes into each Graph card's node-color settings when you want full visual parity.

## Where this fits

- `bloom/uaf-perspective.json` — original design intent, kept as the reference document for categories/colours/phrases.
- `viz/neodash/uaf-starter.json` — the working NeoDash dashboard (this directory).
- `docker-compose/docker-compose.neodash.yml` — the overlay that adds the NeoDash container.
- See [`ontology/NEXT-STEPS.md`](../../ontology/NEXT-STEPS.md) A-Box-visualisation entry for the decision history.
