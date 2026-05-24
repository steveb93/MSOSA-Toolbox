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
AXIOMS_FILE = REPO_ROOT / "ontology" / "uaf-mvo-axioms.ttl"
SHAPES_FILE = REPO_ROOT / "ontology" / "shapes" / "uaf-shapes.ttl"

PREFIXES = """
@prefix uaf:     <http://msosa-toolbox.local/uaf#> .
@prefix uafinst: <http://msosa-toolbox.local/uaf/instance#> .
@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
"""


def _validate(extra_ttl: str) -> tuple[bool, list[dict]]:
    """Validate MVO + axioms + synthetic instance TTL against the shapes file."""
    from pyshacl import validate
    from rdflib import Namespace, URIRef

    data = Graph()
    data.parse(MVO_FILE, format="turtle")
    data.parse(AXIOMS_FILE, format="turtle")
    data.parse(data=PREFIXES + extra_ttl, format="turtle")

    shapes = Graph()
    shapes.parse(SHAPES_FILE, format="turtle")

    conforms, report, _ = validate(
        data_graph=data,
        shacl_graph=shapes,
        inference="rdfsowlrl",
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


# --- Operational domain ------------------------------------------------------

def test_operational_activity_warns_without_performer():
    """OperationalActivity with no performer (forward or inverse) must warn."""
    ttl = """
    uafinst:act1 a uaf:OperationalActivity ;
        rdfs:label "Act1" ;
        uaf:domain "OPERATIONAL" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("act1") and r["severity"] == "Warning"]
    assert matches, f"OperationalActivity warning not raised. Got: {rows}"


def test_operational_activity_with_performer_does_not_warn():
    """OperationalActivity linked to a performer via uaf:performs must not flag."""
    ttl = """
    uafinst:act2 a uaf:OperationalActivity ;
        rdfs:label "Act2" ;
        uaf:domain "OPERATIONAL" .
    uafinst:perf2 a uaf:OperationalPerformer ;
        rdfs:label "Perf2" ;
        uaf:domain "OPERATIONAL" ;
        uaf:performs uafinst:act2 .
    """
    _, rows = _validate(ttl)
    act_rows = [r for r in rows if r["focus"].endswith("act2")]
    assert act_rows == [], f"Performed activity should not flag: {act_rows}"


def test_operational_performer_warns_without_activity():
    """OperationalPerformer with no performs link must warn."""
    ttl = """
    uafinst:perf3 a uaf:OperationalPerformer ;
        rdfs:label "Perf3" ;
        uaf:domain "OPERATIONAL" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("perf3") and r["severity"] == "Warning"]
    assert matches, f"OperationalPerformer warning not raised. Got: {rows}"


def test_operational_process_warns_without_activities():
    """OperationalProcess with no composedOf must warn."""
    ttl = """
    uafinst:proc1 a uaf:OperationalProcess ;
        rdfs:label "Proc1" ;
        uaf:domain "OPERATIONAL" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("proc1") and r["severity"] == "Warning"]
    assert matches, f"OperationalProcess warning not raised. Got: {rows}"


# --- Resource domain ---------------------------------------------------------

def test_resource_performer_warns_without_function():
    """ResourcePerformer with no performs link must warn."""
    ttl = """
    uafinst:rp1 a uaf:ResourcePerformer ;
        rdfs:label "RP1" ;
        uaf:domain "RESOURCE" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("rp1") and r["severity"] == "Warning"]
    assert matches, f"ResourcePerformer warning not raised. Got: {rows}"


def test_resource_role_warns_without_assignment():
    """ResourceRole not assignedTo a performer must warn."""
    ttl = """
    uafinst:role1 a uaf:ResourceRole ;
        rdfs:label "Role1" ;
        uaf:domain "RESOURCE" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("role1") and r["severity"] == "Warning"]
    assert matches, f"ResourceRole warning not raised. Got: {rows}"


def test_resource_role_with_assignment_does_not_warn():
    """ResourceRole assignedTo a ResourcePerformer must not flag."""
    ttl = """
    uafinst:role2 a uaf:ResourceRole ;
        rdfs:label "Role2" ;
        uaf:domain "RESOURCE" ;
        uaf:assignedTo uafinst:rp2 .
    uafinst:rp2 a uaf:ResourcePerformer ;
        rdfs:label "RP2" ;
        uaf:domain "RESOURCE" ;
        uaf:performs uafinst:fn2 .
    uafinst:fn2 a uaf:ResourceFunction ;
        rdfs:label "Fn2" ;
        uaf:domain "RESOURCE" .
    """
    _, rows = _validate(ttl)
    role_rows = [r for r in rows if r["focus"].endswith("role2")]
    assert role_rows == [], f"Assigned ResourceRole should not flag: {role_rows}"


def test_resource_architecture_warns_without_components():
    """ResourceArchitecture with no composedOf must warn."""
    ttl = """
    uafinst:arch1 a uaf:ResourceArchitecture ;
        rdfs:label "Arch1" ;
        uaf:domain "RESOURCE" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("arch1") and r["severity"] == "Warning"]
    assert matches, f"ResourceArchitecture warning not raised. Got: {rows}"


def test_resource_artifact_warns_without_allocation():
    """ResourceArtifact not allocatedTo anything must warn."""
    ttl = """
    uafinst:art1 a uaf:ResourceArtifact ;
        rdfs:label "Art1" ;
        uaf:domain "RESOURCE" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("art1") and r["severity"] == "Warning"]
    assert matches, f"ResourceArtifact warning not raised. Got: {rows}"


# --- Service domain ----------------------------------------------------------

def test_service_warns_without_provider():
    """Service with no provider (forward providedBy or reverse provides) must warn."""
    ttl = """
    uafinst:svc1 a uaf:Service ;
        rdfs:label "Svc1" ;
        uaf:domain "SERVICE" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("svc1") and r["severity"] == "Warning"]
    assert matches, f"Service warning not raised. Got: {rows}"


