# Ontology roadmap — where to go next

Stage 2 is operationally live and Stage 4's emitter-side work has landed early: the Java plugin can already write Cypher and RDF in the same export, and Apache Jena Fuseki provides a real SPARQL 1.1 endpoint with RDFS reasoning over a Minimum Viable Ontology covering all UAF 1.2 domains plus SysML 1.6 and BPMN 2.0. Neo4j remains the system of record; the plugin's RDF emitter is the preferred refresh path, with `dump_to_rdf.py` retained as the recovery path. The Python ↔ Java emitters are now held to triple-set parity by a shared fixture and matched tests on both sides.

This document describes what comes next, in the order the staging in `Ontology-Approach-to-Knowledge.md` §12 prescribes — and explicitly what each stage is **not** for, so the migration does not over-shoot.

---

## Where we are — Stage 2 (live)

| | Detail |
|---|---|
| **Backend** | Apache Jena Fuseki sidecar (`stain/jena-fuseki:latest`) on port 3030 |
| **Reasoner** | RDFS-Exp rule reasoner (subsumption + property inheritance) |
| **T-Box** | `ontology/uaf-mvo.ttl` — 103 classes (UAF 76, SysML 10, BPMN 17), 31 ObjectProperties |
| **A-Box** | `ontology/dump/uaf-instance.ttl` — refreshed by `ontology/codegen/dump_to_rdf.py` after each MSOSA export |
| **MCP tool** | `run_sparql(query)` alongside `run_cypher(query)` |
| **Refresh cadence** | Manual after each export (matches MSOSA's export cadence). Plugin RDF emitter optionally PUTs to Fuseki Graph Store Protocol — no docker restart required. |
| **Emitter parity** | `parity-fixture.json` consumed by `Test/test_rdf_parity.py` (Python) and `RDFTripleBuilderParityTest.java` (Java) — namespaces, instance/class/predicate/tag-property IRIs all checked. (#73, #74) |
| **Traverser coverage** | Full UAF 1.2 + SysML 1.6 + BPMN 2.0; UAF>BPMN>SysML stereotype ranking; inherited-stereotype lookup; classifier-owned content (parts, ports, actions); attached project modules. Unmatched stereotypes surface in the post-export dialog. (#75) |
| **Data/ERD coverage** | DataObject/Input/Output/Store + Entity/EntityRelationship/EntityRelation registered. Attributes are first-class `:Attribute` nodes via `HAS_ATTRIBUTE`; primitive/enum/datatype targets emit `:DataType` via `OF_TYPE`. Association edges carry srcMult/tgtMult/srcRole/tgtRole. (#76) |

**What Stage 2 does well.** Subsumption queries (`?x a/rdfs:subClassOf* uaf:StrategicElement`), cross-language traceability (UAF Capability → SysML Requirement), orphan/gap detection via `FILTER NOT EXISTS`, semantic search backed by AI via the Claude MCP server.

**What Stage 2 does not do, by design.** OWL 2 DL consistency checking. A-Box reasoning over property chains (e.g. "if A inherits from B and B has property P, then A has P at the individual level"). Cross-tool federation via SPARQL `SERVICE`. Sub-second freshness against Neo4j writes.

---

## Stage 3 — Native triplestore for governed sub-domains

**Trigger.** When formal compliance, accreditation, or cross-tool federation pressure arrives. Likely sub-domains first: **Strategic** (Capability portfolio governance) and **Security** (classification dominance chains for accreditation).

**What changes.** Materialise the Strategic + Security slice as native RDF in a triplestore that supports OWL 2 RL or EL reasoning (GraphDB Free, Stardog Community, or upgrading the Jena profile from RDFS to OWL Mini). The rest of the model stays in Neo4j + Fuseki.

**Concrete tasks.**

- [ ] Pick a triplestore. GraphDB Free is the most pragmatic — has OWL 2 RL reasoner, SHACL validation, and a managed Docker image. Stardog Community has a 10M-triple cap but better federation. Alternatively, swap the Fuseki reasoner to a stronger profile if the rule layer is enough.
- [ ] Author OWL 2 axioms beyond the current RDFS subClassOf scaffolding:
  - `owl:someValuesFrom` cardinality restrictions on `CapabilityConfiguration` (every config must have ≥1 realising activity, ≥1 resource, ≥1 role)
  - Disjointness between Strategic and Operational classes
  - Property chains for security classification dominance: `:dominates o :dominates ⊑ :dominates`
  - `owl:inverseOf` for the canonical pairs (`uaf:realises ↔ uaf:realisedBy`)
- [ ] Stand up a SHACL shapes file (`ontology/shapes/uaf-shapes.ttl`) and run SHACL validation as a post-dump step. Surface violations in the MCP server.
- [ ] Federation: enable SPARQL 1.1 `SERVICE` so the triplestore can query the Fuseki dataset for unmaterialised classes, and vice versa.
- [ ] Extend the Java plugin's `ExportSummaryDialog` with a SHACL conformance row alongside the existing "errors" row.

**Gating criteria** (per `Ontology-Approach-to-Knowledge.md` §10).

- The UAF programme faces a named accreditation or compliance milestone (e.g. JSP 440, NIST 800-53, ISO 27001 for the modelled architecture).
- A dedicated ontologist / knowledge engineer is available to author and maintain OWL axioms.
- The stereotype registry has stabilised — no MSOSA profile renames pending.

---

## Stage 4 — Full RDF migration

**Trigger.** When the programme reaches production / utilisation (ISO/IEC/IEEE 15288 §6.4 stages 5–6) and cross-tool federation or accreditation is mandatory **across the whole model**, not just sub-domains.

**What changes.** The Java plugin gains an RDF emitter alongside (eventually replacing) `Neo4jCypherBuilder`. Apache Jena's `RDFFormat.TURTLE` writer would sit behind a parallel `RDFExportService implements ExportService` interface (which would need to be extracted first — see "preparatory refactor" below). Neo4j becomes optional or is retired.

**Concrete tasks.**

- [x] **Preparatory refactor** — extracted an `ExportService` interface from `Neo4jExportService` so a new `RDFExportService` sits alongside without touching the traverser or DTOs. Shipped in `v1.1.0-Preview`.
- [x] Add Apache Jena as a Maven dependency. Shipped in `v1.2.0-Preview` as `jena-arq:4.10.0` (last release on Java 11), relocated to `com.uaf.shaded.jena.*` to be safe — 3,791 class files cleanly shaded, zero unshaded leak.
- [x] Implement `RDFTripleBuilder` mirroring `Neo4jCypherBuilder`. IRI conventions kept byte-identical to `dump_to_rdf.py` so SPARQL queries written against the Python dump still match. Shipped in `v1.2.0-Preview`.
- [x] Implement `RDFExportService` — buffers triples in an in-memory Jena model; on close, writes Turtle to disk and optionally PUTs to Fuseki's Graph Store Protocol endpoint. Shipped in `v1.2.0-Preview`.
- [x] Extend `ExportConfigDialog` with a target-type chooser: "Neo4j (LPG via Cypher)" and "RDF Turtle file (and optionally PUT to Fuseki)" with multi-target loop and merged summary. Shipped in `v1.3.0-Preview`.
- [x] **Enforce dump_to_rdf.py ↔ RDFTripleBuilder triple-set parity** with a shared JSON fixture (`ontology/codegen/parity-fixture.json`) and matched tests on both sides — `Test/test_rdf_parity.py` and `RDFTripleBuilderParityTest.java`. Closes #73 and the dump-script cleanup in #74. Shipped via PR #77 (`v1.3.1-Preview`).
- [ ] Migrate the MCP server to SPARQL-only or keep both tools depending on operational needs.
- [ ] Decommission the dump script — no longer needed for routine refreshes (the plugin writes RDF directly) but `ontology/codegen/dump_to_rdf.py` remains as the recovery path for rebuilding Fuseki from the Neo4j system of record. The clean-up of vestigial layer/multi-label/`:UAFElement` code (#74) has landed; decommission only when the plugin RDF emitter has been live and unchanged for ≥1 quarter.

**Gating criteria** (per `Ontology-Approach-to-Knowledge.md` §10).

- Accreditation or federation pressure has arrived across the whole model.
- The team has accumulated OWL/SPARQL skills via Stages 2 and 3.
- The Java plugin maintainer has bandwidth for a new export target.
- Stakeholder appetite for losing Neo4j-only features (Cypher, GDS algorithms, Bloom) is real.

---

## Stage 5 — Composite AI and decision intelligence

**Trigger.** When the SPARQL view has been live long enough that pattern-recognition tasks can be retrained on graph-derived features.

**What changes.** Adds the third leg of Gartner's "composite AI" pattern (graph + ML + reasoning + LLM, per Jaffri G00768041 §3.3): a model layer that consumes the SPARQL graph as feature input. The Claude MCP server already provides the LLM leg; the reasoner leg lives in Fuseki/triplestore; the missing leg is supervised or graph-native ML.

**Concrete tasks.**

- [ ] Stand up Neo4j Graph Data Science (GDS) on the LPG side. The Docker image already includes it (`NEO4J_PLUGINS=graph-data-science`). GDS gives PageRank, community detection, betweenness — useful for identifying critical Operational Activities and capability-coverage gaps.
- [ ] Author GDS-driven recommendation algorithms (per Jaffri §3.3): content-based recommendation of ResourceArtifacts that could fill detected capability gaps, using graph-position features.
- [ ] Wire GDS outputs back into the RDF view (write algorithm results as `uafprop:pagerank` annotations on node URIs) so SPARQL queries can sort by relevance.
- [ ] Build a decision-intelligence dashboard (Streamlit/Dash/Grafana) consuming both the SPARQL endpoint and GDS outputs. Targets the "Decision makers" persona in `Ontology-Approach-to-Knowledge.md` §8.

**Gating criteria.** No infrastructure gates — Stage 5 is incremental and additive. Realistic trigger is when Stage 2 has been in active use for ≥1 quarter and stakeholder demand for "tell me what I should do, not just what exists" emerges.

---

## Cross-cutting backlog (do at any stage)

These are independent of stage progression. Pick up whichever pays off soonest in your context.

### Traverser & registry coverage

- [x] **Stereotype selection ranks UAF > BPMN > SysML** and walks the general chain to find a registered ancestor. Closes the bulk of #75 — multi-stereotyped Operational/Resource elements no longer collapse to generic SysML `Block`. Shipped via PR #78.
- [x] **Descend into Classifiers, not just Packages.** Internal block diagram parts/ports and activity-owned actions now reach the export. PR #78.
- [x] **Relationship-stereotype map** for `OperationalExchange`, `ResourceInteraction`, `NeedLine` (and Tier-1 iSCP additions: `Implements`, `IsCapableToPerform`, `PerformsInContext`, `MapsToCapability`, `DataAssociation`, the four `*Association` family stereotypes, `Allocate`, `DeriveReqt`, `Copy`, `SequenceFlow`, `MessageFlow`). PRs #78 + #82.
- [x] **Walk attached project modules** by default (opt-out via the `UAFModelTraverser(Project, boolean)` constructor). PR #83 plus the `com.nomagic.ci.persistence` build dependency.
- [x] **Unmatched-stereotype diagnostic** surfaced in `ExportSummaryDialog` as a dedicated tab with copy-to-clipboard. PR #78.
- [x] **Registry reconciliation tooling** — `scripts/registry-diff.groovy` runs in the MSOSA scripting console and diffs the live profile against the registry. PR #81.
- [x] **Data artefact + ERD coverage (#76):** `DataObject` / `DataInput` / `DataOutput` / `DataStore` registered; BPMN `DataInputAssociation` / `DataOutputAssociation` connect data to consuming/producing Tasks; `Entity` / `EntityRelationship` / `EntityRelation` registered; attributes promoted to first-class `:Attribute` nodes via `HAS_ATTRIBUTE` (Option A); `:DataType` synthesised for primitive/enum/datatype targets via `OF_TYPE`; `Association` edges carry `srcMult` / `tgtMult` / `srcRole` / `tgtRole`. Shipped via the #78/#79 cascade.
- [ ] **AssociationClass handling** (last open #76 sub-item). Currently UML `AssociationClass` instances are not specially handled — they emit only the relationship side. Decide: emit both a node and an edge, or document the omission. Low frequency in current UAF profiles, so it can wait.
- [ ] **Live-model regression test.** Acceptance criterion on #75/#76 calls for non-zero counts against a real MSOSA project. Cannot run on the CI box (no MSOSA install). Suggest a manual export-and-check script that runs against a known-good `.mdzip` once a release cut is being prepared, with the counts captured in the PR description.
- [ ] **Further iSCP registry tiers.** PR #82 reconciled Tier-1. Tiers 2–N (less common stereotypes flagged by `registry-diff.groovy`) remain as the profile expands.

### Ontology hygiene

- [ ] Stop UAF/SysML stereotype name collisions becoming silent RDF collisions. Both languages have a `Capability`-adjacent term in some profiles. Today the codegen relies on the seeded `:Stereotype` nodes being correctly tagged with `language`. Add a SHACL shape that asserts every concrete class has exactly one language annotation.
- [ ] Add `rdfs:comment` to each class drawn from MSOSA's stereotype description fields (UML `ownedComment` on the Stereotype). The Java traverser already extracts these; the registry just needs to surface them.
- [ ] Validate the codegen output with `riot --validate` (Jena CLI) in CI so a malformed TTL doesn't ship.

### Refresh automation

- [x] Add an RDF emission path inside `ExportConfigDialog` so the user can opt in to refresh-on-export. Shipped in `v1.3.0-Preview` — the dialog now offers "Neo4j (LPG)" and "RDF" as independent target checkboxes, defaulting to LPG-only to preserve the v1.0.x behaviour.
- [x] Replace "restart fuseki" with a Fuseki Graph Store Protocol `PUT` to `/uaf/data` — implemented as `com.uaf.neo4j.plugin.rdf.FusekiClient` using Java 11's built-in `HttpClient`. Tick "Also PUT to Fuseki /data endpoint" in the export dialog to enable. Shipped in `v1.3.0-Preview`.

### Quality and tests

- [ ] Add a SPARQL conformance test set under `Test/queries/` — pairs of "Cypher query against Neo4j" and "SPARQL query against Fuseki" that must return the same result set. Catches mapping drift.
- [ ] Wire a smoke-test job into CI: pull Neo4j + Fuseki, run init + seed + dump + ASK queries. Cheap insurance against silent regressions in any of the moving parts.

### Documentation

- [ ] Recursive cross-link: each ontology artefact (`uaf-mvo.ttl`, mapping file, dump script) should `rdfs:isDefinedBy` an IRI that resolves to a documentation anchor. Today the codegen emits `rdfs:isDefinedBy <http://msosa-toolbox.local/uaf/mvo>` but that IRI doesn't resolve to anything. Fix it to point at a real `ontology/README.md` anchor or a published wiki page.
- [ ] Persona-shaped landing pages, per `Ontology-Approach-to-Knowledge.md` §8 / Gartner persona framing: `docs/personas/decision-makers.md`, `docs/personas/enterprise-architects.md`, `docs/personas/data-architects.md`, with the queries each persona is likely to want copy-pasted at the top.

---

## Decision log — kept for posterity

| Decision | Made | Why |
|---|---|---|
| **n10s rejected** | 2026-05-19 | n10s has no SPARQL endpoint — only Cypher→RDF and ontology export. n10s init script and mapping cookbook were prototyped (showed correct RDF translation in the Neo4j browser) but provided no queryable surface for downstream tools. |
| **Ontop rejected** | 2026-05-19 | Ontop 5.x JVM requires Java 11; Neo4j JDBC v6 (the one with SQL→Cypher translation) requires Java 17 — hard incompatibility. Older Neo4j JDBC driver (v5) has no SQL translator, so the OBDA layer would have had no underlying SQL to translate. |
| **Fuseki adopted** | 2026-05-19 | Real SPARQL 1.1, real reasoning, stock image, no Java compat hell. Trade-off: data freshness gated by dump refresh cadence — acceptable because MSOSA exports are themselves manual. |
| **MVO scope expanded** | 2026-05-19 | Initial Stage 2 plan covered Strategic + Operational + Resource only. Extended to all 8 UAF domains + SysML + BPMN because the codegen was already query-driven and the marginal cost of including everything was zero. |
| **Plugin UI: light-touch SPARQL config** | 2026-05-19 | Added Fuseki URL/auth to `ConnectionDialog` plus an `Open SPARQL Endpoint` menu item and a `Copy SPARQL Refresh Cmd` button on `ExportSummaryDialog`. Did **not** wire the dump script into the plugin — keeps Python and Java loosely coupled for now. |
| **Triple-set parity, not byte parity** | 2026-05-20 | `rdflib` and Jena differ on serialisation ordering. Fixture-based assertions compare expected IRIs, not bytes — docstrings on both emitters say so explicitly. (#73 / PR #77) |
| **ERD attributes as first-class nodes (Option A)** | 2026-05-20 | UML `Property` on entities becomes a `:Attribute` node with a `HAS_ATTRIBUTE` edge; `:DataType` synthesised for primitive/enum targets via `OF_TYPE`. The pre-#76 `tv_attr_*` flattening would have erased multiplicity, role, and read-only/derived flags — Option B was cheaper but lost the semantics that make ERD queries useful. Breaking change versus v1.2.x: callers that grep for `tv_attr_<name>` must traverse the new edges. (#76 / PR #79 → #78) |
| **Stereotype ranking UAF > BPMN > SysML** | 2026-05-20 | Multi-stereotyped Operational elements (UAF + SysML `Block`) were resolving to `Block` depending on iteration order, dropping the UAF domain context. Language rank + most-specific-within-rank tie-break is deterministic and matches user intent. (#75 / PR #78) |
| **Attached modules walked by default** | 2026-05-20 | UAF reference architectures and library modules normally live in attached projects. Defaulting off would have left them silently missing from every export. Opt-out kept via the two-arg constructor for cases where modules are intentionally excluded. (#75 RC #4 / PR #83) |
