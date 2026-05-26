
[[Bi-directional Ontology for UAFML, UML, BPMN & SysML]]

Reviewed by - [[Agent--mbse-uaf-architect]]
# Ontology Approach to Knowledge

## A Strategy Report for Representing UAF 1.2 Enterprise Architecture as a Knowledge Graph

**Document status:** Strategic analysis — for review by enterprise architects, solution architects, data architects, decision makers, and implementation teams.
**Scope:** The MSOSA-Toolbox UAF 1.2 → Neo4j pipeline and its evolution toward a formal ontology-backed knowledge graph.
**Date:** 2026-05-18

---

## 1. Executive Summary

The MSOSA-Toolbox currently exports UAF 1.2 architecture from Catia Magic MSOSA into a **Labelled Property Graph** (LPG) hosted on Neo4j, with the UAF metamodel encoded as pre-seeded `:Stereotype`, `:Domain`, and `:ArchitectureLayer` nodes that exported instance nodes link to via `:INSTANCE_OF`. This is a pragmatic and operational approach, but it is not a *formal* ontology. The semantics of the UAF Domain Metamodel (DMM) are enforced by convention (the `UAFStereotypeRegistry` source file and disciplined Cypher), not by a type system that can perform inference, classification, or consistency checking.

This report compares three ontology approaches:

| # | Approach | Storage / Query | Inference | Posture |
|---|---|---|---|---|
| 1 | **RDF Native** | Triplestore, SPARQL 1.1 | Full OWL 2 reasoning | **User's stated preference** |
| 2 | **RDF Virtual / OBDA** | LPG or RDBMS + virtual SPARQL view | T-Box only | Overlay on current LPG |
| 3 | **Labelled Property Graph** | Neo4j, Cypher | None (procedural only) | **Current state** |

It places these approaches in the context of Gartner's May 2025 research note *How to Build Knowledge Graphs That Enable AI-Driven Enterprise Applications* (Jaffri, G00768041), and analyses each against the UAF MBSE programme dimensions that matter for an enterprise rollout: metamodel fidelity, cross-domain traceability, reasoning value, enterprise integration, stakeholder fit, and lifecycle posture under ISO/IEC/IEEE 15288 and the INCOSE SE Handbook.

**Headline conclusion.** RDF Native is the correct long-term target for a serious UAF programme that will face accreditation, cross-tool federation, or formal compliance obligations. It is *not* the correct first move from the current state. A staged migration via an RDF Virtual (OBDA) overlay — following Gartner's **Minimum Viable Ontology / Minimum Viable Graph** cadence — preserves every existing investment (Java plugin, Docker stack, MCP server), delivers SPARQL interoperability immediately, and positions the programme for a full migration when (and only when) governance maturity, team skills, and stakeholder demand justify it. The value narrative driving that migration must lead with **AI use cases** (LLM-fronted decision intelligence, semantic search, capability gap discovery) rather than "joining up architecture data" — a framing that Gartner explicitly identifies as the most common failure mode for knowledge graph initiatives.

---

## 2. Background — Knowledge Graphs, Ontologies, and Why They Differ

A **knowledge graph** is a graph-structured representation of entities, the relationships between them, and the meaning attached to those entities and relationships. The "knowledge" qualifier is doing real work — it implies that the graph carries *semantics*, not only structure. Two entities labelled `OperationalActivity` in different graphs should mean the same thing only if both graphs share a vocabulary that defines what `OperationalActivity` is.

An **ontology** is the formalised vocabulary. In its strongest form (OWL 2 DL) an ontology specifies classes, properties, cardinality and value restrictions, disjointness, equivalence, and the rules by which new facts can be inferred from asserted facts. A weaker form ("ontology-as-data") simply names the vocabulary without making the semantics machine-checkable.

The three approaches in this report differ on **where the ontology lives** and **what is done with it**:

- **RDF Native** — the ontology is a first-class artefact (OWL T-Box). Data is RDF triples (A-Box). The reasoner uses the ontology to materialise new triples, check consistency, and classify individuals.
- **RDF Virtual (OBDA)** — the ontology is still a first-class OWL artefact. Data lives in an underlying store (LPG, RDBMS) and is projected as virtual RDF via R2RML or RML mappings. The reasoner is constrained to what the mappings can deliver.
- **Labelled Property Graph** — there is no formal ontology. A vocabulary exists by convention in source code (in our case, `UAFStereotypeRegistry`) and is reflected as nodes in the graph, but neither subsumption nor cardinality is machine-enforced.

The choice between these is not primarily a technology choice — it is a choice about how much semantic rigour the programme intends to demand, and from whom.

---

## 3. Gartner's Positioning

**Source.** Afraz Jaffri, *How to Build Knowledge Graphs That Enable AI-Driven Enterprise Applications*, Gartner research note **G00768041**, 22 May 2025 (14 min read).

