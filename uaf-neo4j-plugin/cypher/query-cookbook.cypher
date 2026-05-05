// =============================================================================
// UAF Neo4j Query Cookbook
// Ready-to-run queries for exploring UAF knowledge graphs.
// Neo4j Browser: paste any block and run.
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// 1. BROWSE — All elements in a domain
// ─────────────────────────────────────────────────────────────────────────────

MATCH (n:UAFElement {domain: 'OPERATIONAL'})
RETURN n.name, n.stereotype, n.layer, n.qualifiedName
ORDER BY n.stereotype, n.name
LIMIT 100;

// Count by stereotype within a domain
MATCH (n:UAFElement {domain: 'OPERATIONAL'})
RETURN n.stereotype AS stereotype, count(*) AS total
ORDER BY total DESC;

// ─────────────────────────────────────────────────────────────────────────────
// 2. TRACEABILITY — Capability → Operational Activity chain
// ─────────────────────────────────────────────────────────────────────────────

MATCH path = (cap:Capability)-[:REALISES|CONTRIBUTES_TO|SATISFIES*1..4]->(act:OperationalActivity)
RETURN path
LIMIT 50;

// Direct realisation links from capabilities
MATCH (cap:Capability)-[r:REALISES]->(target:UAFElement)
RETURN cap.name AS capability,
       type(r)  AS relationship,
       target.name      AS target,
       target.stereotype AS targetType
ORDER BY cap.name;

// ─────────────────────────────────────────────────────────────────────────────
// 3. CROSS-DOMAIN — Trace from Strategic to Physical implementation
// ─────────────────────────────────────────────────────────────────────────────

MATCH path = (st:UAFElement {domain:'STRATEGIC'})
             -[:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|IMPLEMENTS*1..6]->
             (ph:UAFElement {layer:'PHYSICAL'})
RETURN st.name  AS strategic,
       ph.name  AS physical,
       ph.stereotype AS physType,
       length(path) AS hops
ORDER BY hops
LIMIT 50;

// ─────────────────────────────────────────────────────────────────────────────
// 4. ARCHITECTURE LAYERS — Everything at the Logical layer
// ─────────────────────────────────────────────────────────────────────────────

MATCH (n:UAFElement {layer: 'LOGICAL'})
RETURN n.name, n.stereotype, n.domain, n.diagramName
ORDER BY n.domain, n.stereotype
LIMIT 100;

// Heatmap: element counts by domain × layer
MATCH (n:UAFElement)
RETURN n.domain AS domain, n.layer AS layer, count(*) AS count
ORDER BY domain, layer;

// ─────────────────────────────────────────────────────────────────────────────
// 5. DIAGRAM MEMBERSHIP — All elements in a named diagram
// ─────────────────────────────────────────────────────────────────────────────

MATCH (n:UAFElement)
WHERE n.diagramName CONTAINS 'Operational Context'
RETURN n.name, n.stereotype, n.qualifiedName
ORDER BY n.stereotype;

// Which diagrams contain a specific element?
MATCH (n:UAFElement {name: 'My Capability'})
RETURN n.diagramName;

// ─────────────────────────────────────────────────────────────────────────────
// 6. RELATIONSHIPS — All relationships between two domains
// ─────────────────────────────────────────────────────────────────────────────

MATCH (src:UAFElement {domain:'OPERATIONAL'})-[r]->(tgt:UAFElement {domain:'RESOURCE'})
RETURN src.name AS source, type(r) AS rel, tgt.name AS target,
       src.stereotype AS srcType, tgt.stereotype AS tgtType
ORDER BY type(r)
LIMIT 100;

// Relationship type frequency (UAF instance relationships only)
MATCH ()-[r]->()
WHERE type(r) NOT IN ['INSTANCE_OF','BELONGS_TO','IN_LAYER','DEFINES']
RETURN type(r) AS relType, count(*) AS frequency
ORDER BY frequency DESC;

// ─────────────────────────────────────────────────────────────────────────────
// 7. METAMODEL — All instances of a stereotype (via INSTANCE_OF)
// ─────────────────────────────────────────────────────────────────────────────

MATCH (s:Stereotype {name: 'Capability'})<-[:INSTANCE_OF]-(n:UAFElement)
RETURN n.name, n.qualifiedName, n.diagramName
ORDER BY n.name;

// How many instances does each stereotype have?
MATCH (s:Stereotype)<-[:INSTANCE_OF]-(n:UAFElement)
RETURN s.name AS stereotype, count(n) AS instances
ORDER BY instances DESC;

