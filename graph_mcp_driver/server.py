import os
import sys
from pathlib import Path

import httpx
from mcp.server.fastmcp import FastMCP
from neo4j import GraphDatabase

mcp = FastMCP("local-neo4j")

uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
user = os.getenv("NEO4J_USERNAME", "neo4j")
password = os.getenv("NEO4J_PASSWORD", "Password123")
database = os.getenv("NEO4J_DATABASE", "neo4j")
sparql_url = os.getenv(
    "NEO4J_SPARQL_URL",
    "http://localhost:3030/uaf/sparql",
)
# Non-reasoning SPARQL endpoint — same base model, no OWL FB closure. Used by
# analytical tools whose queries consume only directly-emitted triples (no
# inverseOf inferences, no subsumption). Bypasses the reasoner overhead that
# can take OWL-side queries minutes to evaluate on modest datasets.
sparql_raw_url = os.getenv(
    "NEO4J_SPARQL_RAW_URL",
    "http://localhost:3030/uaf-raw/sparql",
)
sparql_auth = (
    os.getenv("FUSEKI_USER", "admin"),
    os.getenv("FUSEKI_PASSWORD", "Password123"),
)
shapes_path = os.getenv(
    "UAF_SHAPES_PATH",
    str(Path(__file__).resolve().parents[1] / "ontology" / "shapes" / "uaf-shapes.ttl"),
)

driver = GraphDatabase.driver(uri, auth=(user, password))


@mcp.tool()
def run_cypher(query: str) -> list[dict]:
    """Run a Cypher query against Neo4j."""
    with driver.session(database=database) as session:
        return [record.data() for record in session.run(query)]


@mcp.tool()
def run_sparql(query: str) -> list[dict]:
    """Run a SPARQL 1.1 SELECT query against the Fuseki endpoint.

    Returns one dict per binding row, mapping variable name to its lexical value.
    Use this for ontology-aware queries (subsumption via uaf:StrategicElement,
    cross-language traceability, etc.). For pure graph traversal, prefer run_cypher.
    """
    response = httpx.post(
        sparql_url,
        auth=sparql_auth,
        data={"query": query},
        headers={"Accept": "application/sparql-results+json"},
        timeout=30.0,
    )
    response.raise_for_status()
    bindings = response.json().get("results", {}).get("bindings", [])
    return [{k: v["value"] for k, v in row.items()} for row in bindings]


# Forward-direction realisation predicates (the ones the dump emits directly).
# Using a single-hop alternation in path position lets the engine decompose
# into indexed lookups; transitive variants `(...)+` against the reasoning
# endpoint OOM the reasoner on this dataset and are not needed when we already
# have the explicit edges.
_CAPABILITY_GAPS_QUERY = """\
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX uafgds: <http://msosa-toolbox.local/uaf/gds#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?capability ?name ?pagerank WHERE {
  ?capability a uaf:Capability ;
              rdfs:label ?name ;
              uafgds:pagerank ?pagerank .
  FILTER NOT EXISTS {
    ?resource (uaf:realises|uaf:exhibits|uaf:tracesTo|uaf:implements) ?capability .
    ?resource uaf:domain "RESOURCE" .
  }
}
ORDER BY DESC(?pagerank)
LIMIT %d
"""

_RECOMMEND_RESOURCES_QUERY = """\
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX uafgds: <http://msosa-toolbox.local/uaf/gds#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?resource ?name ?stereotype ?pagerank (COUNT(DISTINCT ?peer) AS ?peersRealised) WHERE {
  ?resource (uaf:realises|uaf:exhibits|uaf:tracesTo|uaf:implements) ?peer .
  ?peer a uaf:Capability .
  ?resource uaf:domain "RESOURCE" ;
            rdfs:label ?name ;
            a ?stereotype ;
            uafgds:pagerank ?pagerank .
  FILTER(?peer != <%s>)
  FILTER(STRSTARTS(STR(?stereotype), STR(uaf:)))
}
GROUP BY ?resource ?name ?stereotype ?pagerank
ORDER BY DESC(?peersRealised) DESC(?pagerank)
LIMIT %d
"""


def _sparql_select(query: str, endpoint: str | None = None) -> list[dict]:
    response = httpx.post(
        endpoint or sparql_url,
        auth=sparql_auth,
        data={"query": query},
        headers={"Accept": "application/sparql-results+json"},
        timeout=30.0,
    )
    response.raise_for_status()
    bindings = response.json().get("results", {}).get("bindings", [])
    return [{k: v["value"] for k, v in row.items()} for row in bindings]


@mcp.tool()
def find_capability_gaps(limit: int = 25) -> list[dict]:
    """List Capabilities with no realisation chain to a RESOURCE-domain element,
    ranked by PageRank.

    Surfaces the most consequential coverage gaps in the model: strategic intent
    that nothing yet delivers, ordered by how central the Capability is in the
    full UAF trace graph. Reads `uafgds:pagerank` triples — requires the GDS
    write-back + dump_to_rdf.py refresh path (cookbook §6a, NEXT-STEPS Stage 5).

    Routes to the non-reasoning /uaf-raw/sparql endpoint (NEO4J_SPARQL_RAW_URL
    env var) — every triple consumed is directly emitted by the dump, so OWL
    inference would add cost without changing results.

    Returns: list of {capability, name, pagerank}. Empty if no PageRank has been
    materialised yet, or if the model has no realisation gaps.
    """
    return _sparql_select(_CAPABILITY_GAPS_QUERY % int(limit),
                          endpoint=sparql_raw_url)


