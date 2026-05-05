// =============================================================================
// UAF Neo4j Query Cookbook
// Ready-to-run queries for exploring UAF knowledge graphs.
// Neo4j Browser: paste any block and run.
//
// Model conventions:
//   - Each element node carries only its stereotype label (e.g. :Capability).
//   - Every element has an 'id' property holding the MSOSA element ID
//     (globally unique — use this, not name, to identify elements).
//   - Names are NOT unique across the graph; elements in different domains
//     may share the same name.
//   - Information/data attributes on ResourceInformation nodes are stored
//     as tv_<tagName> properties (e.g. tv_dataType, tv_multiplicity).
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// 1. BROWSE — All elements in a domain
// ─────────────────────────────────────────────────────────────────────────────

// All Operational elements
MATCH (n {domain: 'OPERATIONAL'})
WHERE n.stereotype IS NOT NULL
RETURN n.name, n.stereotype, n.qualifiedName, n.diagramName
ORDER BY n.stereotype, n.name
LIMIT 100;

// Count by stereotype within a domain
MATCH (n {domain: 'OPERATIONAL'})
WHERE n.stereotype IS NOT NULL
RETURN n.stereotype AS stereotype, count(*) AS total
ORDER BY total DESC;

// All elements across every domain
MATCH (n)
WHERE n.domain IS NOT NULL AND n.stereotype IS NOT NULL
RETURN n.domain AS domain, n.stereotype AS stereotype, count(*) AS total
ORDER BY domain, total DESC;

// ─────────────────────────────────────────────────────────────────────────────
// 2. TRACEABILITY — Capability → Operational Activity chain
// ─────────────────────────────────────────────────────────────────────────────

MATCH path = (cap:Capability)-[:REALISES|CONTRIBUTES_TO|SATISFIES*1..4]->(act:OperationalActivity)
RETURN path
LIMIT 50;

// Direct realisation links from capabilities
MATCH (cap:Capability)-[r:REALISES]->(target)
WHERE target.stereotype IS NOT NULL
RETURN cap.name AS capability,
       type(r)           AS relationship,
       target.name       AS target,
       target.stereotype AS targetType
ORDER BY cap.name;

// Full capability decomposition tree
MATCH path = (cap:Capability)-[:COMPOSED_OF|SPECIALISES*1..5]->(sub:Capability)
RETURN cap.name AS root, [n IN nodes(path) | n.name] AS chain
LIMIT 50;

// ─────────────────────────────────────────────────────────────────────────────
// 3. CROSS-DOMAIN — Strategic → Resource implementation traces
// ─────────────────────────────────────────────────────────────────────────────

// Strategic capabilities traced to Resource performers
MATCH path = (st {domain: 'STRATEGIC'})
             -[:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|IMPLEMENTS*1..6]->
             (rs {domain: 'RESOURCE'})
WHERE st.stereotype IS NOT NULL AND rs.stereotype IS NOT NULL
RETURN st.name        AS strategic,
       st.stereotype  AS strategicType,
       rs.name        AS resource,
       rs.stereotype  AS resourceType,
       length(path)   AS hops
ORDER BY hops
LIMIT 50;

// Capabilities traced to physical hardware/software specifically
MATCH path = (cap:Capability)
             -[:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|IMPLEMENTS*1..6]->
             (ph:HardwareElement|SoftwareElement|ActualSystem|PhysicalArchitecture|NaturalResource)
RETURN cap.name AS capability,
       ph.name  AS implementation,
       ph.stereotype AS implType,
       length(path) AS hops
ORDER BY hops
LIMIT 50;

// Operational performers mapped to resource performers
MATCH (op:OperationalPerformer)-[r:REALISES|ALLOCATED_TO|ASSIGNED_TO]->(rp:ResourcePerformer)
RETURN op.name AS operational, type(r) AS rel, rp.name AS resource
ORDER BY op.name;

// ─────────────────────────────────────────────────────────────────────────────
// 4. INFORMATION ELEMENTS — Operational and Resource information
// ─────────────────────────────────────────────────────────────────────────────

// All OperationalInformation elements
MATCH (oi:OperationalInformation)
RETURN oi.id, oi.name, oi.qualifiedName, oi.diagramName
ORDER BY oi.name;

// OperationalInformation exchanged between performers
MATCH (src:OperationalPerformer)-[:FLOWS_TO|CONNECTED_TO]->(oi:OperationalInformation)
RETURN src.name AS from, oi.name AS information
ORDER BY src.name;

// All ResourceInformation nodes with their data attributes (tv_ properties)
MATCH (ri:ResourceInformation)
RETURN ri.id, ri.name, ri.qualifiedName,
       [k IN keys(ri) WHERE k STARTS WITH 'tv_' | {attr: k, value: ri[k]}] AS dataAttributes
