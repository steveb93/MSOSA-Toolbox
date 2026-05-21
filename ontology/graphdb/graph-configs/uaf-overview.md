# GraphDB Graph Config — UAF Overview

A pre-built **GraphDB Visual Graph configuration** that always opens on the same eight UAF domain-anchor classes, so you never have to search for a starting IRI by name. Click any anchor and the graph fans out:

- **on a class node** → subclasses (down the T-Box) and instances (jump to A-Box)
- **on an instance node** → outgoing UAF relationships (`uaf:performs`, `uaf:realises`, …) and any incoming references

GraphDB 11.x does not expose Visual Graph configs through the public REST API, so creating one is a one-time UI step. After that, the config is stored in GraphDB's system repository and persists across restarts of the `graphdb-uaf` container (because the `/opt/graphdb/home` volume is named — `docker-compose_graphdb-data`).

---

## 1. Create the config in the Workbench

1. Open <http://localhost:7200/>.
2. Pick the **uaf** repository (top-right selector).
3. **Explore → Visual graph**.
4. Click **Create graph config** (the "+" / gear icon, top-right of the Visual Graph page).
5. Fill the fields below — paste each SPARQL block into its matching tab.

| Field | Value |
|---|---|
| **Name** | `UAF Overview` |
| **Description** | `Starts from the 8 UAF domain-anchor classes; expand into stereotype subclasses and instance individuals.` |
| **Repository** | `uaf` |

Save. The config now appears under **Saved graph configs** on the Visual Graph landing page — one click opens the rendered graph.

---

## 2. The four queries

### Tab: **Starting point** → "Graph query"

GraphDB's starting-point query must be a `CONSTRUCT` (or `DESCRIBE`) — a `SELECT` is rejected by the form. It must also emit at least one **IRI→IRI triple per node you want rendered**; a CONSTRUCT that only emits literal-valued triples (e.g. `?anchor rdfs:label "…"`) produces a blank canvas because Visual Graph treats literals as node properties, not as graph structure.

The query below emits the 145 `subClassOf` edges from each direct stereotype subclass back to its UAF domain anchor. The canvas opens with 8 anchor hubs (`StrategicElement`, `OperationalElement`, `ResourceElement`, `ServiceElement`, `PersonnelElement`, `AcquisitionElement`, `SecurityElement`, `SharedElement`) each surrounded by its direct subclasses (Capability, CapabilityRole, OperationalActivity, OperationalPerformer, ResourceArtifact, …). Labels for these nodes are supplied by the Node Basics query.

```sparql
PREFIX uaf:  <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

CONSTRUCT {
  ?child rdfs:subClassOf ?anchor .
}
WHERE {
  VALUES ?anchor {
    uaf:StrategicElement
    uaf:OperationalElement
    uaf:ResourceElement
    uaf:ServiceElement
    uaf:PersonnelElement
    uaf:AcquisitionElement
    uaf:SecurityElement
    uaf:SharedElement
  }
  ?child rdfs:subClassOf ?anchor .
}
```

If you want a less dense initial view, add `LIMIT 60` at the end — you'll still see all 8 anchors plus a representative sample of their children, and the rest are one expand-click away.

### Tab: **Graph expansion**

`?node` is bound by GraphDB to the IRI of the node you just clicked. The query emits every outgoing and incoming edge whose other end is an IRI (literals are skipped — they belong on the node, not as edges). This single pattern covers both class clicks (subclasses are incoming `rdfs:subClassOf`, instances are incoming `rdf:type`) and instance clicks (outgoing `uaf:performs` / `uaf:realises` / …).

```sparql
PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

CONSTRUCT {
  ?node ?outP ?out .
  ?in   ?inP  ?node .
}
WHERE {
  {
    ?node ?outP ?out .
    FILTER(isIRI(?out))
  } UNION {
    ?in ?inP ?node .
    FILTER(isIRI(?in))
  }
}
LIMIT 50
```

