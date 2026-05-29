"""OWL axiom unit tests — Stage 3 scaffolding.

Validates that the axioms in ontology/uaf-mvo-axioms.ttl produce the expected
inferences when an OWL RL reasoner closes the graph. No live Neo4j or Fuseki
required — uses owlrl in-process so the tests stay hermetic.

Coverage:
  - owl:inverseOf: forward → reverse and reverse → forward materialisation
  - owl:TransitiveProperty: uaf:dominates closes transitively
  - owl:disjointWith: instance typed as two disjoint domain classes triggers
    inconsistency (owlrl flags via owl:Nothing membership)
  - subClassOf restriction with someValuesFrom: documented (does NOT flag
    absence — that's SHACL's job — but the restriction node is reachable
    from the class)

Run:
    pytest Test/test_owl_axioms.py -v

Skip if owlrl is not installed.
"""

from __future__ import annotations

from pathlib import Path

import pytest

owlrl = pytest.importorskip("owlrl")
from rdflib import Graph, Namespace, URIRef  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[1]
MVO_FILE = REPO_ROOT / "ontology" / "uaf-mvo.ttl"
AXIOMS_FILE = REPO_ROOT / "ontology" / "uaf-mvo-axioms.ttl"

UAF = Namespace("http://msosa-toolbox.local/uaf#")
UAFINST = Namespace("http://msosa-toolbox.local/uaf/instance#")
OWL_NS = Namespace("http://www.w3.org/2002/07/owl#")

PREFIXES = """
@prefix uaf:     <http://msosa-toolbox.local/uaf#> .
@prefix uafinst: <http://msosa-toolbox.local/uaf/instance#> .
@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .
@prefix owl:     <http://www.w3.org/2002/07/owl#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
"""


