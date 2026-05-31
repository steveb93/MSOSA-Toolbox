# Enterprise architects — UAF KG as the agent's long-term semantic memory

This is the primary landing page for the agent-memory demo (#119). It
shows how an architecture-aware LLM agent uses the UAF knowledge graph as
its long-term semantic memory across the AS-IS → TO-BE transition.

A vector store tells you what TO-BE *resembles* in AS-IS. The graph tells
you what AS-IS no longer has a counterpart in TO-BE — which is the actual
decommissioning question. Resemblance gives you neighbours; the graph
gives you the impact closure that lets the agent reason about what to
retire, retain, and introduce.

## The agent task

> Given AS-IS and TO-BE sub-packages under the Operational (or Strategic)
> domain, produce a delta report: retired / retained / introduced
> elements, and trace impact through `realisedBy` / `tracedBy` closure to
> surface affected Capabilities and Resources.

## How the agent recognises phase

Transitional state is encoded by **sub-package partitioning** following the
Dassault Systèmes UAF *Way of Working* convention — e.g.
`Operational > AS-IS Capability` and `Operational > TO-BE Capability`. The
MSOSA exporter serialises this on every node as the `uaf:qualifiedName`
property (`::`-delimited package path), so the agent recognises phase via
a string filter — `FILTER(CONTAINS(?qname, "AS-IS"))` /
`FILTER(CONTAINS(?qname, "TO-BE"))`. No `UAFStereotypeRegistry` or
`UAFRelationshipDTO` extension is required.

Correspondence between AS-IS and TO-BE counterparts uses
`rdfs:label + rdf:type` (name + stereotype) within the same UAF domain as
the default rule, with an explicit `<<TRACES_TO>>` UML Dependency
between AS-IS and TO-BE elements as the disambiguating override when
name-matching is ambiguous (renames, splits, merges).

## Why the graph, not a vector store

| Question | Vector retrieval | Knowledge graph |
|---|---|---|
| "Which TO-BE element is nearest to this AS-IS one?" | Strong — embedding similarity | Possible but not the point |
| "Which AS-IS elements have **no counterpart** in TO-BE?" | Cannot express set-difference reliably | `FILTER NOT EXISTS` — query 2 below |
| "If I retire X, which Capabilities are affected?" | Returns text resembling X | `realisedBy*` / `tracedBy*` traversal — query 3 below |
| "Which TO-BE Capabilities have no realising Resource yet?" | Cannot constrain TO-BE on both sides | `FILTER NOT EXISTS` with phase-aware bind — query 4 below |
| "Which programme owns each delta?" | Surfaces documents mentioning the programme | Walks `Project`/`ProjectMilestone` to delta element — query 5 below |

The graph queries below are the agent's **procedural memory**: each one is
a deterministic, copy-pasteable SPARQL fragment that the agent chains
across the AS-IS and TO-BE phases to assemble the delta report.

## The five queries

Copy any block into `run_sparql` via the MCP tool. The full file lives at
[`ontology/queries/agent-memory-phase-delta.sparql`](../../ontology/queries/agent-memory-phase-delta.sparql)
including a `TRACES_TO` override variant (query 2b) and an introduced-side
variant (query 2a).

### 1. Phase membership

Returns every element in the AS-IS phase. Substitute `"AS-IS"` with
`"TO-BE"` for the other side.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?elem ?type ?name ?qname WHERE {
  ?elem a ?type ;
        rdfs:label ?name ;
        uaf:qualifiedName ?qname .
  FILTER(CONTAINS(?qname, "AS-IS"))
}
ORDER BY ?type ?name
```

### 2. Set difference — retired

Names present in AS-IS but absent in TO-BE under the same stereotype.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?type ?name WHERE {
  ?asis a ?type ;
        rdfs:label ?name ;
        uaf:qualifiedName ?asisQ .
  FILTER(CONTAINS(?asisQ, "AS-IS"))
  FILTER NOT EXISTS {
    ?tobe a ?type ;
          rdfs:label ?name ;
          uaf:qualifiedName ?tobeQ .
    FILTER(CONTAINS(?tobeQ, "TO-BE"))
  }
}
ORDER BY ?type ?name
```

### 3. Forward impact closure

For each retired element, walk `realisedBy*` and `tracedBy*` outwards to
enumerate everything whose architectural justification disappears.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?retiredType ?retiredName ?dependentType ?dependentName WHERE {
  ?retired a ?retiredType ;
           rdfs:label ?retiredName ;
           uaf:qualifiedName ?asisQ .
  FILTER(CONTAINS(?asisQ, "AS-IS"))
  FILTER NOT EXISTS {
    ?tobe a ?retiredType ;
          rdfs:label ?retiredName ;
          uaf:qualifiedName ?tobeQ .
    FILTER(CONTAINS(?tobeQ, "TO-BE"))
  }
  ?retired (uaf:realisedBy|uaf:tracedBy)+ ?dependent .
  ?dependent a ?dependentType ;
             rdfs:label ?dependentName .
}
ORDER BY ?retiredName ?dependentType ?dependentName
```

### 4. Phase-aware capability gap

TO-BE Capabilities with no realising TO-BE ResourcePerformer.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?cap ?capName WHERE {
  ?cap a uaf:Capability ;
       rdfs:label ?capName ;
       uaf:qualifiedName ?capQ .
  FILTER(CONTAINS(?capQ, "TO-BE"))
  FILTER NOT EXISTS {
    ?cap uaf:realisedBy ?perf .
    ?perf a uaf:ResourcePerformer ;
          uaf:qualifiedName ?perfQ .
    FILTER(CONTAINS(?perfQ, "TO-BE"))
  }
}
ORDER BY ?capName
```

### 5. Acquisition trace (optional)

Project / ProjectMilestone whose scope touches each delta element.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?projType ?projName ?deltaType ?deltaName ?phase WHERE {
  { ?proj a uaf:Project . BIND(uaf:Project AS ?projType) }
  UNION
  { ?proj a uaf:ProjectMilestone . BIND(uaf:ProjectMilestone AS ?projType) }
  ?proj rdfs:label ?projName ;
        (uaf:composedOf|uaf:realises|uaf:tracesTo)+ ?delta .
  ?delta a ?deltaType ;
         rdfs:label ?deltaName ;
         uaf:qualifiedName ?deltaQ .
  BIND(IF(CONTAINS(?deltaQ, "AS-IS"), "AS-IS",
         IF(CONTAINS(?deltaQ, "TO-BE"), "TO-BE", "OTHER")) AS ?phase)
  FILTER(?phase != "OTHER")
}
ORDER BY ?projName ?phase ?deltaName
```

## Related personas

Same queries, different lens — pick the page that matches your decision:

- [Solution architects](solution-architects.md) — TO-BE design integrity:
  which proposed elements have no AS-IS analogue and therefore need new
  resource allocation.
- [Capability owners](capability-owners.md) — "what changes for *my*
  Capability" — filtered to one Capability and its dependents.
- [Decision makers](decision-makers.md) — counts only: how many elements
  retired / retained / introduced, by domain.
