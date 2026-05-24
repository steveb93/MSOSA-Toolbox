"""Unit tests for ontology/codegen/sparql_to_graphml.py.

Covers:
  - rdf_to_graphml emits well-formed XML in the GraphML namespace
  - Literal RDF facts fold into node attributes (label, uaf_domain, ...)
  - Resource→resource triples become directed edges with predicate-named labels
  - rdf:type folds into a "rdf_type" attribute (comma-separated if multi-typed)
  - rdf_to_cyjs emits valid JSON with the same nodes/edges
  - The preset queries load and parse as SPARQL

No live Fuseki or Neo4j needed.

Run:
    pytest Test/test_graphml_export.py -v
"""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import pytest

rdflib = pytest.importorskip("rdflib")

from rdflib import Graph  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[1]
QUERIES_DIR = REPO_ROOT / "ontology" / "visualisations" / "queries"

sys.path.insert(0, str(REPO_ROOT / "ontology" / "codegen"))
import sparql_to_graphml  # noqa: E402  (after sys.path tweak — matches test_rdf_parity convention)

GRAPHML_NS = "{http://graphml.graphdrawing.org/xmlns}"

SAMPLE_TTL = """
@prefix uaf:     <http://msosa-toolbox.local/uaf#> .
@prefix uafinst: <http://msosa-toolbox.local/uaf/instance#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .

uafinst:cap1 a uaf:Capability ;
    rdfs:label "Cap1" ;
    uaf:domain "STRATEGIC" ;
    uaf:exhibits uafinst:e1 ;
    uaf:realisedBy uafinst:cfg1 .

uafinst:cfg1 a uaf:CapabilityConfiguration ;
    rdfs:label "Cfg1" ;
    uaf:domain "STRATEGIC" .

uafinst:e1 a uaf:DesiredEffect ;
    rdfs:label "Effect1" ;
    uaf:domain "STRATEGIC" .
"""


@pytest.fixture(scope="module")
def sample_graph() -> Graph:
    g = Graph()
    g.parse(data=SAMPLE_TTL, format="turtle")
    return g


@pytest.fixture(scope="module")
def graphml(sample_graph) -> ET.Element:
    from sparql_to_graphml import rdf_to_graphml
    xml_text = rdf_to_graphml(sample_graph)
    return ET.fromstring(xml_text)


def _nodes(root: ET.Element) -> list[ET.Element]:
    return list(root.iter(f"{GRAPHML_NS}node"))


def _edges(root: ET.Element) -> list[ET.Element]:
    return list(root.iter(f"{GRAPHML_NS}edge"))


def _data_for(el: ET.Element, key: str) -> str | None:
    for d in el.findall(f"{GRAPHML_NS}data"):
        if d.attrib.get("key") == key:
            return d.text
    return None


def test_graphml_root_is_graphml_namespace(graphml):
    assert graphml.tag == f"{GRAPHML_NS}graphml"


def test_three_nodes_emitted(graphml):
    # cap1, cfg1, e1 are subjects of literal facts → ensured nodes.
    # The rdf:type objects (Capability / CapabilityConfiguration / DesiredEffect)
    # are folded into the rdf_type attribute, not separate nodes.
    ids = {n.attrib["id"] for n in _nodes(graphml)}
    assert any(i.endswith("cap1") for i in ids)
    assert any(i.endswith("cfg1") for i in ids)
    assert any(i.endswith("e1") for i in ids)


def test_label_folded_into_attribute(graphml):
    cap_node = next(n for n in _nodes(graphml) if n.attrib["id"].endswith("cap1"))
    assert _data_for(cap_node, "n_label") == "Cap1"


def test_uaf_domain_folded_into_attribute(graphml):
    cap_node = next(n for n in _nodes(graphml) if n.attrib["id"].endswith("cap1"))
    assert _data_for(cap_node, "n_uaf_domain") == "STRATEGIC"


def test_rdf_type_folded_into_attribute(graphml):
    cap_node = next(n for n in _nodes(graphml) if n.attrib["id"].endswith("cap1"))
    rdf_type = _data_for(cap_node, "n_rdf_type")
    assert rdf_type and "Capability" in rdf_type


def test_exhibits_edge_emitted(graphml):
    exhibits_edges = [e for e in _edges(graphml)
                      if _data_for(e, "e_label") == "exhibits"]
    assert len(exhibits_edges) == 1
    src = exhibits_edges[0].attrib["source"]
    tgt = exhibits_edges[0].attrib["target"]
    assert src.endswith("cap1") and tgt.endswith("e1")


def test_realisedBy_edge_emitted(graphml):
    r_edges = [e for e in _edges(graphml)
               if _data_for(e, "e_label") == "realisedBy"]
    assert len(r_edges) == 1


def test_no_edges_for_literal_objects(graphml):
    # rdfs:label and uaf:domain shouldn't appear as edges — they fold into
    # node attributes.
    edge_labels = {_data_for(e, "e_label") for e in _edges(graphml)}
    assert "label" not in edge_labels
    assert "domain" not in edge_labels


def test_cyjs_output_is_valid_json(sample_graph):
    from sparql_to_graphml import rdf_to_cyjs
    payload = json.loads(rdf_to_cyjs(sample_graph))
    assert "elements" in payload
    assert isinstance(payload["elements"]["nodes"], list)
    assert isinstance(payload["elements"]["edges"], list)
    assert len(payload["elements"]["nodes"]) >= 3
    edge_labels = {e["data"]["label"] for e in payload["elements"]["edges"]}
    assert "exhibits" in edge_labels
    assert "realisedBy" in edge_labels


@pytest.mark.parametrize("preset", [
    "capability-realisation",
    "security-controls",
    "operational-flow",
    "resource-allocation",
])
def test_preset_queries_parse(preset):
    """Each shipped preset must be syntactically valid SPARQL CONSTRUCT."""
    from rdflib.plugins.sparql import prepareQuery
    path = QUERIES_DIR / f"{preset}.sparql"
    assert path.is_file(), f"Missing preset: {path}"
    q = path.read_text(encoding="utf-8")
    parsed = prepareQuery(q)
    assert parsed.algebra.name == "ConstructQuery"


def test_local_mode_runs_against_fixture(tmp_path):
    """The --from-file path: write a TTL fixture, run CONSTRUCT, get a Graph."""
    from sparql_to_graphml import _run_construct_local

    fixture = tmp_path / "fix.ttl"
    fixture.write_text(SAMPLE_TTL, encoding="utf-8")

    query = """
    PREFIX uaf: <http://msosa-toolbox.local/uaf#>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    CONSTRUCT { ?c rdfs:label ?l } WHERE { ?c a uaf:Capability ; rdfs:label ?l }
    """
    result = _run_construct_local(query, fixture)
    assert sum(1 for _ in result) == 1
