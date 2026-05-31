# Solution architects — TO-BE design integrity

This is a secondary landing for the agent-memory demo (#119), lensed for
solution architects who own the TO-BE design. The full narrative is on
the [enterprise architects](enterprise-architects.md) page; this page
focuses on the design-integrity question:

> Which proposed TO-BE elements have **no AS-IS analogue** and therefore
> need explicit new resource allocation, integration spec, or
> commissioning plan?

Phase recognition uses `uaf:qualifiedName` substring matching against the
UAF Way of Working sub-package convention (`AS-IS …`, `TO-BE …`);
correspondence uses `rdfs:label + rdf:type` with `<<TRACES_TO>>` as the
modeller's disambiguating override. See the
[enterprise architects](enterprise-architects.md#how-the-agent-recognises-phase)
page for the full mechanism.

## The query that matters here

Set difference, TO-BE side — every TO-BE element with no AS-IS predecessor
under the same stereotype. These are the elements that need a new design
artefact, not a like-for-like replacement.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?type ?name WHERE {
  ?tobe a ?type ;
        rdfs:label ?name ;
        uaf:qualifiedName ?tobeQ .
  FILTER(CONTAINS(?tobeQ, "TO-BE"))
  FILTER NOT EXISTS {
    ?asis a ?type ;
          rdfs:label ?name ;
          uaf:qualifiedName ?asisQ .
    FILTER(CONTAINS(?asisQ, "AS-IS"))
  }
}
ORDER BY ?type ?name
```

Combine with the phase-aware capability gap query
([enterprise architects §4](enterprise-architects.md#4-phase-aware-capability-gap))
to find TO-BE Capabilities introduced **without** a realising
ResourcePerformer — those are the design holes you need to close before
the TO-BE architecture is buildable.

## Why this beats vector search

A vector store will retrieve TO-BE elements that look like *something*
already in AS-IS — useful for analogy-driven design, useless for spotting
genuinely new TO-BE elements that need fresh allocation. The graph's
`FILTER NOT EXISTS` returns exactly the set the vector store cannot:
elements with no antecedent.

## Related personas

- [Enterprise architects](enterprise-architects.md) — primary landing,
  full delta narrative and all five queries.
- [Capability owners](capability-owners.md) — single-Capability scope.
- [Decision makers](decision-makers.md) — counts only.
