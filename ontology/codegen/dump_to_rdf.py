"""Materialise the Neo4j UAF graph as RDF Turtle for Fuseki to load.

Walks all :UAFElement nodes and the relationships between them, emits each as
RDF using the namespaces declared by the MVO (ontology/uaf-mvo.ttl).

Output: ontology/dump/uaf-instance.ttl

STATUS (as of Stage 4, v1.3.0-Preview):
    The msosa-model-exporter plugin can now emit RDF directly from the MSOSA
    model — tick "RDF Turtle file (and optionally PUT to Fuseki)" in the export
    dialog. That path is preferred for routine post-export refreshes: it avoids
    the Bolt round-trip, runs inside the same SwingWorker that wrote Cypher, and
    optionally pushes to Fuseki via Graph Store Protocol (no docker restart
    needed).

    This script is kept as the recovery path:
      - Run it when the plugin RDF emitter has a regression and you need a
        known-good TTL rebuild from Neo4j (the system of record).
      - Run it when Fuseki goes out of step with Neo4j for any reason (manual
        Cypher edits, partial export, etc.) and you need to re-derive the SPARQL
        view from scratch.
      - Keep it pinned to the same IRI conventions as
        msosa-model-exporter/src/main/java/com/uaf/neo4j/plugin/rdf/RDFTripleBuilder.java;
        the two MUST stay byte-identical or SPARQL queries will silently miss.

Output: ontology/dump/uaf-instance.ttl
Run after each MSOSA export so the SPARQL view stays in step with Neo4j.

Usage:
    python ontology/codegen/dump_to_rdf.py
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

from neo4j import GraphDatabase
from rdflib import Graph, Literal, Namespace, URIRef
from rdflib.namespace import RDF, RDFS, XSD

REPO_ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = REPO_ROOT / "ontology" / "dump"
OUT_FILE = OUT_DIR / "uaf-instance.ttl"

UAF = Namespace("http://msosa-toolbox.local/uaf#")
SYSML = Namespace("http://msosa-toolbox.local/sysml#")
BPMN = Namespace("http://msosa-toolbox.local/bpmn#")
UAFINST = Namespace("http://msosa-toolbox.local/uaf/instance#")
UAFTV = Namespace("http://msosa-toolbox.local/uaf/tag#")

NS_FOR_LANG = {"UAF": UAF, "SysML": SYSML, "BPMN": BPMN}


def to_camel(rel_type: str) -> str:
    """PERFORMS -> performs ; INFORMATION_FLOW -> informationFlow"""
    head, *tail = rel_type.lower().split("_")
    return head + "".join(p.capitalize() for p in tail)


SAFE_ID = re.compile(r"[^A-Za-z0-9_\-]")


def instance_uri(node_id: str) -> URIRef:
    return UAFINST[SAFE_ID.sub("_", str(node_id))]


def class_uri(label: str, language: str | None) -> URIRef:
    ns = NS_FOR_LANG.get(language or "UAF", UAF)
    return ns[label]


def property_uri(rel_type: str) -> URIRef:
    return UAF[to_camel(rel_type)]


def tag_property_uri(tag_key: str) -> URIRef:
    # tag keys may contain anything; strip tv_ prefix and sanitise
    key = tag_key[3:] if tag_key.startswith("tv_") else tag_key
    return UAFTV[SAFE_ID.sub("_", key)]


CORE_PROPS = {"id", "name", "qualifiedName", "documentation", "domain", "layer",
              "language", "stereotype", "neo4jLabel", "packageName"}


def add_node(g: Graph, record: dict) -> None:
    labels = [l for l in record["labels"] if l != "UAFElement"]
    if not labels:
        return
    node_id = record["id"]
    if node_id is None:
        return
    iri = instance_uri(node_id)
    language = record.get("language") or "UAF"
    for label in labels:
        g.add((iri, RDF.type, class_uri(label, language)))
    if record.get("name"):
        g.add((iri, RDFS.label, Literal(record["name"], datatype=XSD.string)))
    for k in ("qualifiedName", "documentation", "domain", "layer", "language",
              "packageName"):
        v = record.get(k)
        if v:
            g.add((iri, UAF[k], Literal(v, datatype=XSD.string)))
    # tagged values flattened on the node as tv_* properties
    for k, v in (record.get("props") or {}).items():
        if k in CORE_PROPS or v is None:
            continue
        if not k.startswith("tv_"):
            continue
        g.add((iri, tag_property_uri(k), Literal(v, datatype=XSD.string)))


def add_relationship(g: Graph, record: dict) -> None:
    src = record.get("src_id")
    tgt = record.get("tgt_id")
    rtype = record.get("rel_type")
    if not src or not tgt or not rtype:
        return
    if rtype == "INSTANCE_OF":  # already covered by rdf:type
        return
    g.add((instance_uri(src), property_uri(rtype), instance_uri(tgt)))


def dump(uri: str, user: str, password: str, database: str) -> tuple[int, int]:
    g = Graph()
    g.bind("uaf", UAF)
    g.bind("sysml", SYSML)
    g.bind("bpmn", BPMN)
    g.bind("uafinst", UAFINST)
    g.bind("uaftv", UAFTV)
    g.bind("rdfs", RDFS)
    g.bind("xsd", XSD)

    driver = GraphDatabase.driver(uri, auth=(user, password))
    n_nodes = n_rels = 0
    try:
        with driver.session(database=database) as session:
            # UAF instances are reliably identifiable by their :INSTANCE_OF edge
            # to a :Stereotype node (created unconditionally by Neo4jCypherBuilder
            # for every exported element). The single-vs-dual-label question
            # doesn't matter here.
            for record in session.run(
                """
                MATCH (n)-[:INSTANCE_OF]->(s:Stereotype)
                RETURN labels(n) AS labels, n.id AS id, n.name AS name,
                       n.qualifiedName AS qualifiedName, n.documentation AS documentation,
                       n.domain AS domain, n.layer AS layer,
                       coalesce(n.language, s.language, 'UAF') AS language,
                       n.packageName AS packageName,
                       properties(n) AS props
                """
            ):
                add_node(g, record.data())
                n_nodes += 1
            for record in session.run(
                """
                MATCH (s)-[:INSTANCE_OF]->(:Stereotype),
                      (t)-[:INSTANCE_OF]->(:Stereotype),
                      (s)-[r]->(t)
                WHERE type(r) <> 'INSTANCE_OF'
                RETURN s.id AS src_id, type(r) AS rel_type, t.id AS tgt_id
                """
            ):
                add_relationship(g, record.data())
                n_rels += 1
    finally:
        driver.close()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    g.serialize(destination=str(OUT_FILE), format="turtle")
    return n_nodes, n_rels


def main() -> int:
    uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
    user = os.getenv("NEO4J_USERNAME", "neo4j")
    password = os.getenv("NEO4J_PASSWORD", "Password123")
    database = os.getenv("NEO4J_DATABASE", "neo4j")

    print(f"Dumping {uri} to {OUT_FILE.relative_to(REPO_ROOT)}...", file=sys.stderr)
    nodes, rels = dump(uri, user, password, database)
    print(f"Wrote {nodes} nodes and {rels} relationships.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
