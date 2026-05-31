# Decision makers — delta counts by domain

This is a secondary landing for the agent-memory demo (#119), lensed for
decision makers who need totals rather than item-level detail. The full
narrative is on the [enterprise architects](enterprise-architects.md)
page; this page collapses each delta set to a count per UAF domain.

Phase recognition uses `uaf:qualifiedName` substring matching against the
UAF Way of Working sub-package convention; correspondence uses
`rdfs:label + rdf:type`. See the
[enterprise architects](enterprise-architects.md#how-the-agent-recognises-phase)
page for the full mechanism.

## The queries that matter here

### Retired count by domain

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?domain (COUNT(DISTINCT ?asis) AS ?retired) WHERE {
  ?asis a ?type ;
        rdfs:label ?name ;
        uaf:qualifiedName ?asisQ ;
        uaf:domain ?domain .
  FILTER(CONTAINS(?asisQ, "AS-IS"))
  FILTER NOT EXISTS {
    ?tobe a ?type ;
          rdfs:label ?name ;
          uaf:qualifiedName ?tobeQ .
    FILTER(CONTAINS(?tobeQ, "TO-BE"))
  }
}
GROUP BY ?domain
ORDER BY DESC(?retired)
```

### Introduced count by domain

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?domain (COUNT(DISTINCT ?tobe) AS ?introduced) WHERE {
  ?tobe a ?type ;
        rdfs:label ?name ;
        uaf:qualifiedName ?tobeQ ;
        uaf:domain ?domain .
  FILTER(CONTAINS(?tobeQ, "TO-BE"))
  FILTER NOT EXISTS {
    ?asis a ?type ;
          rdfs:label ?name ;
          uaf:qualifiedName ?asisQ .
    FILTER(CONTAINS(?asisQ, "AS-IS"))
  }
}
GROUP BY ?domain
ORDER BY DESC(?introduced)
```

### Retained count by domain

Elements present in **both** phases under the same stereotype — the
continuity number.

```sparql
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?domain (COUNT(DISTINCT ?name) AS ?retained) WHERE {
  ?asis a ?type ;
        rdfs:label ?name ;
        uaf:qualifiedName ?asisQ ;
        uaf:domain ?domain .
  FILTER(CONTAINS(?asisQ, "AS-IS"))
  ?tobe a ?type ;
        rdfs:label ?name ;
        uaf:qualifiedName ?tobeQ .
  FILTER(CONTAINS(?tobeQ, "TO-BE"))
}
GROUP BY ?domain
ORDER BY DESC(?retained)
```

Read together: retired tells you what the programme is closing out,
introduced tells you what it is committing to deliver, retained tells you
the continuity baseline that survives the transition.

## Why this beats vector search

Vector retrieval cannot answer "how many" reliably — it returns ranked
neighbours, not sets. The graph's `COUNT(DISTINCT …) GROUP BY ?domain`
gives an auditable total per UAF domain that you can put in a slide deck
without spot-checking.

## Related personas

- [Enterprise architects](enterprise-architects.md) — primary landing,
  full delta narrative and all five queries.
- [Solution architects](solution-architects.md) — TO-BE design integrity.
- [Capability owners](capability-owners.md) — single-Capability scope.
