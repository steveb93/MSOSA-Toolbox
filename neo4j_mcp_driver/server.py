import os
import sys
from mcp.server.fastmcp import FastMCP
from neo4j import GraphDatabase

mcp = FastMCP("local-neo4j")

uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
user = os.getenv("NEO4J_USERNAME", "neo4j")
password = os.getenv("NEO4J_PASSWORD", "Password123")
database = os.getenv("NEO4J_DATABASE", "neo4j")

driver = GraphDatabase.driver(uri, auth=(user, password))

@mcp.tool()
def run_cypher(query: str) -> list[dict]:
    """Run a Cypher query against Neo4j."""
    with driver.session(database=database) as session:
        return [record.data() for record in session.run(query)]

if __name__ == "__main__":
    print(f"MCP Neo4j server starting for {uri}", file=sys.stderr)
    mcp.run()