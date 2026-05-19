// =============================================================================
// neosemantics (n10s) initialisation for the UAF MVO overlay.
//
// Run AFTER:
//   1. Neo4j container is up with the n10s JAR in /plugins
//   2. uaf-neo4j-plugin/cypher/init_uaf_graph.cypher has seeded the metamodel
//   3. ontology/codegen/generate_mvo.py has produced n10s-mappings.cypher
//
// Run with:
//   cypher-shell -u neo4j -p Password123 -f ontology/n10s-init.cypher
//   cypher-shell -u neo4j -p Password123 -f ontology/n10s-mappings.cypher
//
// SPARQL endpoint: http://localhost:7474/rdf/neo4j/query
// =============================================================================

// --- Required uniqueness constraint (n10s won't import without this) --------

CREATE CONSTRAINT n10s_unique_uri IF NOT EXISTS
  FOR (r:Resource) REQUIRE r.uri IS UNIQUE;

// --- Graph config (idempotent: ignores errors if already initialised) --------
// handleVocabUris=MAP    -> Neo4j labels map to URIs via n10s.mapping.add(...)
// handleMultival=ARRAY   -> multi-valued props returned as arrays
// keepLangTag=true       -> preserve rdfs:label language tags

CALL n10s.graphconfig.init({
    handleVocabUris: 'MAP',
    handleMultival: 'ARRAY',
    keepLangTag: true,
    applyNeo4jNaming: false
}) YIELD param, value
RETURN param, value;

// --- Namespace prefixes ------------------------------------------------------

CALL n10s.nsprefixes.add('uaf',     'http://msosa-toolbox.local/uaf#');
CALL n10s.nsprefixes.add('uafprop', 'http://msosa-toolbox.local/uaf/prop#');
CALL n10s.nsprefixes.add('sysml',   'http://msosa-toolbox.local/sysml#');
CALL n10s.nsprefixes.add('bpmn',    'http://msosa-toolbox.local/bpmn#');
CALL n10s.nsprefixes.add('owl',     'http://www.w3.org/2002/07/owl#');
CALL n10s.nsprefixes.add('rdfs',    'http://www.w3.org/2000/01/rdf-schema#');
CALL n10s.nsprefixes.add('dcterms', 'http://purl.org/dc/terms/');

// --- T-Box import (uncomment after copying uaf-mvo.ttl to /import) -----------
// Copy ontology/uaf-mvo.ttl to F:/neo4j-docker/import/uaf-mvo.ttl, then run:
//
//   CALL n10s.onto.import.fetch("file:///import/uaf-mvo.ttl", "Turtle");
//
// This populates n10s ontology tables so SPARQL queries see the class
// hierarchy (uaf:Capability rdfs:subClassOf uaf:StrategicElement, etc.).

RETURN "n10s graph config + namespaces ready. Now run n10s-mappings.cypher." AS status;
