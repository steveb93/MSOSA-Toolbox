"""
Live SPARQL endpoint tests for the Fuseki overlay.

Requires:
    - Fuseki container running (docker compose -f docker-compose.yml -f docker-compose.fuseki.yml up -d)
    - ontology/codegen/dump_to_rdf.py executed at least once so uaf-instance.ttl exists
    - ontology/uaf-mvo.ttl present (codegen produces it from the seeded metamodel)

Skip in CI / offline:
    pytest -m "not neo4j"
"""

import os

import httpx
import pytest

pytestmark = pytest.mark.neo4j


SPARQL_URL = os.getenv("NEO4J_SPARQL_URL", "http://localhost:3030/uaf/sparql")
SPARQL_AUTH = (
    os.getenv("FUSEKI_USER", "admin"),
    os.getenv("FUSEKI_PASSWORD", "Password123"),
)


def _post(query: str) -> httpx.Response:
    response = httpx.post(
        SPARQL_URL,
        auth=SPARQL_AUTH,
        data={"query": query},
        headers={"Accept": "application/sparql-results+json"},
        timeout=30.0,
    )
    response.raise_for_status()
    return response


def test_sparql_endpoint_reachable():
    """The Fuseki SPARQL endpoint must respond to a trivial query."""
    bindings = _post("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 1").json()["results"]["bindings"]
    assert isinstance(bindings, list)


def test_uaf_capability_class_exists_in_tbox():
    """uaf-mvo.ttl must have been loaded into Fuseki — uaf:Capability should be an owl:Class."""
    query = (
        "PREFIX uaf: <http://msosa-toolbox.local/uaf#>\n"
        "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n"
        "ASK { uaf:Capability a owl:Class }"
    )
    assert _post(query).json().get("boolean") is True, (
        "uaf:Capability not declared as owl:Class. "
        "Did Fuseki load ontology/uaf-mvo.ttl? Regenerate via "
        "`python ontology/codegen/generate_mvo.py` then restart the fuseki container "
        "(or PUT the TTL via the plugin's Graph Store Protocol option)."
    )


def test_strategic_subsumption_resolves():
    """uaf:Capability rdfs:subClassOf+ uaf:StrategicElement must be derivable from the T-Box."""
    query = (
        "PREFIX uaf: <http://msosa-toolbox.local/uaf#>\n"
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
        "ASK { uaf:Capability rdfs:subClassOf+ uaf:StrategicElement }"
    )
    assert _post(query).json().get("boolean") is True