def test_service_with_provider_does_not_warn():
    """Service linked to a ServicePerformer via uaf:provides must not flag."""
    ttl = """
    uafinst:svc2 a uaf:Service ;
        rdfs:label "Svc2" ;
        uaf:domain "SERVICE" .
    uafinst:sp2 a uaf:ServicePerformer ;
        rdfs:label "SP2" ;
        uaf:domain "SERVICE" ;
        uaf:provides uafinst:svc2 .
    """
    _, rows = _validate(ttl)
    svc_rows = [r for r in rows if r["focus"].endswith("svc2")]
    assert svc_rows == [], f"Provided service should not flag: {svc_rows}"


def test_service_architecture_warns_without_components():
    """ServiceArchitecture with no composedOf must warn."""
    ttl = """
    uafinst:sarch1 a uaf:ServiceArchitecture ;
        rdfs:label "SArch1" ;
        uaf:domain "SERVICE" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("sarch1") and r["severity"] == "Warning"]
    assert matches, f"ServiceArchitecture warning not raised. Got: {rows}"


def test_service_role_warns_without_assignment():
    """ServiceRole not assignedTo a performer must warn."""
    ttl = """
    uafinst:srole1 a uaf:ServiceRole ;
        rdfs:label "SRole1" ;
        uaf:domain "SERVICE" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("srole1") and r["severity"] == "Warning"]
    assert matches, f"ServiceRole warning not raised. Got: {rows}"


# --- Personnel domain --------------------------------------------------------

def test_post_warns_without_organisation():
    """Post with no partOf Organization must warn."""
    ttl = """
    uafinst:post1 a uaf:Post ;
        rdfs:label "Post1" ;
        uaf:domain "PERSONNEL" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("post1") and r["severity"] == "Warning"]
    assert matches, f"Post warning not raised. Got: {rows}"


def test_post_with_organisation_does_not_warn():
    """Post linked to Organization via uaf:composedOf must not flag (inverse path)."""
    ttl = """
    uafinst:org2 a uaf:Organization ;
        rdfs:label "Org2" ;
        uaf:domain "PERSONNEL" ;
        uaf:composedOf uafinst:post2 .
    uafinst:post2 a uaf:Post ;
        rdfs:label "Post2" ;
        uaf:domain "PERSONNEL" .
    """
    _, rows = _validate(ttl)
    post_rows = [r for r in rows if r["focus"].endswith("post2")]
    assert post_rows == [], f"Owned Post should not flag: {post_rows}"


def test_organisation_warns_without_posts():
    """Organization with no composedOf must warn."""
    ttl = """
    uafinst:org1 a uaf:Organization ;
        rdfs:label "Org1" ;
        uaf:domain "PERSONNEL" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("org1") and r["severity"] == "Warning"]
    assert matches, f"Organization warning not raised. Got: {rows}"


def test_personnel_activity_warns_without_performer():
    """PersonnelActivity with no performer must warn."""
    ttl = """
    uafinst:pact1 a uaf:PersonnelActivity ;
        rdfs:label "PAct1" ;
        uaf:domain "PERSONNEL" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("pact1") and r["severity"] == "Warning"]
    assert matches, f"PersonnelActivity warning not raised. Got: {rows}"


# --- Acquisition domain ------------------------------------------------------

def test_project_warns_without_components():
    """Project with no composedOf must warn."""
    ttl = """
    uafinst:proj1 a uaf:Project ;
        rdfs:label "Proj1" ;
        uaf:domain "ACQUISITION" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("proj1") and r["severity"] == "Warning"]
    assert matches, f"Project warning not raised. Got: {rows}"


def test_project_milestone_warns_without_project():
    """ProjectMilestone not partOf any Project must warn."""
    ttl = """
    uafinst:mile1 a uaf:ProjectMilestone ;
        rdfs:label "Mile1" ;
        uaf:domain "ACQUISITION" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("mile1") and r["severity"] == "Warning"]
    assert matches, f"ProjectMilestone warning not raised. Got: {rows}"


def test_project_milestone_with_project_does_not_warn():
    """ProjectMilestone reachable from Project via composedOf must not flag."""
    ttl = """
    uafinst:proj2 a uaf:Project ;
        rdfs:label "Proj2" ;
        uaf:domain "ACQUISITION" ;
        uaf:composedOf uafinst:mile2 .
    uafinst:mile2 a uaf:ProjectMilestone ;
        rdfs:label "Mile2" ;
        uaf:domain "ACQUISITION" .
    """
    _, rows = _validate(ttl)
    mile_rows = [r for r in rows if r["focus"].endswith("mile2")]
    assert mile_rows == [], f"Project-owned milestone should not flag: {mile_rows}"


def test_project_theme_warns_without_projects():
    """ProjectTheme with no composedOf must warn."""
    ttl = """
    uafinst:theme1 a uaf:ProjectTheme ;
        rdfs:label "Theme1" ;
        uaf:domain "ACQUISITION" .
    """
    _, rows = _validate(ttl)
    matches = [r for r in rows
               if r["focus"].endswith("theme1") and r["severity"] == "Warning"]
    assert matches, f"ProjectTheme warning not raised. Got: {rows}"
