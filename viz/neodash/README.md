# UAF starter dashboard for NeoDash

Self-hosted [NeoDash](https://github.com/neo4j-labs/neodash) dashboard for browsing the UAF 1.2 knowledge graph that the MSOSA exporter writes into Neo4j. Apache-2.0, Community-edition compatible — no licence file required.

NeoDash replaces the Neo4j Bloom slot in this toolbox. Bloom's Docker plugin needs a non-public licence and Desktop's free Explore only works against Desktop-managed local instances, not the Dockerised Community DB — see [`ontology/NEXT-STEPS.md`](../../ontology/NEXT-STEPS.md) A-Box-visualisation entry for the decision history.

## Pages

The dashboard has nine pages — an **Overview** orientation page plus one page per UAF 1.2 domain and a cross-cutting **Data / ERD** page.

| # | Page | What it shows | Parameter(s) |
|---|---|---|---|
| 0 | Overview | counts by stereotype, domain and relationship type; most-connected elements; a markdown page guide | — |
| 1 | Strategic | capabilities, capability traceability, performers realising a capability, capability gaps | `$neodash_capability` |
| 2 | Operational | activities & performers, decommissioning impact, AS-IS vs TO-BE per domain | `$neodash_element`, `$neodash_domain` |
| 3 | Resource | architectures, hardware/software, performer→function, resource neighbourhood | `$neodash_resource` |
| 4 | Service | service specifications & interfaces, services exposed by performers, service neighbourhood | `$neodash_service` |
| 5 | Personnel | organisations & posts, posts realising capabilities, post responsibilities | `$neodash_post` |
| 6 | Acquisition | projects & milestones, milestone dependencies, project context | `$neodash_project` |
| 7 | Security | domains/enclaves/controls, dominance chain, controls protecting assets | `$neodash_enclave` |
| 8 | Data / ERD | entities & attributes, entity-to-entity relations, attribute types | `$neodash_entity` |

## Quick start (editor mode)

```powershell
docker compose -f docker-compose/docker-compose.yml `
               -f docker-compose/docker-compose.neodash.yml up -d
```

Open <http://localhost:5005/>, connect with `neo4j://localhost:7687`, the `neo4j` user, your `.env` password, and the `neo4j` database.

## Kiosk mode

Once `uaf-starter.json` is loaded and saved into Neo4j, switch to the kiosk overlay to skip the editor chrome and auto-load the dashboard on connect:

```powershell
# tear down editor mode
docker compose -f docker-compose/docker-compose.yml `
               -f docker-compose/docker-compose.neodash.yml down

# bring up kiosk mode (Neo4j stays up)
docker compose -f docker-compose/docker-compose.yml `
               -f docker-compose/docker-compose.neodash-kiosk.yml up -d
```

The kiosk overlay pre-fills the connection form with everything except the password. To eliminate the login step entirely, see the header comment in `docker-compose/docker-compose.neodash-kiosk.yml` — only do that on a trusted, isolated host.

## Loading `uaf-starter.json`

NeoDash dashboard JSON formats shift across versions. Two paths — the second is the reliable one if your NeoDash build rejects the file.

### Path A — try the import

1. Top-right header → **folder/upload icon** → **Load dashboard from file**.
2. Pick `viz/neodash/uaf-starter.json`.
3. If everything imports, save it back to Neo4j (header → **save icon** → **Save to Neo4j**) with the name `UAF Starter` so the kiosk overlay can find it.

If the import rejects any field (most common: a Select report's `settings.type` differs slightly between NeoDash builds), fall through to Path B and rebuild — it's quick and yields a clean export.

### Path B — build it in the UI

For each report below: in NeoDash, click the **+** to add a card, set the **Title**, **Type**, **Query**, and (for Select cards) the **Parameter Name** / **Selection Type** as listed. Save the dashboard to Neo4j with the name `UAF Starter` when done, then **export to file** and overwrite `uaf-starter.json` so the JSON in this repo is the canonical artefact going forward.

The full query list is in `uaf-starter.json` — easier to copy directly from the file's `query` fields than to re-transcribe here. Use the table above to remember what each page is for.

## Node colouring

Graph cards are set to `nodeColorScheme: "neodash"` — NeoDash colours each stereotype label with a distinct hue from its own 12-colour earthy palette. With ~80 stereotypes the palette recycles, but visual differentiation within a single graph card (which rarely spans more than 10–15 labels) is clear. Hover any node for its full property set.

To swap the palette, edit `nodeColorScheme` on each Graph card (or via **Advanced Settings → Node Color Scheme**). Other valid values include `nivo`, `category10`, `set1`, `set2`, `set3`, `paired`, `pastel1`, `pastel2`, `dark2`, `accent` (categorical D3 schemes shipped with NeoDash).

The UAF 1.2 stereotype → domain mapping lives in `msosa-model-exporter/src/main/java/com/uaf/neo4j/plugin/model/UAFStereotypeRegistry.java` if you ever want to drive colouring from `domain` instead.

## Why these queries drop `:OperationalPerformer` / `:ResourcePerformer` / `:ServicePerformer`

In UAF 1.2 these are **abstract** stereotypes — concrete models almost never apply them directly. Modellers stereotype with the concrete subtypes (`OperationalRole`, `Organization`, `Post`, `ResourceArtifact`, `HardwareElement`, `SoftwareElement`, …). Querying for `(:OperationalPerformer)` therefore returns zero rows against any real export.

The Performer-related queries drop the source-label constraint and use the edge as the filter (`(p)-[:PERFORMS]->(:OperationalActivity)`). The cost is breadth — the result includes whatever performs the activity. To see what labels actually appear in your export, run this diagnostic in a Table card:

```cypher
MATCH (p)-[r:PERFORMS]->(a:OperationalActivity)
RETURN labels(p) AS source_labels, count(*) AS edges
ORDER BY edges DESC
```

Then narrow the dashboard query with an explicit `WHERE p:OperationalRole OR p:Organization OR ...` clause if needed. The unconstrained form is the safer default for a starter dashboard.

## Relationship-type filter — what's actually emitted

The traversal queries (decommissioning impact, AS-IS / TO-BE, resource/service neighbourhood) filter on edge types. The Java exporter emits 36 relationship types — the canonical list is in `msosa-model-exporter/src/main/java/com/uaf/neo4j/plugin/model/UAFRelationshipDTO.java`. The dashboard sticks to these names; tokens like `RESOURCEEXCHANGE` or `ITEMFLOW` are **not** emitted (an earlier draft used them — those traversals matched zero edges). For exchange/flow semantics use the emitted equivalents:

| You might expect | Emitted as |
|---|---|
| `RESOURCEEXCHANGE`, `OPERATIONALEXCHANGE`, `NEEDLINE` | `INFORMATION_FLOW` |
| `ITEMFLOW`, `OBJECTFLOW` | `FLOWS_TO` |
| `RESOURCE_INTERACTION` | `CONNECTED_TO` |
| `MESSAGEFLOW` | `MESSAGE_FLOW` |

The decommissioning-impact filter is therefore `REALISES|EXHIBITS|IMPLEMENTS|PERFORMS|DEPENDENCY|INFORMATION_FLOW|MESSAGE_FLOW|FLOWS_TO`. It deliberately excludes `INSTANCE_OF` and `HAS_ATTRIBUTE` (metamodel and structural — they'd flood the view) and `ASSOCIATED_WITH` (generic — too noisy).

## Where this fits

- `viz/neodash/uaf-starter.json` — the working NeoDash dashboard (this directory).
- `docker-compose/docker-compose.neodash.yml` — editor-mode overlay.
- `docker-compose/docker-compose.neodash-kiosk.yml` — kiosk-mode overlay, auto-loads the saved dashboard.
- `ontology/NEXT-STEPS.md` A-Box-visualisation entry — decision history for visualisation tooling.
