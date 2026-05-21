// Data artefact + ERD regression queries (issue #76).
//
// Run AFTER an MSOSA export against a model that exercises:
//   - BPMN operational processes with DataObject / DataInput / DataOutput / DataStore
//   - ERD entities (any Classifier stereotyped Entity, OperationalInformation,
//     or ResourceInformation, with attributes)
//
// Each query is an assertion of the form "this stereotype / edge must exist
// post-fix". The matched Python harness in Test/test_data_er_regression.py
// runs the same queries with pytest.
//
// Usage:
//   cypher-shell -u neo4j -p Password123 -f Test/queries/regression_data_er.cypher

// --- Data artefacts (#76 RC #1, #2) ------------------------------------------
// Reachable post-PR2 (classifier recursion) and exported under their registry labels.
MATCH (n {stereotype: 'DataObject'}) RETURN 'DataObject' AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'DataInput'})  RETURN 'DataInput'  AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'DataOutput'}) RETURN 'DataOutput' AS stereotype, count(n) AS instances;
MATCH (n {stereotype: 'DataStore'})  RETURN 'DataStore'  AS stereotype, count(n) AS instances;

// --- Data-association edges (#76 RC #3) --------------------------------------
// Wires Task <- consumes -> DataInput and Task -> produces -> DataOutput.
MATCH ()-[r:DATA_INPUT]->()  RETURN 'DATA_INPUT edges'  AS rel, count(r) AS instances;
MATCH ()-[r:DATA_OUTPUT]->() RETURN 'DATA_OUTPUT edges' AS rel, count(r) AS instances;

// --- First-class ERD attribute representation (#76 design A) -----------------
// Attribute nodes and the HAS_ATTRIBUTE / OF_TYPE edges that connect them.
MATCH (n:Attribute) RETURN 'Attribute nodes'           AS stereotype, count(n) AS instances;
MATCH ()-[r:HAS_ATTRIBUTE]->() RETURN 'HAS_ATTRIBUTE edges' AS rel, count(r) AS instances;
MATCH ()-[r:OF_TYPE]->()       RETURN 'OF_TYPE edges'       AS rel, count(r) AS instances;
MATCH (n:DataType)  RETURN 'DataType nodes'            AS stereotype, count(n) AS instances;

// --- Multiplicity on association edges (#76 RC #6) ---------------------------
// At least some Association-typed edges should carry the new srcMult/tgtMult.
MATCH ()-[r]->()
WHERE r.srcMult <> '' OR r.tgtMult <> ''
RETURN 'Edges with multiplicity' AS metric, count(r) AS instances;

// --- Entity / Information modelling ------------------------------------------
// Entities can appear as Entity, OperationalInformation, or ResourceInformation.
MATCH (n)
WHERE n.stereotype IN ['Entity', 'OperationalInformation', 'ResourceInformation']
RETURN n.stereotype AS stereotype, count(n) AS instances;

// --- Sanity: no Attribute without OF_TYPE edge -------------------------------
// Every :Attribute should either point at a DataType or at an Entity it references.
MATCH (a:Attribute)
WHERE NOT (a)-[:OF_TYPE]->()
RETURN 'Attributes without OF_TYPE' AS issue, count(a) AS instances;
