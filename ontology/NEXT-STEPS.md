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

**What Stage 2 does well.** Subsumption queries (`?x a/rdfs:subClassOf* uaf:StrategicElement`), cross-language traceability (UAF Capability → SysML Requirement), orphan/gap detection via `FILTER NOT EXISTS`, semantic search backed by AI via the MCP server.

**What Stage 2 does not do, by design.** OWL 2 DL consistency checking. A-Box reasoning over property chains (e.g. "if A inherits from B and B has property P, then A has P at the individual level"). Cross-tool federation via SPARQL `SERVICE`. Sub-second freshness against Neo4j writes.

---

## Stage 3 — Native triplestore for governed sub-domains

**Trigger.** When formal compliance, accreditation, or cross-tool federation pressure arrives. Likely sub-domains first: **Strategic** (Capability portfolio governance) and **Security** (classification dominance chains for accreditation).

**What changes (as delivered).** Originally framed as materialising the Strategic + Security slice as native RDF in a *separate* triplestore (GraphDB Free, Stardog Community, …) with the rest staying in Neo4j + Fuseki. **Replaced** by staying on Fuseki and upgrading its reasoner profile from RDFS-Exp to OWL FB — covers everything in the Stage 3 axiom list (property chains, inverseOf, disjointness, someValuesFrom). See decision-log rows "GraphDB excluded for Stage 3" and "Stage 3 widened from Strategic+Security to whole ontology". Coverage now spans all 7 UAF domains in a single SPARQL endpoint.

**Concrete tasks.**

