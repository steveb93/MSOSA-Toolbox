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
