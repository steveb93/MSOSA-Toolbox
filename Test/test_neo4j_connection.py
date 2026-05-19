"""
Live connectivity test for Neo4j.

Requires the Docker Neo4j instance to be running:
    cd docker-compose && docker compose up -d

Skip these tests in CI or offline environments:
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


def test_connection_returns_status(neo4j_driver):
    database = os.getenv("NEO4J_DATABASE", "neo4j")
    with neo4j_driver.session(database=database) as session:
        record = session.run("RETURN 'connected' AS status").single()
    assert record is not None
    assert record["status"] == "connected"


def test_uaf_metamodel_nodes_exist(neo4j_driver):
    """Verify init_uaf_graph.cypher has been run — Domain nodes should be present."""
    database = os.getenv("NEO4J_DATABASE", "neo4j")
    with neo4j_driver.session(database=database) as session:
        count = session.run("MATCH (d:Domain) RETURN count(d) AS n").single()["n"]
    assert count > 0, (
        "No :Domain nodes found. Run: "
        "cypher-shell -u neo4j -p Password123 -f cypher/init_uaf_graph.cypher"
    )


def test_uaf_stereotype_nodes_exist(neo4j_driver):
    """Verify :Stereotype metamodel nodes are present after graph initialisation."""
    database = os.getenv("NEO4J_DATABASE", "neo4j")
    with neo4j_driver.session(database=database) as session:
        count = session.run("MATCH (s:Stereotype) RETURN count(s) AS n").single()["n"]
    assert count > 0, "No :Stereotype nodes found — re-run init_uaf_graph.cypher"