The Jaffri note is the most directly relevant Gartner research to this programme because it reframes the knowledge graph conversation from "graph database technology choice" to **"agile, business-driven ontology development for AI-enabled enterprise applications."** Its core findings and recommendations land squarely on the choices in front of the MSOSA-Toolbox programme.

### 3.1 Gartner's three key findings

1. **Business cases must lead with AI applications, not data integration.** Gartner: *"Data and analytics leaders fail to get stakeholder buy-in for knowledge graph initiatives because they form business cases solely on the basis of connecting data silos, without addressing the opportunities for delivering AI applications."* For this programme that means the value narrative is **not** "we have a UAF knowledge graph that joins our architecture data" — it is "we have a UAF knowledge graph that drives LLM-augmented impact analysis, capability-gap discovery, and decision-intelligence dashboards."
2. **Composability eases semantic consistency.** Gartner: *"The flexible, composable and open nature of knowledge-graph-based data delivery eases the challenge of ensuring the semantic consistency of data across the enterprise. This allows business users, software engineers and data scientists to find, understand and use the data they need."*
3. **Knowledge graphs are an AI asset, alone or in composite.** They function *"as a stand-alone AI asset — or be used in conjunction with machine learning models in a composite AI approach — to deliver knowledge discovery, search and retrieval, recommendation, and decision intelligence platforms."*

### 3.2 Gartner's three recommendations

- **Target up to three functional areas** with focus on **discovery, search and recommendation**.
- **Decrease time to value by taking an agile approach, reusing industry-standard ontologies, and adapting with Minimum Viable Ontologies (MVO) and Minimum Viable Graphs (MVG)**.
- **Increase adoption across personas by creating knowledge-graph-based services and integrations**.

### 3.3 The three high-impact applications — mapped to UAF

| Gartner application | What it means in a UAF programme |
|---|---|
| **Semantic Search / Question Answering** | Architects ask the LLM "which Capabilities are realised by deprecated ResourceFunctions and which OperationalActivities depend on them?" The graph supplies grounded multi-hop answers across the UAF grid that document-only retrieval cannot. |
| **Knowledge Discovery** | Discover previously hidden traceability: orphaned OperationalActivities, redundant CapabilityConfigurations, security-classification conflicts. Gartner specifically calls out **"graph analytics, machine learning and reasoning"** as composite AI — directly aligning with the GDS + OWL reasoning + LLM triad. |
| **Recommendation Engines** | For UAF programmes: recommend ResourceArtifacts to fill capability gaps; recommend reuse candidates across architectures; recommend Personnel roles that match a CapabilityConfiguration's competence profile. Gartner notes that **content-based** (graph-position-based) recommendation, not collaborative filtering, is the right pattern here. |

Gartner argues that **decision intelligence platforms** — which incorporate aspects of all three — *"are likely to become the most popular knowledge-graph-driven application."* This is precisely the trajectory of the MSOSA-Toolbox: from a static export pipeline today, to an LLM-fronted decision-intelligence surface for UAF programmes tomorrow.

### 3.4 The MVO / MVG approach — and why it changes the migration plan

Gartner is **explicit** that an enterprisewide-schema-first approach is a mistake: *"Many organizations seek to define an enterprisewide schema, ontology or taxonomy first. This is a mistake. Such endeavors are costly, time-consuming, filled with disagreement and, in many cases, stopped before any value can be shown or delivered."*

The recommended pattern is:

- **Reuse existing standards, schemas and ontologies as starting points.**
- **Extract key terms via data mining / entity extraction / data profiling.**
- **Add handcrafted rules, entity attributes and relationships from business glossaries and data dictionaries.**

For this programme the MVO starting point is **already available**: the OMG UAF 1.2 Domain Metamodel is itself a published, industry-standard ontology (in SMOF/MOF form). The MVG starting point is **already populated**: the existing Neo4j export of MSOSA models *is* the instance dataset. The MVO/MVG approach therefore translates here as:

- **MVO:** transform `UAFStereotypeRegistry` plus the OMG UAF DMM into an OWL T-Box, scoped initially to the domains in active use (Strategic + Operational at minimum) — not all ten UAF domains at once.
- **MVG:** the existing Neo4j export, projected as RDF via OBDA, becomes the initial instance graph against that MVO. Expand the ontology coverage iteratively as new use cases (Security accreditation, Personnel competence, etc.) demand additional UAF domains.

This is a Gartner-endorsed migration cadence, and it materially **strengthens** the OBDA-first staged plan in §12 of this report.

### 3.5 The persona framing — directly applicable

Gartner's Figure 4 maps four delivery channels to four personas:

| Persona | Delivery channel |
|---|---|
| Data scientists | Python library |
| Data engineers | Query interface (SPARQL / Cypher) |
| Domain experts | Visual interface |
| Software engineers | REST / GraphQL API |

