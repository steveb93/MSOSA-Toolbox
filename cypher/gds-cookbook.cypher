// =============================================================================
// UAF Neo4j Graph Data Science (GDS) Cookbook
// Ready-to-run GDS calls for analysing UAF knowledge graphs.
// Neo4j Browser: paste any block and run.
//
// Prerequisites:
//   - Neo4j Docker stack running (docker compose up -d in docker-compose/)
//   - GDS plugin loaded (already in NEO4J_PLUGINS in docker-compose.yml)
//   - A UAF export merged into the graph (init_uaf_graph.cypher + plugin run)
//
// Health check before anything else:
//   RETURN gds.version();
//
// All projections below are named — drop them with:
//   CALL gds.graph.drop('<name>') YIELD graphName RETURN graphName;
//
// See query-cookbook.cypher for the model conventions (stereotype labels,
// `n.stereotype IS NOT NULL` as the universal filter, etc.).
// =============================================================================

// ─────────────────────────────────────────────────────────────────────────────
// 1. PROJECTIONS — turn the live LPG into an in-memory GDS graph
// ─────────────────────────────────────────────────────────────────────────────

// 1a. Full UAF graph: every stereotype-bearing node + every relationship
//     between such nodes. Good baseline for centrality across the whole model.
MATCH (src) WHERE src.stereotype IS NOT NULL
OPTIONAL MATCH (src)-[r]->(tgt) WHERE tgt.stereotype IS NOT NULL
WITH gds.graph.project('uaf-full', src, tgt) AS g
RETURN g.graphName, g.nodeCount, g.relationshipCount;

// 1b. Capability dependency subgraph — Capability nodes plus the relationships
//     that connect them directly (specialisation, realisation, exhibits).
MATCH (src:Capability)
OPTIONAL MATCH (src)-[r:SPECIALISES|GENERALIZATION|REALISES|EXHIBITS|TRACES_TO]->(tgt:Capability)
WITH gds.graph.project('uaf-capability', src, tgt) AS g
RETURN g.graphName, g.nodeCount, g.relationshipCount;

// 1c. Operational flow subgraph — Performer + Activity + Information,
//     wired by the typical operational rels.
MATCH (src) WHERE src.domain = 'OPERATIONAL' AND src.stereotype IS NOT NULL
OPTIONAL MATCH (src)-[r]->(tgt) WHERE tgt.domain = 'OPERATIONAL' AND tgt.stereotype IS NOT NULL
WITH gds.graph.project('uaf-operational', src, tgt) AS g
RETURN g.graphName, g.nodeCount, g.relationshipCount;

// 1d. Resource exchange subgraph — ResourcePerformer / ResourceArtifact and
//     the rels that carry physical exchanges.
MATCH (src) WHERE src.domain = 'RESOURCE' AND src.stereotype IS NOT NULL
OPTIONAL MATCH (src)-[r]->(tgt) WHERE tgt.domain = 'RESOURCE' AND tgt.stereotype IS NOT NULL
WITH gds.graph.project('uaf-resource', src, tgt) AS g
RETURN g.graphName, g.nodeCount, g.relationshipCount;

// 1e. Strategic → Resource trace subgraph — cross-domain implementation paths.
MATCH (src) WHERE src.domain IN ['STRATEGIC','OPERATIONAL','RESOURCE'] AND src.stereotype IS NOT NULL
OPTIONAL MATCH (src)-[r:REALISES|TRACES_TO|ALLOCATED_TO|SATISFIES|IMPLEMENTS|EXHIBITS]->(tgt)
WHERE tgt.domain IN ['STRATEGIC','OPERATIONAL','RESOURCE'] AND tgt.stereotype IS NOT NULL
WITH gds.graph.project('uaf-trace', src, tgt) AS g
RETURN g.graphName, g.nodeCount, g.relationshipCount;

// ─────────────────────────────────────────────────────────────────────────────
// 2. PAGERANK — most-depended-on elements
// ─────────────────────────────────────────────────────────────────────────────

// 2a. PageRank across the whole UAF graph (stream — read-only).
//     Top scores surface the elements that the most trace/realisation/exhibits
//     paths converge on — usually load-bearing Capabilities, Activities, or
//     System blocks.
CALL gds.pageRank.stream('uaf-full')
YIELD nodeId, score
WITH gds.util.asNode(nodeId) AS n, score
RETURN n.stereotype AS stereotype, n.name AS name, n.domain AS domain,
       round(score * 1000) / 1000.0 AS pagerank
ORDER BY score DESC
LIMIT 25;

// 2b. PageRank inside the Capability subgraph — which capabilities are
//     specialised, realised, or exhibited by the most others?
CALL gds.pageRank.stream('uaf-capability')
YIELD nodeId, score
WITH gds.util.asNode(nodeId) AS n, score
RETURN n.name AS capability, round(score * 1000) / 1000.0 AS pagerank
ORDER BY score DESC
LIMIT 25;

// 2c. PageRank on the Strategic→Resource trace subgraph — which Resource
//     elements absorb the most strategic intent?
CALL gds.pageRank.stream('uaf-trace')
YIELD nodeId, score
WITH gds.util.asNode(nodeId) AS n, score
WHERE n.domain = 'RESOURCE'
RETURN n.stereotype AS stereotype, n.name AS resource,
       round(score * 1000) / 1000.0 AS pagerank
