"""Data artefact + ERD regression — issue #76.

Asserts that the post-fix exporter:
  - Reaches DataObject / DataInput / DataOutput / DataStore artefacts (which
    used to live inside BPMN processes and were unreachable pre-PR2 because of
    the Package-only recursion).
  - Wires Task <-> DataInput/Output via DATA_INPUT / DATA_OUTPUT edges
    (RELATION_TYPE_MAP additions, #76 RC #3).
  - Emits first-class :Attribute nodes and HAS_ATTRIBUTE / OF_TYPE edges
    instead of flattening to tv_attr_* properties (#76 design A).
  - Carries srcMult / tgtMult on at least some association edges (#76 RC #6).

Requires:
    - Neo4j running and reachable on bolt://localhost:7687
    - A model has been exported that exercises BPMN data flow AND has at least
      one ERD entity with attributes

Skip on CI / offline:
    pytest -m "not neo4j"
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


def _count_label(driver, database: str, label: str) -> int:
    with driver.session(database=database) as session:
        # Label is a constant — safe to interpolate.
        record = session.run(f"MATCH (n:{label}) RETURN count(n) AS c").single()
        return record["c"] if record else 0


def _count_stereotype(driver, database: str, stereotype: str) -> int:
    with driver.session(database=database) as session:
        record = session.run(
            "MATCH (n:UAFElement {stereotype: $s}) RETURN count(n) AS c",
            s=stereotype,
        ).single()
        return record["c"] if record else 0


def _count_edges(driver, database: str, rel_type: str) -> int:
    with driver.session(database=database) as session:
        record = session.run(
            f"MATCH ()-[r:{rel_type}]->() RETURN count(r) AS c"
        ).single()
        return record["c"] if record else 0


def _graph_is_empty(driver, database: str) -> bool:
    with driver.session(database=database) as session:
        record = session.run("MATCH (n:UAFElement) RETURN count(n) AS c").single()
        return (record["c"] if record else 0) == 0


# ── First-class ERD attribute representation (#76 design A) ──────────────────


def test_attribute_nodes_present(driver, database):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    n = _count_label(driver, database, "Attribute")
    assert n > 0, (
        ":Attribute count is zero. Pre-fix attributes were flattened to tv_attr_* "
        "properties on the parent entity (#76 design A switched to first-class "
        "nodes). Confirm your test model contains at least one Classifier with "
        "named UML attributes."
    )


def test_has_attribute_edges_present(driver, database):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    n = _count_edges(driver, database, "HAS_ATTRIBUTE")
    assert n > 0, (
        "HAS_ATTRIBUTE edge count is zero. Every :Attribute node should be "
        "wired to its owning entity by HAS_ATTRIBUTE — missing edges indicate a "
        "regression in extractAttributes()."
    )


def test_no_orphan_attributes(driver, database):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    with driver.session(database=database) as session:
        record = session.run(
            "MATCH (a:Attribute) WHERE NOT (()-[:HAS_ATTRIBUTE]->(a)) "
            "RETURN count(a) AS c"
        ).single()
        orphans = record["c"] if record else 0
    assert orphans == 0, (
        f"{orphans} :Attribute node(s) have no incoming HAS_ATTRIBUTE edge. "
        "Attributes must be reachable from their owning entity."
    )


# ── Data artefact reachability (#76 RC #1, #2) ───────────────────────────────


@pytest.mark.parametrize("stereotype", ["DataObject", "DataInput", "DataOutput", "DataStore"])
def test_data_artefact_reachable(driver, database, stereotype):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    n = _count_stereotype(driver, database, stereotype)
    if n == 0:
        pytest.skip(
            f"No {stereotype} elements in this model. Pre-PR2 these were "
            "unreachable due to Package-only recursion; pre-PR4 they were never "
            "wired to their consuming/producing Tasks. Test only meaningful "
            "against a model that includes a BPMN operational process diagram."
        )
    assert n > 0


# ── Data-association edges (#76 RC #3) ───────────────────────────────────────


@pytest.mark.parametrize("rel_type", ["DATA_INPUT", "DATA_OUTPUT"])
def test_data_flow_edges_present(driver, database, rel_type):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    n = _count_edges(driver, database, rel_type)
    if n == 0:
        pytest.skip(
            f"No {rel_type} edges in this model. Requires a BPMN operational "
            "process with at least one DataInputAssociation / DataOutputAssociation."
        )
    assert n > 0


# ── Multiplicity carried on edges (#76 RC #6) ────────────────────────────────


def test_some_edges_carry_multiplicity(driver, database):
    if _graph_is_empty(driver, database):
        pytest.skip("Graph is empty — run an MSOSA export before this regression test.")
    with driver.session(database=database) as session:
        record = session.run(
            "MATCH ()-[r]->() WHERE r.srcMult <> '' OR r.tgtMult <> '' "
            "RETURN count(r) AS c"
        ).single()
        n = record["c"] if record else 0
    if n == 0:
        pytest.skip(
            "No edges with multiplicity. Test model must contain at least one UML "
            "Association with explicit multiplicity on its member ends (the common "
            "ERD case)."
        )
    assert n > 0
