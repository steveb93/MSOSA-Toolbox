"""UML Data Marking ontology unit tests.

Validates the classification lattice in ontology/uml-data-marking.ttl:
  - dm:dominates closes transitively under OWL RL
  - each DoD level individual has a skos:notation matching the canonical
    string the DoD Data Marking Plugin emits as the value of
    securityClassification (so the JOIN pattern in
    ontology/queries/data-marking-examples.sparql works)
  - entities carrying uaftv:securityClassification literals can be linked to
    the matching level individual via the JOIN pattern

No live Fuseki required — uses owlrl in-process so the tests stay hermetic.

Run:
    pytest Test/test_data_marking.py -v

Skip if owlrl is not installed.
"""

from __future__ import annotations

from pathlib import Path

import pytest

owlrl = pytest.importorskip("owlrl")
from rdflib import Graph, Literal, Namespace, URIRef  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[1]
DM_FILE = REPO_ROOT / "ontology" / "uml-data-marking.ttl"

DM = Namespace("http://msosa-toolbox.local/datamarking#")
UAFTV = Namespace("http://msosa-toolbox.local/uaf/tag#")
UAFINST = Namespace("http://msosa-toolbox.local/uaf/instance#")
SKOS = Namespace("http://www.w3.org/2004/02/skos/core#")
OWL_NS = Namespace("http://www.w3.org/2002/07/owl#")
RDFS_NS = Namespace("http://www.w3.org/2000/01/rdf-schema#")

PREFIXES = """
@prefix dm:      <http://msosa-toolbox.local/datamarking#> .
@prefix uaftv:   <http://msosa-toolbox.local/uaf/tag#> .
@prefix uafinst: <http://msosa-toolbox.local/uaf/instance#> .
@prefix rdf:     <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .
@prefix skos:    <http://www.w3.org/2004/02/skos/core#> .
@prefix owl:     <http://www.w3.org/2002/07/owl#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
"""


def _close(extra_ttl: str = "") -> Graph:
    """Load the data-marking slice plus optional instance TTL; run OWL RL closure."""
    g = Graph()
    g.parse(DM_FILE, format="turtle")
    if extra_ttl:
        g.parse(data=PREFIXES + extra_ttl, format="turtle")
    owlrl.DeductiveClosure(owlrl.OWLRL_Semantics).expand(g)
    return g


# --- File hygiene -----------------------------------------------------------

def test_data_marking_file_loads():
    """The TTL must parse cleanly and declare the dm: ontology."""
    g = Graph()
    g.parse(DM_FILE, format="turtle")
    ont = URIRef("http://msosa-toolbox.local/datamarking")
    assert (ont, URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            OWL_NS.Ontology) in g


def test_four_dod_levels_present():
    """All four DoD classification levels are declared as dm:ClassificationLevel."""
    g = Graph()
    g.parse(DM_FILE, format="turtle")
    rdf_type = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    levels = set(g.subjects(predicate=rdf_type, object=DM.ClassificationLevel))
    assert DM.TopSecret in levels
    assert DM.Secret in levels
    assert DM.Confidential in levels
    assert DM.Unclassified in levels


def test_canonical_notations_are_uppercase_dod_strings():
    """skos:notation values match what the DoD Data Marking Plugin emits as
    the value of securityClassification — so the JOIN against
    tv_securityClassification literals works without further normalisation."""
    g = Graph()
    g.parse(DM_FILE, format="turtle")
    expected = {
        DM.TopSecret:    "TOP SECRET",
        DM.Secret:       "SECRET",
        DM.Confidential: "CONFIDENTIAL",
        DM.Unclassified: "UNCLASSIFIED",
    }
    for level, notation in expected.items():
        notations = set(g.objects(subject=level, predicate=SKOS.notation))
        assert Literal(notation) in notations, (
            f"{level} should carry skos:notation '{notation}'"
        )


# --- Lattice closure --------------------------------------------------------

