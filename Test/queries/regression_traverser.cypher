// Traverser regression queries (issue #75).
//
// Run AFTER an MSOSA export against the canonical test model. Each query is an
// assertion of the form "this stereotype must produce at least one node now that
// the traverser walks classifier-owned content and prefers UAF over SysML/BPMN
// on multi-stereotyped elements".
//
// Each query returns a count. A non-zero count means the bug is still fixed.
// The matched Python harness in Test/test_traverser_regression.py runs the
// same queries programmatically with pytest.
//
// Usage:
//   cypher-shell -u neo4j -p Password123 -f Test/queries/regression_traverser.cypher

// --- Operational domain ------------------------------------------------------
MATCH (n {stereotype: 'OperationalPerformer'}) RETURN 'OperationalPerformer' AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'OperationalActivity'})  RETURN 'OperationalActivity'  AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'OperationalProcess'})   RETURN 'OperationalProcess'   AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'OperationalFunction'})  RETURN 'OperationalFunction'  AS stereotype, count(n) AS instances;

// --- Resource domain ---------------------------------------------------------
MATCH (n {stereotype: 'ResourcePerformer'})    RETURN 'ResourcePerformer'    AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'ResourceFunction'})     RETURN 'ResourceFunction'     AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'ResourceArtifact'})     RETURN 'ResourceArtifact'     AS stereotype, count(n) AS instances;

// --- Relationship-stereotype additions (#75 RC #3) ---------------------------
// Edges typed via the stereotype map; raw count is enough to confirm wiring.
MATCH ()-[r:INFORMATION_FLOW]->() RETURN 'INFORMATION_FLOW edges' AS stereotype, count(r) AS instances;
MATCH ()-[r:CONNECTED_TO]->()     RETURN 'CONNECTED_TO edges'     AS stereotype, count(r) AS instances;

// --- Domain breakdown (sanity) -----------------------------------------------
// Pre-fix: OPERATIONAL and RESOURCE counts were significantly lower than the
// containment tree showed because of the first-match-wins / classifier-recursion
// gaps. Post-fix these should align with the model's actual containment tree.
MATCH (n)-[:INSTANCE_OF]->(:Stereotype) RETURN n.domain AS domain, count(n) AS instances ORDER BY domain;

// --- Unmatched-stereotype diagnostic (#75 acceptance) ------------------------
// The summary dialog surfaces this same data, but a Cypher-level check helps
// when verifying CI / scripted exports. If any non-empty unmatched_stereotype
// node appears, the registry is missing entries.
MATCH (n)-[:INSTANCE_OF]->(:Stereotype)
WHERE n.stereotype IS NULL OR n.stereotype = ''
RETURN 'Elements with empty stereotype' AS issue, count(n) AS instances;