This maps almost one-to-one onto the stakeholder fit analysis in §8 below. Gartner also explicitly notes that **"the integration of generative AI-based assistants in many graph platforms has accelerated the time to reach value and gain actionable insights from graph data"** — direct external validation for the MCP integration as a first-class delivery channel for this programme.

### 3.6 The data-integration evidence

Gartner cites the *2023 AI in the Enterprise Survey*: **79% of organisations classed as mature AI organisations were effective at data integration, versus 66% of other organisations**. The implication is that knowledge-graph-driven data integration is correlated with AI maturity — useful evidence for stakeholder discussions where the business case for migrating beyond LPG is being challenged on ROI grounds.

### 3.7 Implications for this programme

Gartner's positioning materially **endorses** three judgements in this report:

1. **Lead with AI use cases, not "join up data."** The MCP integration is not a side feature — it is the primary value narrative the business case should be built around.
2. **MVO/MVG is the right migration cadence.** Do not attempt a big-bang UAF ontology in OWL. Stand up the smallest useful T-Box (Strategic + Operational), project the existing LPG via OBDA, iterate per use case.
3. **Composite AI is the destination.** Graph analytics (Neo4j GDS) + reasoning (OWL via OBDA or native) + an LLM is exactly the composite AI pattern Gartner identifies as more robust than any single technique.

Gartner does **not** take a strong position on RDF Native versus property graph at the technology layer — the note is deliberately ontology-and-application-led, not vendor-led. That neutrality is itself useful: it lets this programme defer the RDF Native commitment until use-case pressure justifies it, rather than treating the technology choice as the strategic question.

---

## 4. Approach 1 — RDF Native (Formal Ontology, Triplestore, SPARQL)

### 4.1 What it means concretely

The UAF DMM is expressed as an OWL 2 ontology. Every metaclass (`Capability`, `OperationalActivity`, `OperationalPerformer`, `ResourceArtifact`, `CapabilityConfiguration`, …) becomes an `owl:Class`. Every UAF association becomes an `owl:ObjectProperty` with declared domain, range, and (where applicable) cardinality restrictions, inverse properties, and property chains. Every UAF tagged value becomes an `owl:DatatypeProperty`. The 10×10 UAF grid (Strategic/Operational/Resource/Services/Personnel/Security × Taxonomy/Structure/Connectivity/Processes/States/Sequences/Information/Parameters/Constraints/Roadmap) is captured as a class hierarchy with domain and layer annotations.

Exported instance nodes become RDF individuals (the A-Box). The MSOSA plugin's job changes from emitting Cypher to emitting RDF (Turtle, JSON-LD, or N-Triples). A triplestore — GraphDB, Stardog, Apache Jena Fuseki, AWS Neptune in RDF mode — hosts the combined T-Box + A-Box and exposes a SPARQL 1.1 endpoint.

### 4.2 Fit to UAF 1.2 metamodel

RDF Native is the **highest-fidelity** representation of the UAF DMM. The OMG UAF specification's normative metamodel is expressed in SMOF/MOF — translating MOF to OWL is a well-trodden path (OMG ODM, Ontology Definition Metamodel). UAF subsumption hierarchies become first-class: an `AircraftManifest rdfs:subClassOf LogisticsDocument rdfs:subClassOf InformationElement`. A SPARQL query for "all exchanges of any logistics document" needs no manual denormalisation — the reasoner materialises subtype membership.

Cardinality and range constraints are enforceable in OWL 2 DL. Violations surface as inconsistencies during reasoning — something neither LPG nor virtual mapping provides. If a `CapabilityConfiguration` lacks the required `realises` relationship to a `Capability`, the reasoner flags the model, not the analyst.

### 4.3 Cross-domain traceability

The canonical UAF chain `Capability → CapabilityConfiguration → OperationalActivity → ServiceSpecification → ResourceFunction → SystemComponent` becomes a SPARQL property path of length five. `owl:inverseOf` and property chains make reverse traversal first-class — "which capabilities depend on this deprecated resource function?" is a single SPARQL query with materialised inverse links. Gap analysis ("which Capabilities in Cv-1 have no downstream resource allocation?") is a `SPARQL NOT EXISTS` clause over a semantically sound graph.

### 4.4 Reasoning value — what becomes newly answerable

This is where RDF Native creates capability that the LPG genuinely cannot replicate:

- **Consistency checking.** Contradictions between a node's `domain` annotation and the domain of its related elements surface as DL inconsistencies, automatically.
- **Subsumption for roles and competences.** UAF Personnel domain hierarchies (`SeniorSystemsEngineer ⊑ SystemsEngineer ⊑ EngineeringRole`) classify automatically — querying for all `EngineeringRole` performers returns all subtypes without manual `SPECIALISES` traversal.
- **Security classification dominance.** UAF Security domain classification levels and compartments can be encoded as OWL property chains expressing dominance (TS/SCI dominates TS dominates SECRET). A reasoner can automatically determine whether a resource's security policy is consistent with the classification of information it handles — directly relevant to security accreditation.
- **Completeness restrictions.** OWL `owl:someValuesFrom` restrictions on `CapabilityConfiguration` (must have at least one realising activity, one resource, one role) surface incomplete configurations as classification failures.