ORDER BY ri.name;

// ResourceInformation linked to resource performers (data → system)
MATCH (ri:ResourceInformation)-[r]-(rp:ResourcePerformer|SystemBlock|LogicalArchitecture)
RETURN ri.name AS information, type(r) AS rel, rp.name AS resource, rp.stereotype AS resourceType
ORDER BY ri.name;

// Find a specific data attribute across all ResourceInformation nodes
// Replace 'tv_dataType' with the attribute name you're looking for
MATCH (ri:ResourceInformation)
WHERE ri.tv_dataType IS NOT NULL
RETURN ri.name, ri.tv_dataType
ORDER BY ri.name;

// ─────────────────────────────────────────────────────────────────────────────
// 5. DIAGRAM MEMBERSHIP — Elements by diagram
// ─────────────────────────────────────────────────────────────────────────────

// All elements in a named diagram
MATCH (n)
WHERE n.diagramName CONTAINS 'Operational Context'
  AND n.stereotype IS NOT NULL
RETURN n.name, n.stereotype, n.domain, n.qualifiedName
ORDER BY n.stereotype;

// Which diagrams reference a specific element (find by MSOSA element ID)?
MATCH (n {id: '_REPLACE_WITH_MSOSA_ID_'})
RETURN n.name, n.stereotype, n.diagramName;

// Count of elements per diagram
MATCH (n)
WHERE n.diagramName IS NOT NULL AND n.diagramName <> '' AND n.stereotype IS NOT NULL
RETURN n.diagramName AS diagram, n.domain AS domain, count(*) AS elements
ORDER BY elements DESC
LIMIT 30;

// ─────────────────────────────────────────────────────────────────────────────
// 6. RELATIONSHIPS — Cross-domain and type analysis
// ─────────────────────────────────────────────────────────────────────────────

// All relationships between Operational and Resource domains
MATCH (src {domain: 'OPERATIONAL'})-[r]->(tgt {domain: 'RESOURCE'})
WHERE src.stereotype IS NOT NULL AND tgt.stereotype IS NOT NULL
RETURN src.name AS source, src.stereotype AS srcType,
       type(r)  AS rel,
       tgt.name AS target, tgt.stereotype AS tgtType
ORDER BY type(r)
LIMIT 100;

// Relationship type frequency (UAF instance relationships only)
MATCH ()-[r]->()
WHERE type(r) NOT IN ['INSTANCE_OF', 'BELONGS_TO', 'DEFINES']
RETURN type(r) AS relType, count(*) AS frequency
ORDER BY frequency DESC;

// Outgoing relationships from a specific element (by MSOSA id)
MATCH (n {id: '_REPLACE_WITH_MSOSA_ID_'})-[r]->(target)
RETURN type(r) AS rel, target.name AS target, target.stereotype AS targetType
ORDER BY type(r);

// ─────────────────────────────────────────────────────────────────────────────
// 7. METAMODEL — Stereotype and domain queries
// ─────────────────────────────────────────────────────────────────────────────

// All instances of a stereotype (via INSTANCE_OF)
MATCH (s:Stereotype {name: 'Capability'})<-[:INSTANCE_OF]-(n)
RETURN n.name, n.qualifiedName, n.diagramName
ORDER BY n.name;

// How many instances does each stereotype have?
MATCH (s:Stereotype)<-[:INSTANCE_OF]-(n)
RETURN s.name AS stereotype, s.domain AS domain, count(n) AS instances
ORDER BY instances DESC;

// View the metamodel (stereotype → domain)
MATCH (s:Stereotype)-[:BELONGS_TO]->(d:Domain)
RETURN d.name AS domain, collect(s.name) AS stereotypes
ORDER BY d.name;

// ─────────────────────────────────────────────────────────────────────────────
// 8. QUALITY — Orphan nodes and coverage checks
// ─────────────────────────────────────────────────────────────────────────────

// Elements with no UAF relationships (isolated nodes)
MATCH (n)
WHERE n.stereotype IS NOT NULL
  AND NOT (n)-[:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|PERFORMS|
                DEPENDS_ON|IMPLEMENTS|ASSIGNED_TO|CONNECTED_TO|FLOWS_TO]-()
RETURN n.name, n.stereotype, n.domain
ORDER BY n.domain, n.stereotype
LIMIT 50;

// Elements with no documentation
MATCH (n)
WHERE n.stereotype IS NOT NULL
  AND (n.documentation IS NULL OR n.documentation = '')
RETURN n.name, n.stereotype, n.domain
ORDER BY n.domain
LIMIT 50;

// ResourceInformation elements with no data attributes (tv_ properties)
MATCH (ri:ResourceInformation)
WHERE NOT any(k IN keys(ri) WHERE k STARTS WITH 'tv_')
RETURN ri.id, ri.name, ri.qualifiedName
ORDER BY ri.name;

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
// 10. PATHFINDING — Shortest path between two elements
// ─────────────────────────────────────────────────────────────────────────────