ORDER BY score DESC
LIMIT 25;

// ─────────────────────────────────────────────────────────────────────────────
// 3. BETWEENNESS — brokers and bottlenecks
// ─────────────────────────────────────────────────────────────────────────────

// 3a. Betweenness on the Operational subgraph — which performers/activities
//     sit on the most operational paths? Candidates for resilience focus.
CALL gds.betweenness.stream('uaf-operational')
YIELD nodeId, score
WITH gds.util.asNode(nodeId) AS n, score
WHERE score > 0
RETURN n.stereotype AS stereotype, n.name AS name,
       round(score * 100) / 100.0 AS betweenness
ORDER BY score DESC
LIMIT 25;

// 3b. Betweenness on the Resource subgraph — which systems/performers
//     mediate the most resource exchanges?
CALL gds.betweenness.stream('uaf-resource')
YIELD nodeId, score
WITH gds.util.asNode(nodeId) AS n, score
WHERE score > 0
RETURN n.stereotype AS stereotype, n.name AS name,
       round(score * 100) / 100.0 AS betweenness
ORDER BY score DESC
LIMIT 25;

// ─────────────────────────────────────────────────────────────────────────────
// 4. CONNECTED COMPONENTS — orphan-island detection
// ─────────────────────────────────────────────────────────────────────────────

// 4a. Weakly connected components on the full graph. The dominant component
//     is the "main" model; small components are orphan islands worth
//     investigating (incomplete imports, dangling stereotypes, …).
CALL gds.wcc.stream('uaf-full')
YIELD nodeId, componentId
WITH componentId, count(*) AS size, collect(gds.util.asNode(nodeId).name)[..5] AS sample
RETURN componentId, size, sample
ORDER BY size DESC
LIMIT 25;

// 4b. Same idea inside Operational only — process-level orphan flows.
CALL gds.wcc.stream('uaf-operational')
YIELD nodeId, componentId
WITH componentId, count(*) AS size, collect(gds.util.asNode(nodeId).name)[..5] AS sample
WHERE size < 5
RETURN componentId, size, sample
ORDER BY size;

// ─────────────────────────────────────────────────────────────────────────────
// 5. COMMUNITY DETECTION — cross-domain cluster discovery
// ─────────────────────────────────────────────────────────────────────────────

// 5a. Louvain on the Strategic→Resource trace subgraph. Communities tend to
//     line up with capability portfolios (Strategic Capability + the
//     Operational Activities and Resource elements that implement it).
CALL gds.louvain.stream('uaf-trace')
YIELD nodeId, communityId
WITH gds.util.asNode(nodeId) AS n, communityId
RETURN communityId,
       count(*) AS size,
       collect(DISTINCT n.domain) AS domains,
       collect(n.name)[..8] AS sample
ORDER BY size DESC
LIMIT 25;

// ─────────────────────────────────────────────────────────────────────────────
// 6. WRITE-BACK — persist scores onto graph nodes
// ─────────────────────────────────────────────────────────────────────────────
//
// Useful when downstream consumers (the SPARQL view, decision dashboards,
// the LLM via the MCP server) need to filter or sort by GDS-derived scores.
// Properties land on the source LPG nodes; the next ontology dump (or the
// plugin's RDF emitter) will carry them across to Fuseki as data properties.
//
// Re-run the corresponding write call after each UAF re-export.
// ─────────────────────────────────────────────────────────────────────────────

// 6a. Write PageRank back as `gdsPagerank` on every stereotype-bearing node.
CALL gds.pageRank.write('uaf-full', {writeProperty: 'gdsPagerank'})
YIELD nodePropertiesWritten, ranIterations
RETURN nodePropertiesWritten, ranIterations;

// 6b. Sanity check — top 10 by the persisted score.
MATCH (n) WHERE n.gdsPagerank IS NOT NULL
RETURN n.stereotype AS stereotype, n.name AS name, n.domain AS domain,
       round(n.gdsPagerank * 1000) / 1000.0 AS pagerank
ORDER BY n.gdsPagerank DESC
LIMIT 10;

// 6c. Clear the write-back property (run before re-export if you want a
//     clean slate; otherwise stale scores linger on nodes whose neighbourhood
//     changed).
MATCH (n) WHERE n.gdsPagerank IS NOT NULL
REMOVE n.gdsPagerank;

// ─────────────────────────────────────────────────────────────────────────────
// 7. HOUSEKEEPING — drop projections when finished
// ─────────────────────────────────────────────────────────────────────────────

CALL gds.graph.list() YIELD graphName, nodeCount, relationshipCount
RETURN graphName, nodeCount, relationshipCount;

CALL gds.graph.drop('uaf-full', false) YIELD graphName RETURN graphName;
CALL gds.graph.drop('uaf-capability', false) YIELD graphName RETURN graphName;
CALL gds.graph.drop('uaf-operational', false) YIELD graphName RETURN graphName;
CALL gds.graph.drop('uaf-resource', false) YIELD graphName RETURN graphName;
CALL gds.graph.drop('uaf-trace', false) YIELD graphName RETURN graphName;