def test_dominates_is_transitive_property():
    """dm:dominates is declared owl:TransitiveProperty so OWL FB closes the chain."""
    g = Graph()
    g.parse(DM_FILE, format="turtle")
    rdf_type = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    assert (DM.dominates, rdf_type, OWL_NS.TransitiveProperty) in g


def test_transitive_closure_top_secret_to_unclassified():
    """TS d S, S d C, C d U → TS dominates U (and every intermediate pair)."""
    g = _close()
    assert (DM.TopSecret, DM.dominates, DM.Confidential) in g, \
        "OWL FB should infer TopSecret dominates Confidential transitively"
    assert (DM.TopSecret, DM.dominates, DM.Unclassified) in g, \
        "OWL FB should infer TopSecret dominates Unclassified transitively"
    assert (DM.Secret, DM.dominates, DM.Unclassified) in g, \
        "OWL FB should infer Secret dominates Unclassified transitively"


def test_lattice_is_not_reflexive():
    """A level does not dominate itself — dominance is strict, not reflexive."""
    g = _close()
    # TransitiveProperty does NOT add reflexive triples, so this should hold.
    assert (DM.Secret, DM.dominates, DM.Secret) not in g


def test_lattice_is_not_symmetric():
    """U does NOT dominate TS — the lattice is directional."""
    g = _close()
    assert (DM.Unclassified, DM.dominates, DM.TopSecret) not in g
    assert (DM.Confidential, DM.dominates, DM.Secret) not in g


# --- JOIN pattern (the use case the slice exists for) ----------------------

def test_entity_marking_joins_to_lattice_individual():
    """An ERD entity carrying tv_securityClassification = 'SECRET' can be
    JOINed to dm:Secret via skos:notation. This is the primary query
    pattern data-marking-examples.sparql is built around."""
    ttl = """
    uafinst:CustomerRecord a uafinst:Entity ;
        rdfs:label "CustomerRecord" ;
        uaftv:securityClassification "SECRET" .
    """
    g = _close(ttl)
    # The JOIN we expect downstream SPARQL to perform:
    #   ?entity uaftv:securityClassification ?marking .
    #   ?level skos:notation ?marking .
    #   ?level a dm:ClassificationLevel .
    # We simulate it with rdflib triples.
    marking = next(g.objects(UAFINST.CustomerRecord, UAFTV.securityClassification))
    assert str(marking) == "SECRET"
    levels = list(g.subjects(predicate=SKOS.notation, object=marking))
    assert DM.Secret in levels, \
        "skos:notation 'SECRET' on dm:Secret must drive the entity→level JOIN"


def test_join_then_dominance_filter():
    """Joining to dm:Confidential then asking 'dominated by TS?' should be
    true under closure — the full end-to-end pattern.

    Modelled as: entity has marking 'CONFIDENTIAL'; we resolve to
    dm:Confidential; we ask whether dm:TopSecret dominates that level."""
    ttl = """
    uafinst:Ledger a uafinst:Entity ;
        uaftv:securityClassification "CONFIDENTIAL" .
    """
    g = _close(ttl)
    marking = next(g.objects(UAFINST.Ledger, UAFTV.securityClassification))
    level = next(g.subjects(predicate=SKOS.notation, object=marking))
    assert (DM.TopSecret, DM.dominates, level) in g, \
        "End-to-end: TS should dominate the resolved level for any sub-TS marking"


def test_unrecognised_marking_yields_no_level_match():
    """An entity carrying a non-DoD marking string finds no matching level —
    this is how SPARQL query #5 in data-marking-examples surfaces typos."""
    ttl = """
    uafinst:Strange a uafinst:Entity ;
        uaftv:securityClassification "MIDNIGHT BLUE" .
    """
    g = _close(ttl)
    marking = next(g.objects(UAFINST.Strange, UAFTV.securityClassification))
    levels = list(g.subjects(predicate=SKOS.notation, object=marking))
    assert levels == [], \
        "An unrecognised marking must not match any dm:ClassificationLevel"
