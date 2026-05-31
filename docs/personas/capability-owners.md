# Capability owners — "what changes for *my* Capability"

This is a secondary landing for the agent-memory demo (#119), lensed for
capability owners who care about one Capability and what depends on it.
The full narrative is on the
[enterprise architects](enterprise-architects.md) page; this page
restricts the scope to a single named Capability and its dependents.

Phase recognition uses `uaf:qualifiedName` substring matching against the
UAF Way of Working sub-package convention; correspondence uses
`rdfs:label + rdf:type` with `<<TRACES_TO>>` as the disambiguating
override. See the
[enterprise architects](enterprise-architects.md#how-the-agent-recognises-phase)
page for the full mechanism.

## The query that matters here

Replace `"YOUR_CAPABILITY"` with the name of your Capability. Returns
every dependent reached via `realisedBy*` / `tracedBy*` across both
phases, with the phase tag so you can see which dependents survive into
TO-BE and which retire with AS-IS.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?dependentType ?dependentName ?phase WHERE {
  ?cap a uaf:Capability ;
       rdfs:label "YOUR_CAPABILITY" ;
       uaf:qualifiedName ?capQ .
  ?cap (uaf:realisedBy|uaf:tracedBy)+ ?dependent .
  ?dependent a ?dependentType ;
             rdfs:label ?dependentName ;
             uaf:qualifiedName ?depQ .
  BIND(IF(CONTAINS(?depQ, "AS-IS"), "AS-IS",
         IF(CONTAINS(?depQ, "TO-BE"), "TO-BE", "OTHER")) AS ?phase)
}
ORDER BY ?phase ?dependentType ?dependentName
```

Cross-check against the phase-aware capability gap
([enterprise architects §4](enterprise-architects.md#4-phase-aware-capability-gap))
— if your Capability appears in that result, it has been promised in
TO-BE but no TO-BE ResourcePerformer is yet committed to realising it.

## Why this beats vector search

Asking a vector store "what changes for my Capability" surfaces text that
*mentions* your Capability. The graph traversal gives you the actual
dependents — Operational Activities, Resource Performers, Services,
ProjectMilestones — and labels each by phase, so the change picture is
structured, not anecdotal.

## Related personas

- [Enterprise architects](enterprise-architects.md) — primary landing,
  full delta narrative and all five queries.
- [Solution architects](solution-architects.md) — TO-BE design integrity.
- [Decision makers](decision-makers.md) — counts only.
