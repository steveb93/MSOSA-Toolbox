"""Export a SPARQL CONSTRUCT result as GraphML for Cytoscape / yEd / Gephi.

A-Box visualisation tool. Reads the RDF subgraph produced by a CONSTRUCT
query and writes GraphML so the result can be opened in any mainstream
graph desktop tool. Closes the visualisation gap that GraphDB Workbench
would have filled, without adding a triplestore or a long-running viz
service to the docker stack — GraphML is a static file you load on
demand.

Two input modes:
  1. Live Fuseki via HTTP (default):
         python ontology/codegen/sparql_to_graphml.py \\
             --preset capability-realisation \\
             --output cap.graphml
  2. Local TTL fixture (for offline use or testing):
         python ontology/codegen/sparql_to_graphml.py \\
             --from-file ontology/dump/uaf-instance.ttl \\
             --query ontology/visualisations/queries/operational-flow.sparql \\
             --output ops.graphml

Two output modes:
  - GraphML (default): widely supported (Cytoscape, yEd, Gephi, NetworkX)
  - Cytoscape.js JSON (--format cyjs): for embedding in a web page

Conventions:
  - Each RDF subject/object IRI becomes a node. Node id = the IRI.
  - rdf:type, rdfs:label, and any uafprop:* / uaf:domain literals become
    node attributes ("label", "rdf_type", "uaf_domain", "language", ...).
  - Each remaining triple (?s ?p ?o) where ?o is also a resource becomes
    a directed edge. Edge "label" = the local-name of the predicate.

Environment (live mode):
  NEO4J_SPARQL_URL   default http://localhost:3030/uaf/sparql
  FUSEKI_USER        default admin
  FUSEKI_PASSWORD    default Password123
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
QUERIES_DIR = REPO_ROOT / "ontology" / "visualisations" / "queries"

DEFAULT_SPARQL_URL = "http://localhost:3030/uaf/sparql"


def _local_name(iri: str) -> str:
    """Return the local-name component of an IRI for compact labels."""
    for sep in ("#", "/"):
        if sep in iri:
            return iri.rsplit(sep, 1)[1]
    return iri


def _load_query(query_path: Path | None, preset: str | None) -> str:
    if query_path and preset:
        sys.exit("[sparql_to_graphml] --query and --preset are mutually exclusive")
    if preset:
        path = QUERIES_DIR / f"{preset}.sparql"
        if not path.is_file():
            available = sorted(p.stem for p in QUERIES_DIR.glob("*.sparql"))
            sys.exit(f"[sparql_to_graphml] unknown preset '{preset}'. "
                     f"Available: {', '.join(available) or '(none)'}")
        return path.read_text(encoding="utf-8")
    if query_path:
        if not query_path.is_file():
            sys.exit(f"[sparql_to_graphml] query file not found: {query_path}")
        return query_path.read_text(encoding="utf-8")
    sys.exit("[sparql_to_graphml] one of --query or --preset is required")


def _run_construct_remote(query: str, url: str, user: str, password: str):
    """POST a CONSTRUCT query to Fuseki and parse the Turtle response."""
    try:
        import httpx
        from rdflib import Graph
    except ImportError as exc:
        sys.exit(f"[sparql_to_graphml] httpx/rdflib not installed: {exc}\n"
                 f"Install with: pip install httpx rdflib")
    resp = httpx.post(
        url,
        data={"query": query},
        headers={"Accept": "text/turtle"},
        auth=(user, password),
        timeout=60.0,
    )
    resp.raise_for_status()
    g = Graph()
    g.parse(data=resp.text, format="turtle")
    return g


def _run_construct_local(query: str, ttl_path: Path):
    """Execute CONSTRUCT against an in-memory parse of ttl_path."""
    try:
        from rdflib import Graph
    except ImportError as exc:
        sys.exit(f"[sparql_to_graphml] rdflib not installed: {exc}")
    if not ttl_path.is_file():
        sys.exit(f"[sparql_to_graphml] --from-file not found: {ttl_path}")
    source = Graph()
    source.parse(ttl_path, format="turtle")
    return source.query(query).graph


# --- GraphML emission --------------------------------------------------------
#
# Spec: http://graphml.graphdrawing.org/specification.html
# We use a minimal subset: <graphml><graph><node><data/></node><edge/></graph>
# plus typed <key> attribute declarations at the top.

GRAPHML_NS = "http://graphml.graphdrawing.org/xmlns"
ET.register_namespace("", GRAPHML_NS)

# Node attributes we emit if present on the RDF subject.
NODE_ATTR_KEYS = {
    "label": "label",
    "rdf_type": "rdf_type",
    "uaf_domain": "uaf_domain",
    "language": "language",
    "stereotype": "stereotype",
}
EDGE_ATTR_KEYS = {"label": "edge_label"}


def rdf_to_graphml(g) -> str:
    """Convert an rdflib Graph (result of CONSTRUCT) to a GraphML string.

    Splits triples into:
      - per-subject literal facts (folded into node attributes), and
      - resource→resource triples (emitted as edges).
    """
    from rdflib import BNode, Literal, URIRef
    from rdflib.namespace import RDF, RDFS

    UAF = "http://msosa-toolbox.local/uaf#"
    UAFPROP = "http://msosa-toolbox.local/uaf/prop#"

    node_attrs: dict[str, dict[str, str]] = {}
    edges: list[tuple[str, str, str]] = []

    def _node_id(node) -> str:
        if isinstance(node, BNode):
            return f"_:{node}"
        return str(node)

    def _ensure_node(node) -> str:
        nid = _node_id(node)
        node_attrs.setdefault(nid, {})
        return nid

    for s, p, o in g:
        s_id = _ensure_node(s)
        if isinstance(o, Literal):
            attrs = node_attrs[s_id]
            if p == RDFS.label:
                attrs["label"] = str(o)
            elif str(p) == UAF + "domain":
                attrs["uaf_domain"] = str(o)
            elif str(p).startswith(UAFPROP):
                local = _local_name(str(p))
                attrs[local] = str(o)
            else:
                attrs[_local_name(str(p))] = str(o)
        elif p == RDF.type:
            _ensure_node(o)
            attrs = node_attrs[s_id]
            existing = attrs.get("rdf_type", "")
            type_name = _local_name(str(o))
            attrs["rdf_type"] = f"{existing},{type_name}" if existing else type_name
        else:
            _ensure_node(o)
            edges.append((s_id, str(p), _node_id(o)))

    for nid, attrs in node_attrs.items():
        if "label" not in attrs:
            attrs["label"] = _local_name(nid)

    root = ET.Element(f"{{{GRAPHML_NS}}}graphml")

    seen_node_keys: set[str] = set()
    for attrs in node_attrs.values():
        seen_node_keys.update(attrs.keys())
    for k in sorted(seen_node_keys):
        ET.SubElement(root, f"{{{GRAPHML_NS}}}key", {
            "id": f"n_{k}",
            "for": "node",
            "attr.name": k,
            "attr.type": "string",
        })
    ET.SubElement(root, f"{{{GRAPHML_NS}}}key", {
        "id": "e_label",
        "for": "edge",
        "attr.name": "label",
        "attr.type": "string",
    })

    graph_el = ET.SubElement(root, f"{{{GRAPHML_NS}}}graph", {
        "id": "G", "edgedefault": "directed",
    })

    for nid, attrs in sorted(node_attrs.items()):
        node_el = ET.SubElement(graph_el, f"{{{GRAPHML_NS}}}node", {"id": nid})
        for k in sorted(attrs.keys()):
            data_el = ET.SubElement(node_el, f"{{{GRAPHML_NS}}}data",
                                    {"key": f"n_{k}"})
            data_el.text = attrs[k]

    for i, (s_id, p, o_id) in enumerate(edges):
        edge_el = ET.SubElement(graph_el, f"{{{GRAPHML_NS}}}edge", {
            "id": f"e{i}", "source": s_id, "target": o_id,
        })
        data_el = ET.SubElement(edge_el, f"{{{GRAPHML_NS}}}data", {"key": "e_label"})
        data_el.text = _local_name(p)

    ET.indent(root, space="  ")
    return '<?xml version="1.0" encoding="UTF-8"?>\n' + \
           ET.tostring(root, encoding="unicode")


def rdf_to_cyjs(g) -> str:
    """Convert an rdflib Graph to Cytoscape.js JSON.

    Same node/edge split as rdf_to_graphml. Returned as a JSON string
    suitable for `cy.add(JSON.parse(...))` on a Cytoscape.js page.
    """
    from rdflib import BNode, Literal
    from rdflib.namespace import RDF, RDFS

    UAF = "http://msosa-toolbox.local/uaf#"
    UAFPROP = "http://msosa-toolbox.local/uaf/prop#"

    nodes: dict[str, dict] = {}
    edges: list[dict] = []

    def _nid(node) -> str:
        return f"_:{node}" if isinstance(node, BNode) else str(node)

    for s, p, o in g:
        s_id = _nid(s)
        nodes.setdefault(s_id, {"id": s_id})
        if isinstance(o, Literal):
            attrs = nodes[s_id]
            if p == RDFS.label:
                attrs["label"] = str(o)
            elif str(p) == UAF + "domain":
                attrs["uaf_domain"] = str(o)
            elif str(p).startswith(UAFPROP):
                attrs[_local_name(str(p))] = str(o)
            else:
                attrs[_local_name(str(p))] = str(o)
        elif p == RDF.type:
            o_id = _nid(o)
            nodes.setdefault(o_id, {"id": o_id})
            attrs = nodes[s_id]
            existing = attrs.get("rdf_type", "")
            type_name = _local_name(str(o))
            attrs["rdf_type"] = f"{existing},{type_name}" if existing else type_name
        else:
            o_id = _nid(o)
            nodes.setdefault(o_id, {"id": o_id})
            edges.append({
                "data": {
                    "id": f"e{len(edges)}",
                    "source": s_id,
                    "target": o_id,
                    "label": _local_name(str(p)),
                }
            })

    for nid, data in nodes.items():
        data.setdefault("label", _local_name(nid))

    return json.dumps({
        "elements": {
            "nodes": [{"data": d} for d in nodes.values()],
            "edges": edges,
        }
    }, indent=2)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n", 1)[0])
    src = parser.add_mutually_exclusive_group()
    src.add_argument("--from-file", type=Path,
                     help="Read RDF from a local Turtle file instead of Fuseki")
    src.add_argument("--sparql-url", default=os.getenv("NEO4J_SPARQL_URL",
                                                       DEFAULT_SPARQL_URL),
                     help="Fuseki SPARQL endpoint (default from $NEO4J_SPARQL_URL "
                          f"or {DEFAULT_SPARQL_URL})")
    qry = parser.add_mutually_exclusive_group(required=True)
    qry.add_argument("--query", type=Path,
                     help="Path to a SPARQL CONSTRUCT query file")
    qry.add_argument("--preset",
                     help="Built-in query name under ontology/visualisations/queries/")
    parser.add_argument("--format", choices=["graphml", "cyjs"], default="graphml",
                        help="Output format (default graphml)")
    parser.add_argument("--output", "-o", type=Path,
                        help="Write to file (default stdout)")
    args = parser.parse_args()

    query = _load_query(args.query, args.preset)

    if args.from_file:
        g = _run_construct_local(query, args.from_file)
    else:
        user = os.getenv("FUSEKI_USER", "admin")
        password = os.getenv("FUSEKI_PASSWORD", "Password123")
        g = _run_construct_remote(query, args.sparql_url, user, password)

    body = rdf_to_graphml(g) if args.format == "graphml" else rdf_to_cyjs(g)

    if args.output:
        args.output.write_text(body, encoding="utf-8")
        n_triples = sum(1 for _ in g)
        print(f"[sparql_to_graphml] wrote {args.output} ({n_triples} triples)",
              file=sys.stderr)
    else:
        sys.stdout.write(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