### 4.5 Enterprise integration and federation

SPARQL 1.1 Federation (the `SERVICE` keyword) is RDF's greatest enterprise integration strength. A single query can span the UAF knowledge graph, a canonical data registry (in OWL), a BPMN ontology (the OMG BPMN metamodel has an OWL form), and an enterprise master data catalogue. This is operational today via Stardog Virtual Graphs, GraphDB FedX, or vanilla SPARQL federation.

For BPMN integration: every UAF `OperationalActivity` maps to a `bpmn:Task`; every UAF `InformationExchange` maps to `bpmn:DataObject` on `bpmn:MessageFlow`. With shared URIs or `owl:sameAs` alignment, "which BPMN processes are affected by changes to this Operational Activity?" is a single federated SPARQL query.

### 4.6 Risks and pitfalls (UAF-specific)

- **Tooling shift.** The Java plugin must emit RDF rather than Cypher. The MCP server (currently Cypher-only) needs a SPARQL counterpart. Neither is intrinsically difficult, but both are real engineering work.
- **Reasoner scalability.** Full OWL 2 DL reasoning over tens of thousands of UAF individuals can be slow. Practical programmes choose their OWL profile carefully — OWL 2 EL for scalable subsumption, OWL 2 RL for rule-based inference, full DL only for offline correctness analyses.
- **T-Box governance.** Every MSOSA profile change (new stereotypes, renamed associations) requires a versioned ontology release with regression testing of consistency. This is a real recurring cost that the LPG model does not have — the LPG simply edits `UAFStereotypeRegistry.java` and re-deploys.
- **Query-language barrier.** SPARQL is harder for non-specialists than Cypher. Without investment in training or a query-abstraction layer, productivity drops.
- **Open World Assumption (OWA).** RDF/OWL is open-world: missing facts do not mean false facts. Programmes that treat OWL reasoning as a closed-world correctness oracle will eventually be wrong in ways that are silent and hard to detect.

---

## 5. Approach 2 — RDF Virtual / Mapped (OBDA)

### 5.1 What it means concretely

Ontology-Based Data Access (OBDA) keeps the LPG (or relational store) as the system of record and projects virtual RDF on demand. The OWL T-Box exists exactly as in Approach 1. Mappings (R2RML for relational, RML/Ontop-Neo4j for property graphs) translate SPARQL queries into native Cypher or SQL at query time. No RDF triples are physically materialised at scale.

### 5.2 Fit to UAF 1.2 metamodel

T-Box fidelity is identical to Approach 1 — the ontology is the same. **A-Box fidelity depends entirely on mapping quality**. With ~50 stereotype labels and 28 relationship types in the current plugin, authoring complete and correct mappings is significant but tractable. The hard limit is that no mapping can supply A-Box facts the underlying graph does not store; if MSOSA's export omits `SPECIALISES` chains for competences, no OBDA layer can fabricate them.

### 5.3 Cross-domain traceability

SPARQL queries run at the same semantic level as RDF Native. Performance is the differentiator: each multi-hop SPARQL path may translate to multiple round-trip Cypher queries. Five-hop traceability is acceptable interactively but unsuitable for large batch analyses without caching.

### 5.4 Reasoning value

T-Box reasoning is supported (class subsumption, property hierarchies). A-Box reasoning over inferred individuals is **incomplete** — the reasoner can tell you `SeniorSystemsEngineer` ⊑ `SystemsEngineer` but cannot materialise individuals via property chains unless explicit mappings exist for each level. Consistency checking that requires materialising inferred A-Box facts is largely out of reach.

For programmes whose primary need is **vocabulary alignment and SPARQL-accessibility** of the existing graph rather than novel inference, this trade-off is entirely acceptable — and often optimal, because the LPG investment is preserved unchanged.

### 5.5 Enterprise integration

This is OBDA's strongest argument in the UAF context. The Neo4j graph remains the operational system of record. The existing Java plugin and Docker stack are unchanged. The MCP server continues to query Cypher directly. An OBDA layer provides a **standards-based SPARQL endpoint** for consumption by adjacent systems (GRC platforms, capability management tools, SPARQL-native BI, DoDAF/MODAF reporting) without duplicating data or migrating storage.

For data architects managing canonical ERDs alongside the UAF model, Ontop or Stardog Virtual Graphs can expose the UAF graph and the canonical data model through one SPARQL endpoint, federated logically without a physical move.

### 5.6 Risks and pitfalls