@mcp.tool()
def recommend_resources_for_gap(capability_iri: str, k: int = 10) -> list[dict]:
    """Recommend RESOURCE-domain candidates that could realise a Capability gap.

    Content-based: each candidate is scored by how many *other* Capabilities it
    already realises (peer-realiser frequency), broken by `uafgds:pagerank` as
    importance. Universal players surface first.

    Pass the Capability IRI from find_capability_gaps()[i]["capability"]. Routes
    to the non-reasoning /uaf-raw/sparql endpoint for the same reason as
    find_capability_gaps. Returns list of
    {resource, name, stereotype, pagerank, peersRealised}.
    """
    return _sparql_select(_RECOMMEND_RESOURCES_QUERY % (capability_iri, int(k)),
                          endpoint=sparql_raw_url)


_TOP_PAGERANK_QUERY = """\
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
PREFIX uafgds: <http://msosa-toolbox.local/uaf/gds#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
SELECT ?node ?name ?type ?pagerank WHERE {
  ?node uafgds:pagerank ?pagerank ;
        rdfs:label ?name ;
        a ?type .
  FILTER(STRSTARTS(STR(?type), STR(uaf:)))
}
ORDER BY DESC(?pagerank)
LIMIT %d
"""

_DOMAIN_COUNTS_QUERY = """\
PREFIX uaf: <http://msosa-toolbox.local/uaf#>
SELECT ?domain (COUNT(DISTINCT ?node) AS ?count) WHERE {
  ?node uaf:domain ?domain .
}
GROUP BY ?domain
ORDER BY DESC(?count)
"""


@mcp.tool()
def find_top_n_by_pagerank(limit: int = 25) -> list[dict]:
    """Top-N most-influential UAF elements by GDS PageRank.

    Surfaces the elements that the most trace/realisation/exhibits paths
    converge on — usually load-bearing Capabilities, Activities, or System
    blocks. Same data source and refresh path as find_capability_gaps; routes
    to the non-reasoning /uaf-raw/sparql endpoint.

    Returns: list of {node, name, type, pagerank}.
    """
    return _sparql_select(_TOP_PAGERANK_QUERY % int(limit),
                          endpoint=sparql_raw_url)


@mcp.tool()
def count_nodes_by_domain() -> list[dict]:
    """Node count per UAF domain — model coverage breakdown.

    Counts distinct nodes carrying a uaf:domain literal, grouped and ordered
    descending. Cheap; consumed by the decision dashboard's composition panel
    and useful for spotting under-modelled domains (a tiny SECURITY count when
    the programme has accreditation pressure, for example). Routes to the
    non-reasoning /uaf-raw/sparql endpoint.

    Returns: list of {domain, count} ordered by descending count.
    """
    return _sparql_select(_DOMAIN_COUNTS_QUERY, endpoint=sparql_raw_url)


@mcp.tool()
def validate_shacl(shapes_file: str | None = None) -> dict:
    """Validate the live Fuseki dataset against UAF SHACL shapes (Stage 3 scaffolding).

    Pulls every triple from the SPARQL endpoint via CONSTRUCT, then runs pyshacl
    against the shapes file (default: ontology/shapes/uaf-shapes.ttl, override
    via UAF_SHAPES_PATH env var or the shapes_file argument).

    Returns: {"conforms": bool, "results": [{focus_node, source_shape, message,
    severity, path, value}, ...]}.

    Requires: pyshacl + rdflib (install with `pip install pyshacl`). Returns an
    error dict if pyshacl is not installed.
    """
    try:
        from pyshacl import validate
        from rdflib import Graph, Namespace, URIRef
    except ImportError as exc:
        return {"error": f"pyshacl not installed: {exc}. "
                          f"Install with: pip install pyshacl"}

    target_shapes = Path(shapes_file or shapes_path)
    if not target_shapes.is_file():
        return {"error": f"shapes file not found: {target_shapes}. "
                          f"Set UAF_SHAPES_PATH or pass shapes_file."}

    construct_response = httpx.post(
        sparql_url,
        auth=sparql_auth,
        data={"query": "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"},
        headers={"Accept": "text/turtle"},
        timeout=60.0,
    )
    construct_response.raise_for_status()

    data_graph = Graph()
    data_graph.parse(data=construct_response.text, format="turtle")

    shapes_graph = Graph()
    shapes_graph.parse(target_shapes, format="turtle")

    conforms, report_graph, _ = validate(
        data_graph=data_graph,
        shacl_graph=shapes_graph,
        inference="rdfsowlrl",
        meta_shacl=False,
        advanced=True,
    )

    SH = Namespace("http://www.w3.org/ns/shacl#")
    RDF_TYPE = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    rows = []
    for vr in report_graph.subjects(predicate=RDF_TYPE,
                                    object=URIRef(f"{SH}ValidationResult")):
        rows.append({
            "focus_node": str(report_graph.value(vr, SH.focusNode) or ""),
            "source_shape": str(report_graph.value(vr, SH.sourceShape) or ""),
            "message": str(report_graph.value(vr, SH.resultMessage) or ""),
            "severity": str(report_graph.value(vr, SH.resultSeverity) or "")
                            .rsplit("#", 1)[-1] or "Violation",
            "path": str(report_graph.value(vr, SH.resultPath) or ""),
            "value": str(report_graph.value(vr, SH.value) or ""),
        })
    return {"conforms": conforms, "results": rows}


def main() -> None:
    """Console entry point. Also referenced by graph_mcp_driver/__main__.py
    and by the `graph-mcp-driver` console script declared in pyproject.toml."""
    print(f"MCP Neo4j server starting for {uri} (SPARQL: {sparql_url})", file=sys.stderr)
    mcp.run()


if __name__ == "__main__":
    main()