- [x] **Triplestore decision.** Stayed on Fuseki and upgraded the reasoner profile from RDFS-Exp to OWL FB. GraphDB excluded by work-environment policy; OWL FB on Jena covers everything in the Stage 3 axiom list (property chains, inverseOf, disjointness, someValuesFrom). RDF4J Server / Stardog Community remain the fallback if DL-complete entailment becomes mandatory.
- [x] **OWL 2 axioms.** Authored as `ontology/uaf-mvo-axioms.ttl`, sibling to the generated MVO. Covers:
  - `owl:someValuesFrom` cardinality restrictions on `CapabilityConfiguration` (every config must have ≥1 OperationalActivity / ResourcePerformer / ResourceRole via `uaf:realisedBy`)
  - `owl:disjointWith` between all UAF domain superclasses (Strategic / Operational / Resource / Service / Personnel / Acquisition / Security) — pairwise
  - `uaf:dominates` as `owl:TransitiveProperty` for dominance between UAF SecurityElements (architectural Security domain). The Java plugin maps the `<<Dominates>>` UML Dependency stereotype to `REL_DOMINATES`, so modellers can express SecurityEnclave-to-SecurityEnclave dominance and the lattice closes automatically in Fuseki. Data-level classification dominance — the TS/S/C/U lattice the DoD Data Marking Plugin applies to ERD Entities — sits in UML, not UAF, and is layered separately via `ontology/uml-data-marking.ttl` (`dm:` namespace, `dm:dominates` property, four named individuals, JOIN via `skos:notation` against `uaftv:securityClassification` literals).
  - **§7 ERD → Resource bridge.** `uaf:Entity rdfs:subClassOf uaf:ResourceInformation`. UAF 1.2 keeps ERD content in the Shared domain (`uaf:Entity rdfs:subClassOf uaf:SharedElement` from codegen), but the records described by an ERD Entity are exactly the payloads of `uaf:ResourceExchange` between systems. The bridge makes every Entity instance automatically a ResourceInformation / ResourceElement so Resource-view queries reach ERD content without the caller knowing it came via Shared. SharedElement is intentionally outside the domain-disjointness web (axioms §2), so the dual parent is consistent. Attribute data types (Oracle VARCHAR2/NUMBER/DATE, SysML ValueType, UML PrimitiveType/Enumeration/DataType) are already materialised as first-class `:Attribute` and `:DataType` nodes by the traverser (#76); no Java change required.
  - `owl:inverseOf` for 15 canonical pairs (`realises ↔ realisedBy`, `tracesTo ↔ tracedBy`, `exhibits ↔ exhibitedBy`, ...). SHACL shapes simplified to forward paths (dropped `sh:inversePath` workarounds) and now rely on the reasoner to materialise the reverse direction.

  Fuseki assembler updated to load the axioms file and run `OWLFBRuleReasoner`. Validator + MCP server bumped to `inference="rdfsowlrl"` to align. Unit tests at `Test/test_owl_axioms.py` (9 cases) and updated `Test/test_shacl_shapes.py`.
- [x] **SHACL shapes scaffolding.** `ontology/shapes/uaf-shapes.ttl` covers language-uniqueness on MVO classes (also closes the hygiene item under "Ontology hygiene" below), Strategic backbone (Capability / CapabilityConfiguration / Vision) and Security structural shapes (SecurityAsset / Control / Risk / Domain). Validator at `ontology/codegen/validate_shacl.py` runs pyshacl with `inference="rdfsowlrl"` to align with Fuseki's OWL FB reasoner. MCP server exposes `validate_shacl()` tool that fetches the live Fuseki dataset via SPARQL CONSTRUCT and returns the violation report. Unit tests at `Test/test_shacl_shapes.py`. Speculative — extend as Stage 3 firms up.
- [x] **SHACL + OWL coverage extended to Operational + Resource.** Stage 3 now spans 4 of 7 UAF domains (Strategic, Security, Operational, Resource). Seven new SHACL NodeShapes (OperationalActivity / Performer / Process; ResourcePerformer / Role / Architecture / Artifact) and four new `owl:someValuesFrom` restrictions (OperationalActivity ⊑ ∃ performedBy . OperationalPerformer, OperationalProcess ⊑ ∃ composedOf . OperationalActivity, ResourcePerformer ⊑ ∃ performs . ResourceElement, ResourceArchitecture ⊑ ∃ composedOf . ResourceElement) — all targeting classes already produced by `UAFStereotypeRegistry`, so they fire over real export data. Unit tests added to `Test/test_shacl_shapes.py` and `Test/test_owl_axioms.py`.
- [x] **SHACL + OWL coverage — final 3 domains (Stage 3 whole-ontology complete).** Service, Personnel, Acquisition added — Stage 3 governance now covers all 7 UAF domains. Ten new SHACL NodeShapes (Service / ServicePerformer / ServiceArchitecture / ServiceRole; Post / Organization / PersonnelActivity; Project / ProjectMilestone / ProjectTheme), four new `owl:someValuesFrom` restrictions in axioms §6 (Service ⊑ ∃ providedBy . ServicePerformer; ServiceArchitecture ⊑ ∃ composedOf . ServiceElement; Post ⊑ ∃ partOf . Organization; Project ⊑ ∃ composedOf . AcquisitionElement), and one new `owl:inverseOf` pair (`providedBy` / `provides`) so the Service shape can use a forward path. The earlier sketch "Post ⊑ ∃ heldBy . Person" was discarded — UAF has no first-class Person class in the registered set; `partOf Organization` is the canonical relation. 46/46 unit tests pass.
- [ ] **Outbound SPARQL `SERVICE` federation.** Demonstrate enrichment by federating from Fuseki out to a public endpoint (Wikidata / DBpedia) — e.g. join UAF Capability concepts to Wikidata equivalents. Reframed from the original "two-triplestore federation" plan: the simplification goal (one SPARQL endpoint locally) makes the second-triplestore interpretation counter-productive. Cross-tool federation lives at the network boundary, not inside the toolbox.
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

**What changes.** Adds the third leg of Gartner's "composite AI" pattern (graph + ML + reasoning + LLM, per Jaffri G00768041 §3.3): a model layer that consumes the SPARQL graph as feature input. The MCP server already provides the LLM leg; the reasoner leg lives in Fuseki/triplestore; the missing leg is supervised or graph-native ML.

**Concrete tasks.**

- [ ] Stand up Neo4j Graph Data Science (GDS) on the LPG side. The Docker image already includes it (`NEO4J_PLUGINS=graph-data-science`). GDS gives PageRank, community detection, betweenness — useful for identifying critical Operational Activities and capability-coverage gaps.
- [ ] Author GDS-driven recommendation algorithms (per Jaffri §3.3): content-based recommendation of ResourceArtifacts that could fill detected capability gaps, using graph-position features.
- [ ] Wire GDS outputs back into the RDF view (write algorithm results as `uafprop:pagerank` annotations on node URIs) so SPARQL queries can sort by relevance.
- [ ] Build a decision-intelligence dashboard (Streamlit/Dash/Grafana) consuming both the SPARQL endpoint and GDS outputs. Targets the "Decision makers" persona in `Ontology-Approach-to-Knowledge.md` §8.
- [ ] **Agent-memory demo (#119).** UAF KG as the long-term semantic memory of an architecture-aware agent. AS-IS vs TO-BE delta + impact closure over the Dassault UAF *Way of Working* sub-package convention (`qualifiedName` substring filter — no registry extension required). Lands five SPARQL queries (phase membership, set-difference, forward impact closure via `realisedBy*`/`tracedBy*`, phase-aware capability gap, Acquisition trace) plus persona landing pages for Enterprise Architects (primary), Solution Architects, Capability Owners, and Decision Makers (secondary). Correspondence rule between AS-IS and TO-BE counterparts: `name + stereotype` within domain, with `TRACES_TO` as the disambiguating override.

**Gating criteria.** No infrastructure gates — Stage 5 is incremental and additive. Realistic trigger is when Stage 2 has been in active use for ≥1 quarter and stakeholder demand for "tell me what I should do, not just what exists" emerges.

---

## Cross-cutting backlog (do at any stage)

These are independent of stage progression. Pick up whichever pays off soonest in your context.

### Traverser & registry coverage

- [x] **Stereotype selection ranks UAF > BPMN > SysML** and walks the general chain to find a registered ancestor. Closes the bulk of #75 — multi-stereotyped Operational/Resource elements no longer collapse to generic SysML `Block`. Shipped via PR #78.
- [x] **Descend into Classifiers, not just Packages.** Internal block diagram parts/ports and activity-owned actions now reach the export. PR #78.
- [x] **Relationship-stereotype map** for `OperationalExchange`, `ResourceInteraction`, `NeedLine` (and Tier-1 additions reconciled from real-world profile diffs: `Implements`, `IsCapableToPerform`, `PerformsInContext`, `MapsToCapability`, `DataAssociation`, the four `*Association` family stereotypes, `Allocate`, `DeriveReqt`, `Copy`, `SequenceFlow`, `MessageFlow`). PRs #78 + #82.
- [x] **Walk attached project modules** by default (opt-out via the `UAFModelTraverser(Project, boolean)` constructor). PR #83 plus the `com.nomagic.ci.persistence` build dependency.
- [x] **Unmatched-stereotype diagnostic** surfaced in `ExportSummaryDialog` as a dedicated tab with copy-to-clipboard. PR #78.
- [x] **Registry reconciliation tooling** — `scripts/registry-diff.groovy` runs in the MSOSA scripting console and diffs the live profile against the registry. PR #81.
- [x] **Data artefact + ERD coverage (#76):** `DataObject` / `DataInput` / `DataOutput` / `DataStore` registered; BPMN `DataInputAssociation` / `DataOutputAssociation` connect data to consuming/producing Tasks; `Entity` / `EntityRelationship` / `EntityRelation` registered; attributes promoted to first-class `:Attribute` nodes via `HAS_ATTRIBUTE` (Option A); `:DataType` synthesised for primitive/enum/datatype targets via `OF_TYPE`; `Association` edges carry `srcMult` / `tgtMult` / `srcRole` / `tgtRole`. Shipped via the #78/#79 cascade.
- [ ] **AssociationClass handling** (last open #76 sub-item). Currently UML `AssociationClass` instances are not specially handled — they emit only the relationship side. Decide: emit both a node and an edge, or document the omission. Low frequency in current UAF profiles, so it can wait.
- [ ] **Live-model regression test.** Acceptance criterion on #75/#76 calls for non-zero counts against a real MSOSA project. Cannot run on the CI box (no MSOSA install). Suggest a manual export-and-check script that runs against a known-good `.mdzip` once a release cut is being prepared, with the counts captured in the PR description.
- [ ] **Further registry tiers.** PR #82 reconciled Tier-1. Tiers 2–N (less common stereotypes flagged by `registry-diff.groovy`) remain as the profile expands.

### Ontology hygiene

- [x] Stop UAF/SysML stereotype name collisions becoming silent RDF collisions. `uafsh:LanguageUniquenessShape` in `ontology/shapes/uaf-shapes.ttl` asserts every class declared with `uafprop:language` has exactly one value in `{UAF, SysML, BPMN}`. Picked up by the Stage-3 SHACL scaffolding.
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
- [ ] Persona-shaped landing pages, per `Ontology-Approach-to-Knowledge.md` §8 / Gartner persona framing — the full set is established by #119: `docs/personas/business-analysts.md`, `docs/personas/capability-owners.md`, `docs/personas/data-architects.md`, `docs/personas/decision-makers.md`, `docs/personas/enterprise-architects.md`, `docs/personas/implementation-teams.md`, `docs/personas/process-owners.md`, `docs/personas/solution-architects.md`. Each carries the queries that persona is most likely to copy-paste at the top. #119 populates `enterprise-architects.md` (primary) plus `solution-architects.md`, `capability-owners.md`, and `decision-makers.md` (secondary); the other four remain stubs until their own Stage 5 demos are scoped (BPMN-process-evolution for Process Owners, ERD/canonical-data evolution for Data Architects, resource-migration for Implementation Teams, requirements-traceability for Business Analysts).

### Visualisation

- [x] **T-Box viewing via TIB-hosted WebVOWL** — Documented `https://service.tib.eu/webvowl/` (the upstream-blessed hosted instance per the WebVOWL README's 2026 update) as the T-Box viewer. Users upload `ontology/uaf-mvo.ttl` (and optionally `uaf-mvo-axioms.ttl`) through the public UI. The ontology is already public in this repo, so a third-party upload is acceptable. Replaces the earlier SPARQL→GraphML exporter — no intermediary steps and no local infra. See the decision log for why self-hosting was explored and rejected.
- [ ] **A-Box visualisation** — currently no shipped path. SPARQL `CONSTRUCT` results from the Fuseki endpoint can be saved as Turtle and rendered ad-hoc by any external tool, but the toolbox does not ship a default viewer for instance graphs after the GraphML exporter was withdrawn (2026-05-26 — see decision log). Re-open if there is concrete demand.

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
| **GraphDB excluded for Stage 3** | 2026-05-22 | Work-environment policy prohibits GraphDB Free/SE/EE despite a local install being used for evaluation. Stage 3 will progress on Apache Jena Fuseki (current engine, Apache 2.0) with a reasoner-profile upgrade rather than introducing a second triplestore. RDF4J Server is the fallback if Jena's rule layer proves insufficient. |
| **SHACL shapes before OWL axioms** | 2026-05-22 | Stage 3 has two complementary work strands (SHACL constraints, OWL 2 axioms). SHACL chosen first because (a) it is reasoner-independent — runs identically on today's RDFS-Exp Fuseki and on tomorrow's OWL FB upgrade, (b) it exposes the actual data gaps that should drive which OWL axioms are load-bearing, and (c) it delivers immediate validation value without committing the project to a stronger reasoner. Speculative scaffolding shipped via PR — Stage 3 OWL axioms remain open. |
| **OWL axioms in sibling file, not codegen** | 2026-05-22 | Considered extending `generate_mvo.py` to emit OWL axioms. Rejected because most Stage 3 axioms reference specific class names (`CapabilityConfiguration`, `StrategicElement`, ...) rather than generic patterns — codegen would have been mostly literal strings. `ontology/uaf-mvo-axioms.ttl` keeps generated and hand-authored cleanly separated, mirroring the SHACL shapes pattern. |
| **uaf:dominates as TransitiveProperty, not propertyChainAxiom** | 2026-05-22 | NEXT-STEPS originally specified the property-chain form `:dominates o :dominates ⊑ :dominates`. The owl:TransitiveProperty form is equivalent for the binary case, shorter to write, and matches how OWL textbooks express security classification dominance. Both forms are computed by OWL FB; chose the idiomatic one. |
| **Reasoner upgrade bundled with axioms** | 2026-05-22 | Could have shipped OWL axioms first and upgraded reasoner later. Rejected — without OWL FB, every axiom in `uaf-mvo-axioms.ttl` is inert (RDFS-Exp ignores `owl:inverseOf`, `owl:disjointWith`, `owl:TransitiveProperty`, `owl:someValuesFrom`). Bundling means the axioms actually fire from day one. |
| **Stage 3 widened from Strategic+Security to whole ontology** | 2026-05-24 | Stage 3's original scoping (Strategic + Security only) was a consequence of the discarded "separate native triplestore for the governed slice" plan. With reasoning collapsed into Fuseki itself (decision row above), the artificial scope bound is gone — and 5 of 7 domains having no governance constraints was an accidental gap rather than a deliberate one. Extended SHACL + OWL coverage to Operational + Resource in the same PR. Service / Personnel / Acquisition follow when their canonical invariants are agreed. |
| **Federation reframed to outbound `SERVICE` only** | 2026-05-24 | The original federation bullet ("triplestore can query the Fuseki dataset for unmaterialised classes, and vice versa") assumed Stage 3 would add a second triplestore. With the decision to stay on Fuseki, that interpretation works against the explicit goal of *fewer* platforms in the knowledge-graph stack. Reframed to outbound SPARQL `SERVICE` federation to external endpoints (Wikidata, future BPMN ontology, canonical data registries) — which is the actual enterprise-integration value SPARQL federation delivers without adding a second internal engine. |
| **Visualisation via static exporter, not a hosted viz service** | 2026-05-24 | Considered options to fill the GraphDB-Workbench-shaped gap: (a) embed a long-running browser viz container (yasgui/LodLive/Cytoscape.js page served from nginx), (b) a heavyweight engine swap to Stardog Studio / AllegroGraph Gruff, or (c) a thin static exporter. Chose (c) — `ontology/codegen/sparql_to_graphml.py` writes GraphML on demand, users open it in Cytoscape Desktop / yEd / Gephi. Zero new long-running infra, zero vendor lock-in, no impact on the simplification goal. T-Box viewing delegated to Protégé Desktop and webvowl.dev, both free and external. |
| **Static GraphML exporter withdrawn — self-hosted WebVOWL only** | 2026-05-26 | Reversed the 2026-05-24 decision after use revealed the SPARQL→GraphML→desktop-tool workflow had too many intermediary steps to be worth keeping (run `dump_to_rdf` → run `sparql_to_graphml` → save file → open in Cytoscape/yEd/Gephi). Deleted `ontology/codegen/sparql_to_graphml.py`, the four `ontology/visualisations/queries/*.sparql` presets, the visualisations README, and `Test/test_graphml_export.py`. Initial intent was to add a WebVOWL container to `docker-compose.fuseki.yml`, but see the next row. |
| **Self-hosting WebVOWL abandoned — TIB-hosted only** | 2026-05-26 | Explored three layers of self-hosting before giving up: (1) `image:` from Docker Hub — failed, no published official image; (2) `build.context:` pointed at the upstream git URL — failed, Compose v2 on Windows rejects `https://` as a build context; (3) local multi-stage `Dockerfile` rebuilding from the upstream npm source — got further but uncovered that the upstream pre-built WAR bundled *two* components, **WebVOWL** (the JS frontend, what `npm install` produces) and **OWL2VOWL** (a separate Java/Maven REST service that converts OWL/TTL → VOWL JSON). Rebuilding both from source plus an nginx `proxy_pass` to wire them together is materially more intermediary steps than the GraphML pipeline this PR pair set out to remove. Removed the `webvowl` service from `docker-compose.fuseki.yml`; pointed users at <https://service.tib.eu/webvowl/> (the upstream-blessed hosted instance per the WebVOWL README's 2026 update). Acceptable because `uaf-mvo.ttl` is already public in this repo. No A-Box viewer is shipped by default; re-open if concrete demand reappears. |
| **Data Marking split into UML slice** | 2026-05-26 | Considered wiring `uaf:dominates` end-to-end by adding `REL_DOMINATES` + a stereotype mapping in the Java registry. Rejected once it became clear that the actual dominance use case sits in UML, not UAF: DoD classification levels (TS/S/C/U) are applied to ERD Entities via the Data Marking Plugin's `securityClassification` stereotype, not between UAF SecurityElements. Split into a separate ontology slice `ontology/uml-data-marking.ttl` under a non-UAF namespace (`dm:`); MSOSA traverser is unchanged because `tv_securityClassification` is already captured via generic tagged-value flattening; SPARQL queries JOIN literal markings against `skos:notation` on the four DoD lattice individuals (`dm:TopSecret/Secret/Confidential/Unclassified`). `uaf:dominates` retained for the architectural case (one SecurityEnclave dominating another). 10 new unit tests at `Test/test_data_marking.py`. |
| **REL_DOMINATES wired separately for UAF Security** | 2026-05-26 | The Data Marking split confirmed that `uaf:dominates` is still the right shape for the *architectural* Security case (SecurityEnclave-to-SecurityEnclave dominance for accreditation reasoning). Followed up by adding `REL_DOMINATES = "DOMINATES"` to `UAFRelationshipDTO` and mapping the `<<Dominates>>` UML Dependency stereotype in `UAFModelTraverser.RELATIONSHIP_STEREOTYPE_MAP`. Modellers apply `<<Dominates>>` to a Dependency between two SecurityElements; the existing `owl:TransitiveProperty` axiom closes the lattice in Fuseki. `<<Dominates>>` chosen as a conventional UML stereotype name — single-line change if a source profile turns out to use a different name. 124 Java tests pass. |
| **ERD Entity → ResourceInformation bridge** | 2026-05-26 | UAF 1.2 places `uaf:Entity` under the Shared domain by codegen, which is taxonomically correct but leaves ERD content unreachable from Resource-view queries. Real programmes use ERD entities as the payloads of `uaf:ResourceExchange` between systems. Added `uaf:Entity rdfs:subClassOf uaf:ResourceInformation` as axioms §7 — every Entity now classifies as ResourceInformation / ResourceElement under OWL FB closure while keeping its SharedElement parent (SharedElement is intentionally outside the disjointness web). The bridge lives in `uaf-mvo-axioms.ttl` rather than the generated `uaf-mvo.ttl` so MVO regeneration does not erase it. Attribute data types (Oracle / SysML / UML) are already first-class `:Attribute` + `:DataType` nodes via #76, so no traverser change is needed. 3 new unit tests in `Test/test_owl_axioms.py`. |
