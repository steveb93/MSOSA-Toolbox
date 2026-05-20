"""Traverser regression — issue #75.

Asserts that the post-fix exporter produces at least one node for each of the
key Operational and Resource stereotypes that the pre-fix traverser was
silently dropping (because of first-match-wins stereotype selection plus the
Package-only recursion).

Requires:
    - Neo4j running and reachable on bolt://localhost:7687
    - A model has been exported against the canonical test model

Skip on CI / offline:
    pytest -m "not neo4j"

The matching Cypher file at Test/queries/regression_traverser.cypher carries
the same assertions for cypher-shell / Neo4j Browser use.
"""

from __future__ import annotations

import os

import pytest

from neo4j import GraphDatabase

pytestmark = pytest.mark.neo4j


@pytest.fixture(scope="module")
def driver():
    uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    user = os.getenv("NEO4J_USERNAME", "neo4j")
    password = os.getenv("NEO4J_PASSWORD", "Password123")
    d = GraphDatabase.driver(uri, auth=(user, password))
    yield d
    d.close()


@pytest.fixture(scope="module")
def database() -> str:
    return os.getenv("NEO4J_DATABASE", "neo4j")


def _count(driver, database: str, stereotype: str) -> int:
    with driver.session(database=database) as session:
        record = session.run(
            "MATCH (n:UAFElement {stereotype: $s}) RETURN count(n) AS c",
            s=stereotype,
        ).single()
        return record["c"] if record else 0


def _edge_count(driver, database: str, rel_type: str) -> int:
    with driver.session(database=database) as session:
        # rel_type is a constant from the canonical REL_* set — safe to interpolate.
        record = session.run(
            f"MATCH ()-[r:{rel_type}]->() RETURN count(r) AS c"
        ).single()
        return record["c"] if record else 0


def _graph_is_empty(driver, database: str) -> bool:
    with driver.session(database=database) as session:
        record = session.run("MATCH (n:UAFElement) RETURN count(n) AS c").single()
        return (record["c"] if record else 0) == 0


# ── Operational domain (#75 RC #1 + RC #2) ───────────────────────────────────

OPERATIONAL_STEREOTYPES = [
    "OperationalPerformer",
    "OperationalActivity",
    "OperationalProcess",
    "OperationalFunction",
]

RESOURCE_STEREOTYPES = [
    "ResourcePerformer",
    "ResourceFunction",
    "ResourceArtifact",
]


@pytest.mark.parametrize("stereotype", OPERATIONAL_STEREOTYPES)
def test_operational_stereotype_has_at_least_one_instance(driver, database, stereotype):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    n = _count(driver, database, stereotype)
    assert n > 0, (
        f"{stereotype} count is zero. Pre-fix this happened because the traverser "
        "preferred SysML Block on multi-stereotyped elements or could not recurse "
        "into classifier-owned content. Re-run the export and verify your test "
        "model contains at least one such element."
    )


@pytest.mark.parametrize("stereotype", RESOURCE_STEREOTYPES)
def test_resource_stereotype_has_at_least_one_instance(driver, database, stereotype):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    n = _count(driver, database, stereotype)
    assert n > 0, (
        f"{stereotype} count is zero. See test_operational_stereotype_has_at_least_one_instance "
        "for the diagnostic checklist; same root causes apply on the resource side."
    )


# ── Relationship-stereotype map additions (#75 RC #3) ────────────────────────


def test_information_flow_edges_present(driver, database):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    n = _edge_count(driver, database, "INFORMATION_FLOW")
    assert n > 0, (
        "INFORMATION_FLOW edges count is zero. OperationalExchange and NeedLine "
        "stereotypes applied to UML InformationFlow elements should produce these "
        "edges post-fix."
    )


def test_no_empty_stereotype_nodes(driver, database):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    with driver.session(database=database) as session:
        record = session.run(
            "MATCH (n:UAFElement) WHERE n.stereotype IS NULL OR n.stereotype = '' "
            "RETURN count(n) AS c"
        ).single()
        n = record["c"] if record else 0
    assert n == 0, (
        f"{n} :UAFElement node(s) have a null or empty stereotype. This indicates "
        "a regression in the DTO builder — every traversed element should carry "
        "the stereotype name that resolved it."
    )