// ─────────────────────────────────────────────────────────────────────────────
// 8. QUALITY — Orphan nodes and coverage checks
// ─────────────────────────────────────────────────────────────────────────────

// Elements with no outgoing or incoming UAF relationships
MATCH (n:UAFElement)
WHERE NOT (n)-[:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|PERFORMS|
               DEPENDS_ON|IMPLEMENTS|ASSIGNED_TO|CONNECTED_TO|FLOWS_TO]-()
RETURN n.name, n.stereotype, n.domain
ORDER BY n.domain, n.stereotype
LIMIT 50;

// Elements with no documentation
MATCH (n:UAFElement)
WHERE n.documentation IS NULL OR n.documentation = ''
RETURN n.name, n.stereotype, n.domain, n.layer
ORDER BY n.domain
LIMIT 50;

// ─────────────────────────────────────────────────────────────────────────────
// 9. FULL-TEXT SEARCH — Find elements by keyword
// ─────────────────────────────────────────────────────────────────────────────

// Requires FULLTEXT INDEX uaf_element_text (created in init_uaf_graph.cypher)
CALL db.index.fulltext.queryNodes('uaf_element_text', 'sensor network')
YIELD node, score
RETURN node.name, node.stereotype, node.domain, score
ORDER BY score DESC
LIMIT 20;

// ─────────────────────────────────────────────────────────────────────────────
// 10. PATHFINDING — Shortest path between two named elements
// ─────────────────────────────────────────────────────────────────────────────

MATCH (start:UAFElement {name: 'Air Defence Capability'}),
      (end:UAFElement   {name: 'Radar System'})
MATCH path = shortestPath((start)-[*..10]->(end))
RETURN path;

// All paths up to 5 hops (use carefully on large graphs)
MATCH (start:UAFElement {name: 'Air Defence Capability'}),
      (end:UAFElement   {name: 'Radar System'})
MATCH path = (start)-[*1..5]->(end)
RETURN path
LIMIT 10;

// ─────────────────────────────────────────────────────────────────────────────
// 11. MULTI-MODEL — SystemModel provenance queries
// ─────────────────────────────────────────────────────────────────────────────

// List all system models and how many elements each defines
MATCH (m:SystemModel)-[:DEFINES]->(n:UAFElement)
RETURN m.name AS model, count(n) AS elements ORDER BY elements DESC;

// All elements defined by a specific model
MATCH (m:SystemModel {name: 'MyProject'})-[:DEFINES]->(n:UAFElement)
RETURN n.name, n.stereotype, n.domain, n.layer
ORDER BY n.domain, n.stereotype;

// Elements shared across two or more models (reused via project usage)
MATCH (m:SystemModel)-[:DEFINES]->(n:UAFElement)
WITH n, collect(m.name) AS models, count(m) AS modelCount
WHERE modelCount > 1
RETURN n.name, n.stereotype, models, modelCount
ORDER BY modelCount DESC;

// All elements unique to a single model (not shared)
MATCH (m:SystemModel)-[:DEFINES]->(n:UAFElement)
WITH n, collect(m.name) AS models, count(m) AS modelCount
WHERE modelCount = 1
RETURN n.name, n.stereotype, models[0] AS ownedBy
ORDER BY ownedBy, n.stereotype;

// Cross-model traceability: capabilities defined in one model realised by resources in another
MATCH (m1:SystemModel)-[:DEFINES]->(cap:Capability),
      (m2:SystemModel)-[:DEFINES]->(res:UAFElement),
      (cap)-[:REALISES|SATISFIES|ALLOCATED_TO*1..4]->(res)
WHERE m1.name <> m2.name
RETURN m1.name AS capModel, cap.name AS capability,
       m2.name AS resModel, res.name AS resource, res.stereotype
ORDER BY m1.name, cap.name;

// ─────────────────────────────────────────────────────────────────────────────
// EXTRAS — Useful utility queries
// ─────────────────────────────────────────────────────────────────────────────

// Full graph overview (node + rel counts)
MATCH (n:UAFElement)
WITH count(n) AS nodes
MATCH ()-[r]->()
WHERE type(r) NOT IN ['INSTANCE_OF','BELONGS_TO','IN_LAYER']
RETURN nodes, count(r) AS rels;

// View the metamodel (stereotype → domain → layer)
MATCH (s:Stereotype)-[:BELONGS_TO]->(d:Domain),
      (s)-[:IN_LAYER]->(l:ArchitectureLayer)
RETURN s.name AS stereotype, d.name AS domain, l.name AS layer
ORDER BY d.name, s.name;