// Use MSOSA element IDs — names are not unique
MATCH (start {id: '_REPLACE_WITH_START_ID_'}),
      (end   {id: '_REPLACE_WITH_END_ID_'})
MATCH path = shortestPath((start)-[*..10]->(end))
RETURN path;

// All paths up to 5 hops (use carefully on large graphs)
MATCH (start {id: '_REPLACE_WITH_START_ID_'}),
      (end   {id: '_REPLACE_WITH_END_ID_'})
MATCH path = (start)-[*1..5]->(end)
RETURN path
LIMIT 10;

// ─────────────────────────────────────────────────────────────────────────────
// 11. MULTI-MODEL — SystemModel provenance queries
// ─────────────────────────────────────────────────────────────────────────────

// List all system models and how many elements each defines
MATCH (m:SystemModel)-[:DEFINES]->(n)
WHERE n.stereotype IS NOT NULL
RETURN m.name AS model, count(n) AS elements ORDER BY elements DESC;

// All elements defined by a specific model
MATCH (m:SystemModel {name: 'MyProject'})-[:DEFINES]->(n)
WHERE n.stereotype IS NOT NULL
RETURN n.name, n.stereotype, n.domain
ORDER BY n.domain, n.stereotype;

// Elements shared across two or more models
MATCH (m:SystemModel)-[:DEFINES]->(n)
WHERE n.stereotype IS NOT NULL
WITH n, collect(m.name) AS models, count(m) AS modelCount
WHERE modelCount > 1
RETURN n.name, n.stereotype, models, modelCount
ORDER BY modelCount DESC;

// Cross-model traceability: capabilities in one model realised by resources in another
MATCH (m1:SystemModel)-[:DEFINES]->(cap:Capability),
      (m2:SystemModel)-[:DEFINES]->(res),
      (cap)-[:REALISES|SATISFIES|ALLOCATED_TO*1..4]->(res)
WHERE m1.name <> m2.name
  AND res.domain = 'RESOURCE'
RETURN m1.name AS capModel, cap.name AS capability,
       m2.name AS resModel, res.name AS resource, res.stereotype
ORDER BY m1.name, cap.name;

// ─────────────────────────────────────────────────────────────────────────────
// 12. DIAGNOSTICS — Verify what is in the graph and debug missing elements
// ─────────────────────────────────────────────────────────────────────────────

// Summary: element counts by domain and stereotype
MATCH (n)
WHERE n.domain IS NOT NULL AND n.stereotype IS NOT NULL
RETURN n.domain AS domain, n.stereotype AS stereotype, count(*) AS count
ORDER BY domain, count DESC;

// Which stereotype labels are present as node labels in this graph?
CALL db.labels() YIELD label
WHERE label NOT IN ['SystemModel', 'Stereotype', 'Domain']
RETURN label
ORDER BY label;

// Check a specific stereotype is exported (replace name as needed)
MATCH (n:ResourcePerformer)
RETURN count(n) AS resourcePerformerCount;

// Find elements whose stereotype name is NOT in the metamodel
// (indicates a registry gap — stereotype exists in MSOSA but not in init_uaf_graph.cypher)
MATCH (n)
WHERE n.stereotype IS NOT NULL
  AND NOT EXISTS { MATCH (:Stereotype {name: n.stereotype}) }
RETURN DISTINCT n.stereotype AS unmappedStereotype, count(n) AS occurrences
ORDER BY occurrences DESC;

// Check which domains have INSTANCE_OF links vs. which are missing them
MATCH (n)
WHERE n.stereotype IS NOT NULL
OPTIONAL MATCH (n)-[:INSTANCE_OF]->(s:Stereotype)
RETURN n.domain AS domain, n.stereotype AS stereotype,
       count(n) AS elements,
       count(s) AS withInstanceOf,
       count(n) - count(s) AS missingInstanceOf
ORDER BY missingInstanceOf DESC;

// ─────────────────────────────────────────────────────────────────────────────
// EXTRAS — Utility queries
// ─────────────────────────────────────────────────────────────────────────────

// Full graph overview
MATCH (n)
WHERE n.stereotype IS NOT NULL
WITH count(n) AS nodes
MATCH ()-[r]->()
WHERE type(r) NOT IN ['INSTANCE_OF', 'BELONGS_TO', 'DEFINES']
RETURN nodes, count(r) AS rels;

// All tagged values on a specific ResourceInformation element
MATCH (ri:ResourceInformation {name: '_REPLACE_WITH_NAME_'})
RETURN [k IN keys(ri) WHERE k STARTS WITH 'tv_' | {attribute: k, value: ri[k]}] AS dataModel;
