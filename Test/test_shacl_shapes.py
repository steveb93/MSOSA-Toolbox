"""SHACL shape unit tests — Stage 3 scaffolding.

Validates the shapes in ontology/shapes/uaf-shapes.ttl against tiny synthetic
instance graphs. No live Neo4j or Fuseki required.

Run:
    pytest Test/test_shacl_shapes.py -v

Skip if pyshacl is not installed.
"""

from __future__ import annotations

from pathlib import Path

import pytest

pyshacl = pytest.importorskip("pyshacl")
from rdflib import Graph  # noqa: E402  (after importorskip)

REPO_ROOT = Path(__file__).resolve().parents[1]
MVO_FILE = REPO_ROOT / "ontology" / "uaf-mvo.ttl"
SHAPES_FILE = REPO_ROOT / "ontology" / "shapes" / "uaf-shapes.ttl"

PREFIXES = """
@prefix uaf:     <http://msosa-toolbox.local/uaf#> .
@prefix uafinst: <http://msosa-toolbox.local/uaf/instance#> .
@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
"""


def _validate(extra_ttl: str) -> tuple[bool, list[dict]]:
    """Validate MVO + synthetic instance TTL against the shapes file."""
    from pyshacl import validate
    from rdflib import Namespace, URIRef

    data = Graph()
    data.parse(MVO_FILE, format="turtle")
    data.parse(data=PREFIXES + extra_ttl, format="turtle")

    shapes = Graph()
    shapes.parse(SHAPES_FILE, format="turtle")

    conforms, report, _ = validate(
        data_graph=data,
        shacl_graph=shapes,
        inference="rdfs",
        advanced=True,
        meta_shacl=False,
    )
    SH = Namespace("http://www.w3.org/ns/shacl#")
    RDF_TYPE = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    rows = []
    for vr in report.subjects(predicate=RDF_TYPE,
                              object=URIRef(f"{SH}ValidationResult")):
        rows.append({
            "focus": str(report.value(vr, SH.focusNode) or ""),
            "severity": str(report.value(vr, SH.resultSeverity) or "")
                            .rsplit("#", 1)[-1],
            "path": str(report.value(vr, SH.resultPath) or ""),
        })
    return conforms, rows


def test_mvo_alone_conforms():
    """The MVO with no instances must pass — no false positives on the T-Box."""
    conforms, rows = _validate("")
    assert conforms, f"MVO alone produced {len(rows)} unexpected violations: {rows}"


def test_well_formed_capability_passes():
    """A Capability with a label, domain, and exhibits an effect must not flag."""
    ttl = """
    uafinst:cap1 a uaf:Capability ;
        rdfs:label "Cap1" ;
        uaf:domain "STRATEGIC" ;
        uaf:exhibits uafinst:e1 .
    uafinst:e1 a uaf:DesiredEffect ;
        rdfs:label "E1" ;
        uaf:domain "STRATEGIC" .
    """
    conforms, rows = _validate(ttl)
    # Warnings allowed (other classes flagged) but no violations.
    assert conforms or all(r["severity"] != "Violation" for r in rows)
    cap_rows = [r for r in rows if r["focus"].endswith("cap1")]
    assert cap_rows == [], f"Well-formed capability flagged: {cap_rows}"


def test_vision_without_statement_violates():
    """Vision with no uaf:composedOf must produce a Violation (not a Warning)."""
    ttl = """
    uafinst:v1 a uaf:Vision ;
        rdfs:label "V1" ;
        uaf:domain "STRATEGIC" .
    """
    conforms, rows = _validate(ttl)
    assert not conforms
    violations = [r for r in rows
                  if r["focus"].endswith("v1") and r["severity"] == "Violation"]
    assert violations, f"Vision violation not raised. Got: {rows}"


def test_capability_configuration_warns_without_realiser():
    """CapabilityConfiguration with no incoming uaf:realises must warn."""
    ttl = """
    uafinst:cfg1 a uaf:CapabilityConfiguration ;
        rdfs:label "Cfg1" ;
        uaf:domain "STRATEGIC" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("cfg1") and r["severity"] == "Warning"]
    assert matches, f"CapabilityConfiguration warning not raised. Got: {rows}"


def test_security_risk_warns_without_traces():
    """SecurityRisk with no incoming uaf:tracesTo must warn (residual risk)."""
    ttl = """
    uafinst:risk1 a uaf:SecurityRisk ;
        rdfs:label "Risk1" ;
        uaf:domain "SECURITY" .
    """
    _, rows = _validate(ttl)
    risk_warns = [r for r in rows
                  if r["focus"].endswith("risk1") and r["severity"] == "Warning"]
    assert risk_warns, f"SecurityRisk warning not raised. Got: {rows}"


def test_security_risk_with_traces_does_not_warn():
    """SecurityRisk mitigated by a SecurityControl must not warn."""
    ttl = """
    uafinst:risk2 a uaf:SecurityRisk ;
        rdfs:label "Risk2" ;
        uaf:domain "SECURITY" .
    uafinst:ctrl2 a uaf:SecurityControl ;
        rdfs:label "Ctrl2" ;
        uaf:domain "SECURITY" ;
        uaf:tracesTo uafinst:risk2 ;
        uaf:containedIn uafinst:fam2 .
    uafinst:fam2 a uaf:SecurityControlFamily ;
        rdfs:label "Fam2" ;
        uaf:domain "SECURITY" .
    """
    _, rows = _validate(ttl)
    risk_rows = [r for r in rows if r["focus"].endswith("risk2")]
    assert risk_rows == [], f"Mitigated SecurityRisk should not flag: {risk_rows}"
