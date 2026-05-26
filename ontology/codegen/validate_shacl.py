"""SHACL validation of the dumped UAF instance graph against ontology/shapes/uaf-shapes.ttl.

Stage 3 scaffolding (speculative). Runs pyshacl against:
  - data:   ontology/uaf-mvo.ttl + ontology/uaf-mvo-axioms.ttl + ontology/dump/uaf-instance.ttl
  - shapes: ontology/shapes/uaf-shapes.ttl

Inference is set to "rdfsowlrl" so:
  - rdfs:subClassOf+ paths resolve (sh:targetClass uaf:StrategicElement
    matches every subclass without listing them individually),
  - owl:inverseOf materialises both directions of canonical pairs
    (uaf:realises ↔ uaf:realisedBy etc.) — shapes use forward paths
    rather than sh:inversePath,
  - owl:TransitiveProperty closes the security classification dominance
    chain.

This matches Fuseki's OWL FB reasoner (Stage 3 upgrade from RDFS-Exp).

Usage:
    python ontology/codegen/validate_shacl.py
    python ontology/codegen/validate_shacl.py --json    # machine-readable report

Exit codes:
    0  - conforms (no violations)
    1  - violations present
    2  - warnings only (no violations)
    3  - validator could not run (missing files, pyshacl error)

NOTE: pyshacl is not a runtime dependency of graph_mcp_driver. Install with
      `pip install pyshacl` (or `pip install -e .[ontology]` once we declare an
      ontology extras group).
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MVO_FILE = REPO_ROOT / "ontology" / "uaf-mvo.ttl"
AXIOMS_FILE = REPO_ROOT / "ontology" / "uaf-mvo-axioms.ttl"
DUMP_FILE = REPO_ROOT / "ontology" / "dump" / "uaf-instance.ttl"
SHAPES_FILE = REPO_ROOT / "ontology" / "shapes" / "uaf-shapes.ttl"

SH = "http://www.w3.org/ns/shacl#"


def _require(path: Path, hint: str) -> None:
    if not path.is_file():
        print(f"[validate_shacl] missing {path.relative_to(REPO_ROOT)}: {hint}",
              file=sys.stderr)
        sys.exit(3)


def _load_pyshacl():
    try:
        from pyshacl import validate  # type: ignore
        from rdflib import Graph
    except ImportError as exc:
        print(f"[validate_shacl] pyshacl/rdflib not installed: {exc}\n"
              f"Install with: pip install pyshacl",
              file=sys.stderr)
        sys.exit(3)
    return validate, Graph


def _parse_report(report_graph) -> list[dict]:
    """Extract violation rows from a pyshacl report graph.

    Each row: {focus_node, source_shape, message, severity, path, value}.
    """
    from rdflib import Namespace, URIRef
    SH_NS = Namespace(SH)
    RDF_TYPE = URIRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    rows: list[dict] = []
    for vr in report_graph.subjects(predicate=RDF_TYPE,
                                    object=URIRef(f"{SH}ValidationResult")):
        row = {
            "focus_node": str(report_graph.value(vr, SH_NS.focusNode) or ""),
            "source_shape": str(report_graph.value(vr, SH_NS.sourceShape) or ""),
            "message": str(report_graph.value(vr, SH_NS.resultMessage) or ""),
            "severity": str(report_graph.value(vr, SH_NS.resultSeverity) or "")
                            .split("#")[-1] or "Violation",
            "path": str(report_graph.value(vr, SH_NS.resultPath) or ""),
            "value": str(report_graph.value(vr, SH_NS.value) or ""),
        }
        rows.append(row)
    return rows


def _format_text(conforms: bool, rows: list[dict]) -> str:
    if conforms and not rows:
        return "[validate_shacl] CONFORMS (no violations, no warnings)\n"
    lines = ["[validate_shacl] CONFORMS" if conforms else "[validate_shacl] DOES NOT CONFORM",
             f"  {len(rows)} result(s):", ""]
    for r in rows:
        lines.append(f"  [{r['severity']}] {r['source_shape'].rsplit('#', 1)[-1]}")
        lines.append(f"    focus:    {r['focus_node']}")
        if r["path"]:
            lines.append(f"    path:     {r['path']}")
        if r["value"]:
            lines.append(f"    value:    {r['value']}")
        lines.append(f"    message:  {r['message']}")
        lines.append("")
    return "\n".join(lines)


def run(mvo: Path = MVO_FILE,
        axioms: Path = AXIOMS_FILE,
        dump: Path = DUMP_FILE,
        shapes: Path = SHAPES_FILE) -> tuple[bool, list[dict]]:
    """Validate the dump against the shapes. Returns (conforms, rows)."""
    _require(mvo, "regenerate via `python ontology/codegen/generate_mvo.py`")
    _require(axioms, "expected ontology/uaf-mvo-axioms.ttl (Stage 3 OWL axioms)")
    _require(dump, "regenerate via `python ontology/codegen/dump_to_rdf.py`")
    _require(shapes, "expected ontology/shapes/uaf-shapes.ttl (Stage 3 scaffolding)")

    validate, Graph = _load_pyshacl()

    data_graph = Graph()
    data_graph.parse(mvo, format="turtle")
    data_graph.parse(axioms, format="turtle")
    data_graph.parse(dump, format="turtle")

    shapes_graph = Graph()
    shapes_graph.parse(shapes, format="turtle")

    conforms, report_graph, _report_text = validate(
        data_graph=data_graph,
        shacl_graph=shapes_graph,
        inference="rdfsowlrl",
        meta_shacl=False,
        advanced=True,
        debug=False,
    )
    rows = _parse_report(report_graph)
    return conforms, rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n", 1)[0])
    parser.add_argument("--json", action="store_true",
                        help="emit JSON report instead of text")
    args = parser.parse_args()

    conforms, rows = run()

    if args.json:
        print(json.dumps({"conforms": conforms, "results": rows}, indent=2))
    else:
        print(_format_text(conforms, rows))

    if not conforms:
        violations = [r for r in rows if r["severity"] == "Violation"]
        return 1 if violations else 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
