"""Python side of the RDF-emitter parity test (GitHub issue #73).

Exercises the IRI helpers in ``ontology/codegen/dump_to_rdf.py`` against the
shared fixture at ``ontology/codegen/parity-fixture.json``. The matching Java
test (RDFTripleBuilderParityTest) runs the same fixture against
``RDFTripleBuilder.java``. If either implementation drifts on namespaces,
sanitisation, or camelCase rules, one of the two tests fails.

These tests cover the IRI mechanics only — they do not require Neo4j or
Fuseki, and run as plain unit tests.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = REPO_ROOT / "ontology" / "codegen" / "parity-fixture.json"

# Make ontology/codegen importable without packaging the project.
sys.path.insert(0, str(REPO_ROOT / "ontology" / "codegen"))

import dump_to_rdf  # noqa: E402  (intentional late import after sys.path tweak)


@pytest.fixture(scope="module")
def fixture() -> dict:
    with FIXTURE_PATH.open(encoding="utf-8") as fh:
        return json.load(fh)


def _expand(curie: str, namespaces: dict[str, str]) -> str:
    prefix, _, local = curie.partition(":")
    if prefix not in namespaces:
        raise AssertionError(
            f"fixture references unknown prefix {prefix!r} in {curie!r}; "
            f"known prefixes: {sorted(namespaces)}"
        )
    return namespaces[prefix] + local


def test_namespaces_match_module_constants(fixture: dict) -> None:
    ns = fixture["namespaces"]
    assert str(dump_to_rdf.UAF)     == ns["uaf"]
    assert str(dump_to_rdf.SYSML)   == ns["sysml"]
    assert str(dump_to_rdf.BPMN)    == ns["bpmn"]
    assert str(dump_to_rdf.UAFINST) == ns["uafinst"]
    assert str(dump_to_rdf.UAFTV)   == ns["uaftv"]


def test_instance_iri_cases(fixture: dict) -> None:
    namespaces = fixture["namespaces"]
    for case in fixture["instance_iri"]:
        expected = _expand(case["expected_curie"], namespaces)
        actual = str(dump_to_rdf.instance_uri(case["id"]))
        assert actual == expected, (
            f"instance_uri({case['id']!r}): expected {expected!r}, got {actual!r} "
            f"({case.get('note', '')})"
        )


def test_class_iri_cases(fixture: dict) -> None:
    namespaces = fixture["namespaces"]
    for case in fixture["class_iri"]:
        expected = _expand(case["expected_curie"], namespaces)
        actual = str(dump_to_rdf.class_uri(case["label"], case["language"]))
        assert actual == expected, (
            f"class_uri({case['label']!r}, {case['language']!r}): "
            f"expected {expected!r}, got {actual!r} ({case.get('note', '')})"
        )


def test_predicate_iri_cases(fixture: dict) -> None:
    namespaces = fixture["namespaces"]
    for case in fixture["predicate_iri"]:
        expected = _expand(case["expected_curie"], namespaces)
        actual = str(dump_to_rdf.property_uri(case["rel_type"]))
        assert actual == expected, (
            f"property_uri({case['rel_type']!r}): expected {expected!r}, got {actual!r} "
            f"({case.get('note', '')})"
        )


def test_tag_property_iri_cases(fixture: dict) -> None:
    namespaces = fixture["namespaces"]
    for case in fixture["tag_property_iri"]:
        expected = _expand(case["expected_curie"], namespaces)
        actual = str(dump_to_rdf.tag_property_uri(case["key"]))
        assert actual == expected, (
            f"tag_property_uri({case['key']!r}): expected {expected!r}, got {actual!r} "
            f"({case.get('note', '')})"
        )