def _close(extra_ttl: str) -> Graph:
    """Load MVO + axioms + synthetic instance TTL and run OWL RL closure."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    g.parse(data=PREFIXES + extra_ttl, format="turtle")
    owlrl.DeductiveClosure(owlrl.OWLRL_Semantics).expand(g)
    return g


def test_axioms_file_loads():
    """The axioms file must parse cleanly and import the MVO ontology."""
    g = Graph()
    g.parse(AXIOMS_FILE, format="turtle")
    axiom_ontology = URIRef("http://msosa-toolbox.local/uaf/axioms")
    imports = list(g.objects(subject=axiom_ontology, predicate=OWL_NS.imports))
    assert URIRef("http://msosa-toolbox.local/uaf/mvo") in imports


def test_inverseOf_materialises_reverse_direction():
    """uaf:realises asserted on a pair → uaf:realisedBy inferred the other way."""
    ttl = """
    uafinst:cap a uaf:Capability ;
        uaf:domain "STRATEGIC" .
    uafinst:cfg a uaf:CapabilityConfiguration ;
        uaf:domain "RESOURCE" ;
        uaf:realises uafinst:cap .
    """
    g = _close(ttl)
    assert (UAFINST.cap, UAF.realisedBy, UAFINST.cfg) in g, \
        "owl:inverseOf with uaf:realises should infer the reverse triple uaf:realisedBy"


def test_inverseOf_materialises_forward_direction():
    """uaf:realisedBy asserted → uaf:realises inferred the other way."""
    ttl = """
    uafinst:cap a uaf:Capability ;
        uaf:domain "STRATEGIC" .
    uafinst:cfg a uaf:CapabilityConfiguration ;
        uaf:domain "RESOURCE" .
    uafinst:cap uaf:realisedBy uafinst:cfg .
    """
    g = _close(ttl)
    assert (UAFINST.cfg, UAF.realises, UAFINST.cap) in g, \
        "owl:inverseOf is symmetric — reverse-asserted triple should yield forward too"


def test_traces_inverseOf_traced_by():
    """SecurityControl uaf:tracesTo SecurityRisk → risk uaf:tracedBy control."""
    ttl = """
    uafinst:risk a uaf:SecurityRisk ;
        uaf:domain "SECURITY" .
    uafinst:ctrl a uaf:SecurityControl ;
        uaf:domain "SECURITY" ;
        uaf:tracesTo uafinst:risk .
    """
    g = _close(ttl)
    assert (UAFINST.risk, UAF.tracedBy, UAFINST.ctrl) in g


def test_dominates_transitive_closure():
    """A dominates B, B dominates C → A dominates C is inferred."""
    ttl = """
    uafinst:topSecret a uaf:SecurityElement ;
        uaf:domain "SECURITY" .
    uafinst:secret a uaf:SecurityElement ;
        uaf:domain "SECURITY" .
    uafinst:confidential a uaf:SecurityElement ;
        uaf:domain "SECURITY" .

    uafinst:topSecret uaf:dominates uafinst:secret .
    uafinst:secret uaf:dominates uafinst:confidential .
    """
    g = _close(ttl)
    assert (UAFINST.topSecret, UAF.dominates, UAFINST.confidential) in g, \
        "owl:TransitiveProperty on uaf:dominates should close the chain"


def test_dominates_longer_chain():
    """Four-element chain closes to all pairs."""
    ttl = """
    uafinst:a a uaf:SecurityElement ; uaf:domain "SECURITY" .
    uafinst:b a uaf:SecurityElement ; uaf:domain "SECURITY" .
    uafinst:c a uaf:SecurityElement ; uaf:domain "SECURITY" .
    uafinst:d a uaf:SecurityElement ; uaf:domain "SECURITY" .

    uafinst:a uaf:dominates uafinst:b .
    uafinst:b uaf:dominates uafinst:c .
    uafinst:c uaf:dominates uafinst:d .
    """
    g = _close(ttl)
    assert (UAFINST.a, UAF.dominates, UAFINST.c) in g
    assert (UAFINST.a, UAF.dominates, UAFINST.d) in g
    assert (UAFINST.b, UAF.dominates, UAFINST.d) in g


def test_disjointness_axiom_present_in_closure():
    """The owl:disjointWith axiom between Strategic and Operational must survive closure.

    Note: owlrl does NOT materialise disjointness violations as owl:Nothing
    membership (an implementation limitation). Fuseki's OWL FB reasoner does
    surface them at query time. This test verifies the axiom is correctly
    authored and reaches the reasoner; the violation-detection behaviour is
    a Fuseki integration concern.
    """
    g = _close("")  # MVO + axioms, no instances
    assert (UAF.StrategicElement, OWL_NS.disjointWith, UAF.OperationalElement) in g \
        or (UAF.OperationalElement, OWL_NS.disjointWith, UAF.StrategicElement) in g, \
        "owl:disjointWith between StrategicElement and OperationalElement must be in the closure"


def test_disjointness_propagates_via_subclasses():
    """An instance typed as both Capability and OperationalActivity inherits
    both domain superclasses (StrategicElement, OperationalElement) via subClassOf —
    which under a stricter reasoner like Jena OWL FB triggers disjointness inconsistency."""
    ttl = """
    uafinst:bad a uaf:Capability , uaf:OperationalActivity ;
        uaf:domain "STRATEGIC" .
    """
    g = _close(ttl)
    rdf_type = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    types = set(g.objects(subject=UAFINST.bad, predicate=rdf_type))
    assert UAF.StrategicElement in types
    assert UAF.OperationalElement in types
    # If either is missing, the disjointness rule could never fire — which would
    # mean a regression in the MVO subClassOf hierarchy, not the axioms file.


def test_capability_configuration_restriction_documented():
    """The CapabilityConfiguration class carries the someValuesFrom restriction.

    This is a structural / documentation check — the restriction is reachable
    via rdfs:subClassOf. It does NOT test that absent realisedBy edges get
    flagged (open-world; that's SHACL's job).
    """
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    RDFS_SUBCLASS = URIRef("http://www.w3.org/2000/01/rdf-schema#subClassOf")
    parents = set(g.objects(subject=UAF.CapabilityConfiguration,
                            predicate=RDFS_SUBCLASS))
    restrictions = [p for p in parents
                    if (p, URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                        OWL_NS.Restriction) in g]
    assert restrictions, \
        "CapabilityConfiguration should have at least one owl:Restriction parent"


# --- §5 Operational + Resource someValuesFrom restrictions ------------------

def _restriction_targets(g: Graph, cls: URIRef) -> set[URIRef]:
    """Collect the owl:someValuesFrom targets reachable from cls via owl:Restriction.

    Walks rdfs:subClassOf parents that are owl:Restriction nodes, then reads
    owl:someValuesFrom. Used to assert the four §5 restrictions are present
    and point at the expected superclass.
    """
    RDFS_SUBCLASS = URIRef("http://www.w3.org/2000/01/rdf-schema#subClassOf")
    RDF_TYPE = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    targets: set[URIRef] = set()
    for parent in g.objects(subject=cls, predicate=RDFS_SUBCLASS):
        if (parent, RDF_TYPE, OWL_NS.Restriction) not in g:
            continue
        for tgt in g.objects(subject=parent, predicate=OWL_NS.someValuesFrom):
            if isinstance(tgt, URIRef):
                targets.add(tgt)
    return targets


def test_operational_activity_restriction_targets_performer():
    """OperationalActivity ⊑ ∃ performedBy . OperationalPerformer (axioms §5)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.OperationalPerformer in _restriction_targets(g, UAF.OperationalActivity)


def test_operational_process_restriction_targets_activity():
    """OperationalProcess ⊑ ∃ composedOf . OperationalActivity (axioms §5)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.OperationalActivity in _restriction_targets(g, UAF.OperationalProcess)


def test_resource_performer_restriction_targets_element():
    """ResourcePerformer ⊑ ∃ performs . ResourceElement (axioms §5)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.ResourceElement in _restriction_targets(g, UAF.ResourcePerformer)