- **Mapping maintenance.** Every Neo4j schema change requires a corresponding mapping update — a hidden governance cost that grows with the registry.
- **Performance ceilings.** OBDA degrades non-linearly with query complexity. Models above ~50k nodes with multi-hop SPARQL hit timeouts without partial materialisation strategies.
- **Reasoning incompleteness is invisible.** The gap between what the T-Box promises and what virtual inference delivers is a frequent source of incorrect conclusions in teams that are not deeply OBDA-literate.
- **Not a stepping stone to RDF Native.** OBDA is an alternative architecture, not a migration phase. Investment in mappings does not translate directly into RDF Native artefacts. (This is the most important pitfall to internalise if the long-term target is full RDF Native — see §10.)

---

## 6. Approach 3 — Labelled Property Graph (Current State)

### 6.1 What it means concretely

The current MSOSA-Toolbox is a mature LPG implementation. UAF stereotypes become Neo4j labels (`:UAFElement:Capability`, `:UAFElement:OperationalActivity`, …). UAF relationships become typed Neo4j edges from a 28-type canonical vocabulary (`REALISES`, `TRACES_TO`, `PERFORMS`, …). Tagged values become `tv_*` node properties. The pre-seeded metamodel (`:Stereotype`, `:Domain`, `:ArchitectureLayer`) provides a lightweight "ontology-as-data" layer linked via `:INSTANCE_OF`.

Semantics are enforced by `UAFStereotypeRegistry` discipline and the `sanitiseLabel()` / `sanitiseRelType()` hygiene in `Neo4jCypherBuilder` — by code, not by a type system.

### 6.2 Fit to UAF 1.2 metamodel

LPG captures the UAF grid **structurally but not formally**. The `domain` and `layer` properties on each node encode grid position; dual labels enable efficient type-specific queries. What LPG cannot enforce is the DMM's structural rules — cardinality, disjointness, inverse property consistency. Those must be implemented as application-layer validation (post-export Cypher assertions) or accepted as unchecked conventions. The `:INSTANCE_OF` chain to the metamodel is pragmatic but shallow — it does not propagate property inheritance or cardinality restrictions.

### 6.3 Cross-domain traceability

Cypher's pattern matching is expressive and performant for the queries that matter in practice. The five-hop capability-to-component chain is a readable `MATCH` with relationship-type filters; Neo4j's native engine executes these efficiently with index-backed lookups. **The gap is gap analysis at scale** — analysts must know the exact relationship types and conventions to construct queries correctly. There is no type-system safety net.

### 6.4 Reasoning value

Essentially none in the formal sense. What the LPG offers instead is **procedural reasoning** via Neo4j's Graph Data Science (GDS) library — shortest path, PageRank, community detection, betweenness centrality. For network analysis of operational connectivity (Ov-2), identifying critical nodes, or clustering resources by connectivity, GDS delivers insight that OWL reasoning does not. This is a genuine capability and should not be dismissed; enterprise architecture is not only about formal correctness.

### 6.5 Enterprise integration

LPG integrates naturally with developer-facing tooling (Spring Data Neo4j, py2neo, the existing MCP server) but poorly with standards-based enterprise infrastructure (data catalogues, GRC, SPARQL endpoints expected by DoDAF/MODAF tools). The MCP interface is a pragmatic, valuable integration for AI-augmented architecture analysis but is not yet a standards-based federation point.

---

## 7. Comparative Summary

| Dimension | RDF Native | RDF Virtual (OBDA) | LPG (Current) |
|---|---|---|---|
| UAF DMM semantic fidelity | **High** — OWL T-Box enforces metamodel constraints | Medium — T-Box exists, A-Box partial | Low — convention-based |
| Cross-domain traceability | **Excellent** — SPARQL property paths, inverses | Good — SPARQL, multi-hop perf limits | Good — Cypher patterns, no type safety |
| Formal reasoning (OWL) | **Full** — consistency, subsumption, role chains | T-Box only — A-Box incomplete | None |
| Gap analysis automation | **High** — SPARQL NOT EXISTS + inference | Medium — bounded by mappings | Manual — analyst-authored Cypher |
| Enterprise federation | **High** — SPARQL SERVICE federation | High — adds SPARQL to LPG | Low — proprietary, bespoke connectors |
| Graph algorithms (GDS) | Not native — needs LPG bridge | Not native | **Full** — Neo4j GDS |
| Tooling maturity | High — GraphDB, Stardog, Jena, Protégé | Medium — Ontop mature, Ontop-Neo4j newer | High — mature Neo4j ecosystem |
| Query accessibility | Low — SPARQL learning curve | Low — same barrier | **High** — Cypher widely known |
| Governance overhead | High — versioned OWL T-Box, profile mgmt | Medium — mapping rules | Low — registry file |
| Migration cost from current | High — new export, new MCP path | Low–Medium — overlay only | Zero — already here |
| MCP fit (today) | Requires SPARQL-MCP bridge | Unchanged | **Native** |
| Best for | Compliance, accreditation, cross-tool federation | Strong LPG investment + SPARQL interop need | Concept/dev phase, AI-augmented analysis |

