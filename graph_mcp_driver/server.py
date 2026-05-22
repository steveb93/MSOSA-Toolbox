import os
import sys

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


def main() -> None:
    """Console entry point. Also referenced by graph_mcp_driver/__main__.py
    and by the `graph-mcp-driver` console script declared in pyproject.toml."""
    print(f"MCP Neo4j server starting for {uri} (SPARQL: {sparql_url})", file=sys.stderr)
    mcp.run()


if __name__ == "__main__":
    main()
