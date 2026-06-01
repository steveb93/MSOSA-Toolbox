"""
Smoke test for the Neo4j Graph Data Science (GDS) plugin.

The plugin is bundled into the Docker image via NEO4J_PLUGINS in
docker-compose/docker-compose.yml. These checks confirm it loaded and is
callable so the rest of the Stage 5 work (projections, PageRank, community
detection) can rely on it.

Requires the Docker Neo4j instance to be running:
    cd docker-compose && docker compose up -d

Skip in CI or offline environments:
    pytest -m "not neo4j"
"""

import os

import pytest

pytestmark = pytest.mark.neo4j


@pytest.fixture(scope="module")
def neo4j_driver():
    from neo4j import GraphDatabase
    uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    user = os.getenv("NEO4J_USERNAME", "neo4j")
    password = os.getenv("NEO4J_PASSWORD", "Password123")
    driver = GraphDatabase.driver(uri, auth=(user, password))
    yield driver
    driver.close()


def test_gds_version_callable(neo4j_driver):
    """gds.version() returns a non-empty string — confirms the plugin loaded."""
    database = os.getenv("NEO4J_DATABASE", "neo4j")
    with neo4j_driver.session(database=database) as session:
        record = session.run("RETURN gds.version() AS v").single()
    assert record is not None
    version = record["v"]
    assert isinstance(version, str) and len(version) > 0


def test_gds_list_returns_procedures(neo4j_driver):
    """gds.list() should report at least one algorithm — sanity check on the
    procedure surface, not a strict count (algorithm catalogue changes between
    GDS releases)."""
    database = os.getenv("NEO4J_DATABASE", "neo4j")
    with neo4j_driver.session(database=database) as session:
        count = session.run("CALL gds.list() YIELD name RETURN count(*) AS n").single()["n"]
    assert count > 0, "gds.list() returned no procedures — plugin missing or unhealthy"


def test_pagerank_stream_on_native_projection(neo4j_driver):
    """End-to-end smoke: project the live UAF graph (all stereotype-bearing
    nodes + all relationships between them), run PageRank in stream mode, drop
    the projection. Stream mode means nothing is written back to the database
    — pure read-side validation."""
    database = os.getenv("NEO4J_DATABASE", "neo4j")
    graph_name = "uaf-gds-smoke"
    with neo4j_driver.session(database=database) as session:
        session.run(
            "CALL gds.graph.exists($name) YIELD exists "
            "WITH exists WHERE exists "
            "CALL gds.graph.drop($name) YIELD graphName RETURN graphName",
            name=graph_name,
        ).consume()
        try:
            project = session.run(
                "MATCH (src) WHERE src.stereotype IS NOT NULL "
                "OPTIONAL MATCH (src)-[r]->(tgt) WHERE tgt.stereotype IS NOT NULL "
                "WITH gds.graph.project($name, src, tgt) AS g "
                "RETURN g.nodeCount AS nodes",
                name=graph_name,
            ).single()
            assert project is not None
            node_count = project["nodes"] or 0
            if node_count == 0:
                pytest.skip(
                    "No stereotype-bearing nodes in the graph — load a UAF export first."
                )
            ranked = session.run(
                "CALL gds.pageRank.stream($name) YIELD nodeId, score "
                "RETURN count(*) AS n",
                name=graph_name,
            ).single()["n"]
            assert ranked == node_count
        finally:
            session.run(
                "CALL gds.graph.exists($name) YIELD exists "
                "WITH exists WHERE exists "
                "CALL gds.graph.drop($name) YIELD graphName RETURN graphName",
                name=graph_name,
            ).consume()