---

## 8. Stakeholder Fit Analysis

### Decision makers
Cv-1 capability taxonomy views, Cv-3/Pv-2 roadmap gap summaries, and risk-weighted impact assessments are needed at this level. All three approaches can support dashboard-level reporting. **RDF Native** has the edge where enterprise GRC or portfolio platforms expect SPARQL. **LPG** has the edge where the primary consumption interface is conversational AI (an LLM via MCP) — an increasingly compelling model for strategic narrative generation.

### Enterprise and solution architects
**RDF Native** decisively supports impact analysis: "if I remove this OperationalActivity, what Capabilities are degraded, what Services lose their basis, what ResourceFunctions are stranded?" — a single SPARQL statement with OWL inverses and role chains. In LPG the same query requires careful multi-direction Cypher that the architect must construct without type-system help. The cognitive load delta is significant at scale.

### Data architects
**RDF Native** is the natural choice. OWL's class hierarchy directly models the canonical data model. `owl:equivalentClass` and `rdfs:subClassOf` express canonical/logical/physical entity mappings that ERD tooling currently captures only in prose. The UAF Information domain (Iv-x: `InformationElement`, `LogicalDataModel`, `PhysicalDataSchema`) maps directly to OWL class hierarchies with property restrictions expressing data structure. Orders of magnitude more expressive than LPG.

### Process owners (BPMN)
**OBDA** is attractive: BPMN models stay in their native tool, the UAF model stays in Neo4j, and the OBDA layer provides a unified SPARQL view over both. Process owners are not forced into a new query language — their BPMN tooling is unchanged while the architecture team gains federated traceability into operational activities and information exchanges.

### Implementation teams
**LPG** wins without reservation. Cypher is significantly more accessible than SPARQL for engineers who are not ontology specialists. The existing Java plugin, Docker compose, and Python MCP server represent a working, tested, maintainable system. RDF Native imposes a technology shift whose benefits are not felt at the implementation layer.

### Business analysts
A coherent, governed vocabulary helps most when authoring requirements and capability statements. **RDF Native**'s T-Box provides this with formal versioning rigor. However, BAs' tooling (Confluence, DOORS, Jira) does not speak SPARQL or OWL natively — the practical interface for BAs will be a purpose-built front-end or structured export regardless of underlying approach. So the choice matters less here than it does for architects and data specialists.

---

## 9. Lifecycle Alignment — ISO/IEC/IEEE 15288 and the INCOSE SE Handbook

ISO/IEC/IEEE 15288 Section 6.4 (Architecture Definition Process) requires that architecture models be **consistent, feasible, and complete**. OWL consistency checking directly supports the "consistent" requirement. OWL completeness restrictions (every `CapabilityConfiguration` must have at least one realising element) support "complete." Neither LPG nor OBDA provides automated enforcement of these properties — only RDF Native does.

| Lifecycle Stage | Best Approach | Rationale |
|---|---|---|
| Concept | **LPG** | Rapid modelling, iteration, MCP for exploration; formal governance overhead unjustified |
| Development | **LPG** or **OBDA** | Capability gap analysis emerging; OBDA adds SPARQL accessibility without migration |
| Production / Integration | **OBDA** or **RDF Native** | Cross-tool federation, compliance reporting, formal consistency checking needed at scale |
| Utilisation | **RDF Native** | Security accreditation, operational readiness, configuration management benefit from OWL inference |
| Retirement / Disposal | **RDF Native** | Decommissioning impact analysis (capability loss, resource un-allocation) benefits from full reasoning |

The INCOSE SE Handbook 5th Edition (2023) emphasises **digital engineering continuity** — an unbroken thread of architectural evidence from concept through retirement. RDF Native is the only approach that supports this thread with machine-checkable consistency at every stage. LPG supports it operationally; OBDA supports it for read-side federation.

---

## 10. Recommendation Framework

### Choose RDF Native when
- The programme faces **formal compliance or accreditation** (safety cases, security accreditation, regulatory conformance) where model consistency must be demonstrable, not merely asserted.
- The enterprise federates **>3 modelling tools** whose outputs must be queryable from a single endpoint — SPARQL federation handles tool heterogeneity elegantly.
- The data architecture team aligns UAF Information-domain views with **canonical ERDs and master data**.
- The UAF model is expected to grow **beyond ~50k elements** across multiple programmes, where LPG label conventions become brittle without a formal type system.
- A **dedicated ontologist or knowledge engineer** is available to govern the T-Box.

### Choose RDF Virtual (OBDA) when
- The existing LPG investment is significant and the primary driver is **SPARQL interoperability** with adjacent enterprise tools.
- The programme must expose the UAF graph to DoDAF/MODAF reporting tools or GRC platforms that expect SPARQL endpoints, **without a full migration**.
- Reasoning needs are limited to **T-Box subsumption** (competence hierarchies, classification levels) and do not require A-Box consistency checking.
- The programme cannot yet absorb the governance overhead of a fully managed OWL ontology.

