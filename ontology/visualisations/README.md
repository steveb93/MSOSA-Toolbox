# Visualisation layer

Two zero-platform workflows — neither adds a long-running container or a
second data engine. Both consume artefacts the toolbox already produces:
`ontology/uaf-mvo.ttl` (T-Box) and the live Fuseki SPARQL endpoint (A-Box).

|  | Looks at | Use this when |
|---|---|---|
| **A-Box: SPARQL → GraphML → Cytoscape / yEd / Gephi** | Live model data | "Show me the Capabilities and what realises them in this exported model." |
| **T-Box: Protégé / webvowl.dev** | The ontology itself | "Show me the UAF class hierarchy and properties." |

---

## A-Box workflow — SPARQL → GraphML

Run `ontology/codegen/sparql_to_graphml.py` against the live Fuseki
endpoint (or a local TTL fixture, for offline use). It executes a
SPARQL `CONSTRUCT` query and writes a GraphML file you can open in any
mainstream graph desktop tool.

### Quickstart (live Fuseki)

```powershell
# Assumes Fuseki is up — see docker-compose/docker-compose.fuseki.yml
python ontology/codegen/sparql_to_graphml.py `
    --preset capability-realisation `
    --output cap-realisation.graphml
```

Open `cap-realisation.graphml` in:

- **[Cytoscape Desktop](https://cytoscape.org/)** — File → Import → Network from File. Apply a force-directed layout (Layout → Prefuse Force Directed).
- **[yEd](https://www.yworks.com/products/yed)** — File → Open. Layout → Hierarchical or Organic.
- **[Gephi](https://gephi.org/)** — File → Open. Statistics → Run Modularity for community colouring.

### Available presets

| Preset | Returns | Mirrors |
|---|---|---|
| `capability-realisation` | Capability → CapabilityConfiguration → Operational/Resource realisers | `uaf-mvo-axioms.ttl` §4 |
| `security-controls` | Risks, Controls, ControlFamilies, Assets | Security SHACL shapes |
| `operational-flow` | Activities, Performers, Processes | §5 + Operational SHACL shapes |
| `resource-allocation` | Performers, Roles, Architecture, Artifacts | §5 + Resource SHACL shapes |

### Custom query

Drop any SPARQL `CONSTRUCT` file into `ontology/visualisations/queries/`
and reference it by stem with `--preset`, or pass an arbitrary path with
`--query`:

```powershell
python ontology/codegen/sparql_to_graphml.py `
    --query path/to/my-query.sparql `
    --output my-view.graphml
```

### Offline / fixture mode

Skip the network round-trip and run the query against a local Turtle file
— useful for offline work or for replaying a known-good dump:

```powershell
python ontology/codegen/sparql_to_graphml.py `
    --from-file ontology/dump/uaf-instance.ttl `
    --preset operational-flow `
    --output ops.graphml
```

### Cytoscape.js JSON (web embedding)

For a web page that embeds [Cytoscape.js](https://js.cytoscape.org/),
emit the same data as Cytoscape JSON:

```powershell
python ontology/codegen/sparql_to_graphml.py `
    --preset capability-realisation `
    --format cyjs `
    --output cap-realisation.cyjs
```

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `NEO4J_SPARQL_URL` | `http://localhost:3030/uaf/sparql` | Fuseki endpoint |
| `FUSEKI_USER` | `admin` | Basic-auth user |
| `FUSEKI_PASSWORD` | `Password123` | Basic-auth password |

The defaults match `docker-compose.fuseki.yml`; override via env vars if
Fuseki is hosted elsewhere.

---

## T-Box workflow — Protégé or webvowl.dev

The T-Box (the ontology itself — classes, properties, restrictions) lives
in `ontology/uaf-mvo.ttl` plus `ontology/uaf-mvo-axioms.ttl`. Two viewers,
both free, both zero-install on the toolbox side:

### Option 1 — Protégé (desktop)

[Protégé](https://protege.stanford.edu/) is the de-facto ontology editor;
its built-in views handle UAF MVO comfortably.

1. Install Protégé Desktop.
2. File → Open: `ontology/uaf-mvo.ttl`.
3. (Optional) File → Import: `ontology/uaf-mvo-axioms.ttl` to bring in
   the Stage-3 inverses, disjointness, dominance, and someValuesFrom
   restrictions.
4. Open the **OntoGraf** or **OWLViz** tab to render the class hierarchy
   as a graph. **DL Query** for ad-hoc subsumption probes.

### Option 2 — webvowl.dev (browser)

[WebVOWL](https://service.visualdataweb.de/webvowl/) renders OWL
ontologies as the **Visual Notation for OWL Ontologies** (VOWL) — a
force-directed view with class colouring by namespace and property
arrows for inverses / domain / range.

1. Open https://service.visualdataweb.de/webvowl/ in a browser.
2. Ontology → Select ontology file → upload `ontology/uaf-mvo.ttl`.
3. Pan, zoom, drag classes; toggle property labels for clarity.

WebVOWL accepts TTL, OWL/XML, or its own `.vowl` JSON. For a
multi-file view, concatenate `uaf-mvo.ttl` + `uaf-mvo-axioms.ttl`
into a single file first (or use Protégé's import to merge them).

---

## Why GraphML, not GraphDB Workbench

GraphDB's appeal was its **node-visualisation Workbench**, not its
SPARQL engine — Fuseki already handles SPARQL + OWL FB reasoning. The
GraphML exporter closes the visualisation gap without adding a second
triplestore, a long-running viz service, or any vendor lock-in:

- **GraphML** is the lingua franca of graph desktop tools (Cytoscape,
  yEd, Gephi, NetworkX, Sigma).
- **Cytoscape Desktop** is free, mature, and the standard view in the
  systems-biology and network-analysis communities — UAF graphs are
  small enough that any of its layouts work well.
- **Protégé** and **webvowl.dev** between them cover the T-Box
  perspective without a separate hosting story.

If you later want a long-running browser-embedded viewer, the same
`sparql_to_graphml.py` `--format cyjs` output drops into Cytoscape.js
on any static web page — no new server required.