The earlier draft of this query used `BIND(rdfs:subClassOf AS ?p)` inside `UNION`-branched patterns; that form parses but GraphDB's Visual Graph renderer drops the resulting triples (verified: clicking any anchor reports "0 connections"). The canonical "outgoing + incoming IRI edges" pattern above renders correctly. Bump `LIMIT 50` if you want to see more in one click; the rendering tends to get noisy past ~80 edges per expand.

### Tab: **Node basics**

```sparql
PREFIX rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?type ?label ?comment WHERE {
  OPTIONAL { ?node rdfs:label ?label }
  OPTIONAL { ?node rdfs:comment ?comment }
  OPTIONAL {
    ?node rdf:type ?type .
    # Prefer the most specific (leaf) class; RDFS reasoning would otherwise
    # report every ancestor class too.
    FILTER NOT EXISTS {
      ?node rdf:type ?moreSpecific .
      ?moreSpecific rdfs:subClassOf ?type .
      FILTER(?moreSpecific != ?type)
    }
  }
}
LIMIT 1
```

Drives what GraphDB shows under each node: the leaf rdf:type (used for colouring), the `rdfs:label`, and any `rdfs:comment` for the tooltip.

### Tab: **Edge basics**

```sparql
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?label WHERE {
  OPTIONAL { ?edge rdfs:label ?ontologyLabel }
  BIND(COALESCE(?ontologyLabel, REPLACE(STR(?edge), "^.*[#/]", "")) AS ?label)
}
LIMIT 1
```

`?edge` is bound to the IRI of each predicate the expansion query returned. The `OPTIONAL` + `COALESCE` is load-bearing — a naive `SELECT ?label WHERE { ?edge rdfs:label ?label }` returns zero rows for predicates that lack a label (e.g. `rdfs:subClassOf`, `rdf:type`, `uaf:domain` — none of which are labelled in the UAF MVO), and GraphDB's Visual Graph silently drops every edge whose basics query is empty. The symptom is "0 connections" after a click even though the expansion query is producing triples. The pattern above guarantees one row per edge — ontology label if present, IRI local-name otherwise (`subClassOf`, `type`, `domain`, …).

---

## 3. Optional — colour nodes by UAF domain

GraphDB lets you set per-class colours under **Visual Graph → Settings → Predicate colors / Type colors**. Suggested mapping (one colour per domain, matches the MSOSA Graph Inspector convention):

| Class | Colour |
|---|---|
| `uaf:StrategicElement`     | gold       |
| `uaf:OperationalElement`   | royal blue |
| `uaf:ResourceElement`      | green      |
| `uaf:ServiceElement`       | teal       |
| `uaf:PersonnelElement`     | purple     |
| `uaf:AcquisitionElement`   | orange     |
| `uaf:SecurityElement`      | red        |
| `uaf:SharedElement`        | grey       |

These settings are stored alongside the config in the system repository.

---

## 4. One-shot CONSTRUCT (no saved config needed)

For ad-hoc browsing without going through the config UI, paste this into **Visual Graph → SPARQL query** to render a snapshot of "all UAF domain anchors plus their direct subclasses" in one go:

```sparql
PREFIX uaf:  <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

CONSTRUCT {
  ?cls rdfs:subClassOf ?anchor .
  ?cls rdfs:label      ?label   .
} WHERE {
  VALUES ?anchor {
    uaf:StrategicElement  uaf:OperationalElement  uaf:ResourceElement
    uaf:ServiceElement    uaf:PersonnelElement    uaf:AcquisitionElement
    uaf:SecurityElement   uaf:SharedElement
  }
  ?cls rdfs:subClassOf ?anchor .
  OPTIONAL { ?cls rdfs:label ?label }
}
```

Useful as a sanity check after a `restart graphdb-loader` — if this query renders the eight domains and their stereotype children, the T-Box loaded cleanly.