### Stay with LPG when
- The programme is in **concept or early development** — pragmatic benefits of Cypher and the MCP interface outweigh OWL rigour at this stage.
- The primary use case is **AI-augmented architectural analysis** (an LLM querying the graph) rather than formal compliance or cross-tool federation.
- The team lacks ontology expertise and cannot realistically govern an OWL T-Box.
- **Rapid iteration on model structure is expected** — schema changes in LPG are dramatically cheaper than ontology versioning.

---

## 11. Honest Critique of the RDF Native Preference

The preference for RDF Native is architecturally well-founded and, for the long-term trajectory of a serious enterprise UAF programme, almost certainly correct. The UAF DMM is a formal MOF metamodel — it was designed to be expressed as a formal ontology. The OMG UAF standard treats the SMOF/MOF metamodel as the normative representation. Working in LPG is, from a standards perspective, a pragmatic approximation of the intended semantic model.

Three risks attend acting on this preference now:

**Risk 1 — Premature formalisation.** MSOSA-Toolbox is at v1.0.1-Preview. The stereotype registry is the single source of truth and is still evolving as MSOSA profile names are verified and new stereotypes are added. Migrating to RDF Native before the vocabulary is stable means versioning an OWL T-Box against a moving target. *Stabilise the LPG schema first, then generate the OWL T-Box from the stabilised registry as a one-time transformation.*

**Risk 2 — The MCP integration is a genuine competitive advantage.** The Python MCP server lets architects interrogate the full UAF model in natural language, generate Cypher, and receive AI-synthesised architectural narratives. Migrating to RDF Native without a working SPARQL-side MCP tool would degrade this capability. The migration must be paced to preserve the MCP interface — either by maintaining a Cypher read replica or by building a SPARQL MCP server (Apache Jena Fuseki with an MCP wrapper is feasible).

**Risk 3 — Reasoning correctness depends on ontology-competent operators.** OWL 2 DL reasoning produces results that can be subtly wrong in ways that are hard to detect without deep expertise — particularly because of the Open World Assumption. Programmes that treat OWL as a black-box oracle will eventually make incorrect architecture decisions based on silent inference failures. This is not a reason to avoid RDF Native; it is a reason to **invest in ontology training before migrating**.

---

## 12. Recommended Migration Path — An MVO / MVG Cadence

A staged migration is materially safer than a big-bang replacement, and it directly applies the Gartner MVO/MVG pattern. Each stage delivers value, is reversible, targets a defined AI use case, and preserves the current investment until the next stage's preconditions are met.

### Stage 1 — Stabilise the LPG and pick the first AI use case (current quarter)
- Continue with LPG.
- Stabilise `UAFStereotypeRegistry` — verify every name against MSOSA scripting console output.
- Ensure all 28 relationship types are applied consistently.
- Add post-export Cypher validation scripts that check structural invariants (every `REALISES` targets a `:Capability`; every `InformationExchange` has both source and target performers; every node has `domain` and `layer` properties from the registry).
- **Pick the first AI use case** from Gartner's three archetypes — semantic search, knowledge discovery, or recommendation. The natural first target is **LLM-fronted semantic search** over the UAF model, which is already partially in place via the MCP server.
- *Outcome:* The discipline that will translate directly into OWL axioms at Stage 3, plus a named business-value driver for stakeholder buy-in.