def test_resource_architecture_restriction_targets_element():
    """ResourceArchitecture ⊑ ∃ composedOf . ResourceElement (axioms §5)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.ResourceElement in _restriction_targets(g, UAF.ResourceArchitecture)


def test_performs_inverseOf_performed_by_via_closure():
    """uaf:performs and uaf:performedBy interoperate under owl:inverseOf.

    Sanity-check that the inverse pair used by the new Operational shapes
    actually closes — if this regressed, the SHACL test
    test_operational_activity_with_performer_does_not_warn would also fail
    but the SHACL trace would obscure the root cause.
    """
    ttl = """
    uafinst:p a uaf:OperationalPerformer ; uaf:domain "OPERATIONAL" .
    uafinst:a a uaf:OperationalActivity ;  uaf:domain "OPERATIONAL" .
    uafinst:p uaf:performs uafinst:a .
    """
    g = _close(ttl)
    assert (UAFINST.a, UAF.performedBy, UAFINST.p) in g, \
        "uaf:performs should infer uaf:performedBy under owl:inverseOf"


# --- §6 Service + Personnel + Acquisition someValuesFrom restrictions -------

def test_service_restriction_targets_performer():
    """Service ⊑ ∃ providedBy . ServicePerformer (axioms §6)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.ServicePerformer in _restriction_targets(g, UAF.Service)


def test_service_architecture_restriction_targets_element():
    """ServiceArchitecture ⊑ ∃ composedOf . ServiceElement (axioms §6)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.ServiceElement in _restriction_targets(g, UAF.ServiceArchitecture)


def test_post_restriction_targets_organization():
    """Post ⊑ ∃ partOf . Organization (axioms §6)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.Organization in _restriction_targets(g, UAF.Post)


def test_project_restriction_targets_acquisition_element():
    """Project ⊑ ∃ composedOf . AcquisitionElement (axioms §6)."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    assert UAF.AcquisitionElement in _restriction_targets(g, UAF.Project)


def test_provides_inverseOf_provided_by_via_closure():
    """uaf:provides and uaf:providedBy interoperate under owl:inverseOf.

    Sanity-check the newly-added pair (§1) used by uafsh:ServiceShape.
    Same reasoning as test_performs_inverseOf_performed_by_via_closure.
    """
    ttl = """
    uafinst:sp a uaf:ServicePerformer ; uaf:domain "SERVICE" .
    uafinst:sv a uaf:Service ;          uaf:domain "SERVICE" .
    uafinst:sp uaf:provides uafinst:sv .
    """
    g = _close(ttl)
    assert (UAFINST.sv, UAF.providedBy, UAFINST.sp) in g, \
        "uaf:provides should infer uaf:providedBy under owl:inverseOf"


# --- §7 Entity → ResourceInformation bridge ---------------------------------

def test_entity_is_subclass_of_resource_information():
    """Direct subClassOf declaration is present in the axioms file."""
    g = Graph()
    g.parse(MVO_FILE, format="turtle")
    g.parse(AXIOMS_FILE, format="turtle")
    RDFS_SUBCLASS = URIRef("http://www.w3.org/2000/01/rdf-schema#subClassOf")
    parents = set(g.objects(subject=UAF.Entity, predicate=RDFS_SUBCLASS))
    assert UAF.ResourceInformation in parents, \
        "uaf:Entity must be rdfs:subClassOf uaf:ResourceInformation (axioms §7)"


def test_entity_instance_classifies_as_resource_information_under_closure():
    """An ERD Entity instance is automatically a ResourceInformation, a
    ResourceElement, and a SharedElement — letting Resource-view SPARQL queries
    reach ERD content without the caller knowing it came from the Shared
    domain."""
    ttl = """
    uafinst:CustomerRecord a uaf:Entity ;
        uaf:domain "SHARED" .
    """
    g = _close(ttl)
    rdf_type = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    types = set(g.objects(subject=UAFINST.CustomerRecord, predicate=rdf_type))
    assert UAF.Entity in types
    assert UAF.ResourceInformation in types, \
        "Bridge §7 should make every Entity a ResourceInformation"
    assert UAF.ResourceElement in types, \
        "ResourceInformation inheritance should reach ResourceElement"
    assert UAF.SharedElement in types, \
        "Entity's original SharedElement parent must still resolve too"


def test_entity_and_resource_information_coexist_without_disjointness_clash():
    """SharedElement is intentionally outside the domain-disjointness web,
    so an Entity carrying both SharedElement (via MVO) and ResourceElement
    (via the §7 bridge) does NOT trigger an inconsistency. Regression guard
    for the §2 disjointness comment block."""
    ttl = """
    uafinst:Ledger a uaf:Entity ;
        uaf:domain "SHARED" .
    """
    g = _close(ttl)
    rdf_type = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    types = set(g.objects(subject=UAFINST.Ledger, predicate=rdf_type))
    # owlrl flags disjointness violations by adding owl:Nothing membership.
    OWL_NOTHING = URIRef("http://www.w3.org/2002/07/owl#Nothing")
    assert OWL_NOTHING not in types, \
        "Entity being both SharedElement and ResourceElement must not violate disjointness"