### Stage 2 — MVO + MVG + OBDA overlay (next 1–2 quarters)
- **Author the MVO** — a Minimum Viable Ontology in OWL covering only the UAF domains in active use (Strategic + Operational at first). Generate the T-Box directly from `UAFStereotypeRegistry` so the registry remains the single source of truth, and align with the published OMG UAF DMM so the work is **reuse, not invention** (per Gartner's "reuse existing standards" recommendation).
- **Project the existing graph as the MVG** — stand up an Ontop (or Ontop-Neo4j) layer mapping Neo4j labels and relationship types to OWL classes and properties.
- Expose a SPARQL endpoint alongside the existing Cypher/Bolt endpoint.
- Expand the MVO **per use case** — when Personnel competence analysis is wanted, extend to the Personnel domain; when security accreditation arrives, extend to Security; not before.
- *Outcome:* SPARQL interoperability with enterprise tooling. T-Box reasoning (subsumption, classification hierarchies). The Java plugin and MCP server are unchanged.

### Stage 3 — Native triplestore for governed sub-domains (when accreditation or federation pressure arrives)
- Identify the subset of the UAF model that requires formal A-Box reasoning — typically Strategic (Capabilities, Capability Configurations, Strategic Parameters) and Security (classification hierarchies, clearance requirements).
- Materialise these as native RDF in GraphDB, Stardog, or Jena.
- Federate the triplestore with the remaining LPG via OBDA.
- *Outcome:* Full OWL reasoning where it pays off most, with the LPG still operational for the rest of the model. This is also when the **composite AI** pattern (graph + ML + reasoning + LLM) becomes operationally meaningful.

### Stage 4 — Full migration (programme-dependent, possibly never)
- When the programme reaches production/utilisation (ISO 15288 Stage 5–6) and formal accreditation or cross-tool federation is mandatory, complete the migration.
- The Java plugin gains a new export target (Apache Jena/RDF4J writer alongside or replacing `Neo4jCypherBuilder`).
- The MCP server gains a SPARQL query tool alongside its Cypher tool — or migrates fully to SPARQL — preserving the conversational AI delivery channel that Gartner identifies as a key accelerator of knowledge graph value.
- *Outcome:* Standards-aligned, federation-ready, reasoning-complete UAF knowledge graph.

This path **preserves every existing investment**, delivers SPARQL interoperability cheaply at Stage 2, and positions the programme for full formal ontology at a point where governance infrastructure and team skills are ready. It is **reversible at every stage** — the LPG remains the system of record until Stage 4, so no architectural decisions are irreversible before the programme is ready for them. It is also **Gartner-endorsed in shape**: agile, use-case-led, ontology-reuse-first, persona-aware.

---

## 13. Conclusion

The choice between RDF Native, RDF Virtual, and LPG is a choice about **how much semantic rigour the UAF programme is ready to demand**, and from whom. RDF Native delivers the highest fidelity to the UAF DMM and the highest analytic ceiling — formal reasoning, cross-organisation federation, accreditation-grade consistency checking. It comes with real governance, tooling, and skill costs that a programme in concept or early development is unlikely to absorb productively.

The current LPG implementation is not a wrong answer — it is the **right answer for the current programme phase**, delivering pragmatic graph analysis, LLM-augmented querying, and a working operational pipeline at low governance cost. RDF Virtual (OBDA) is the **correct bridge** from here toward RDF Native: it preserves the LPG investment, delivers SPARQL interoperability immediately, and lets the programme adopt formal reasoning incrementally for the sub-domains where it pays off most.

The recommendation is therefore: **honour the RDF Native preference as the long-term target, adopt OBDA as the next concrete step under a Gartner-style MVO/MVG cadence, and let the LPG continue to serve the programme until the governance maturity and stakeholder demand justify a fuller move.** Anchored in Jaffri's May 2025 Gartner note — that knowledge graph initiatives succeed when they lead with named AI use cases, when ontology development is agile and reuse-led, when composite AI (graph + ML + reasoning + LLM) is the design target, and when delivery is shaped per persona — this trajectory is defensible to enterprise architecture review boards and to programme decision makers, while remaining technically honest about where each approach pays off.

---

## References and Source Material

- **Afraz Jaffri, *How to Build Knowledge Graphs That Enable AI-Driven Enterprise Applications*, Gartner research note G00768041, 22 May 2025.** Reprint: `https://www.gartner.com/doc/reprints?id=1-2LILTTN9&ct=250723&st=sb`. (Direct quotations in §3 are from this note.)
- **Gartner, *AI Design Patterns for Knowledge Graphs and Generative AI*** — referenced in Jaffri (2025) for GenAI-assisted ontology and graph construction.
- **Gartner, *3 Ways to Enhance AI With Graph Analytics and Machine Learning*** — referenced in Jaffri (2025) for graph analytics technique inventory.
- **Gartner, *How Graph Techniques Deliver Business Value*** — referenced in Jaffri (2025) for case study material.
- **Gartner, *2023 AI in the Enterprise Survey*** — source of the 79% / 66% data integration maturity statistic.
- **OMG UAF 1.2 specification** — including the UAF Domain Metamodel (DMM) in SMOF/MOF (OMG `formal/2022-12-01` or current).
- **W3C OWL 2 Web Ontology Language** — Structural Specification and Functional-Style Syntax (W3C Recommendation, 11 December 2012).
- **W3C SPARQL 1.1 Query Language** — W3C Recommendation, 21 March 2013.
- **W3C R2RML** — RDB to RDF Mapping Language (W3C Recommendation, 27 September 2012); RML for graph and semi-structured sources.
- **W3C SHACL** — Shapes Constraint Language (W3C Recommendation, 20 July 2017) — relevant for A-Box validation in either RDF approach.
- **ISO/IEC/IEEE 15288:2023** — Systems and software engineering — System life cycle processes.
- **INCOSE Systems Engineering Handbook, 5th Edition (2023)** — particularly the chapters on Architecture Definition, Digital Engineering, and Model-Based Systems Engineering.
- **OMG Ontology Definition Metamodel (ODM)** — the normative MOF-to-OWL mapping path.

---

*Prepared for the MSOSA-Toolbox UAF Neo4j Plugin programme, May 2026.*
