// =============================================================================
// UAF Neo4j Graph Initialisation
// Run this ONCE against your Docker Neo4j instance before the first export.
//
//   cypher-shell -u neo4j -p Password123 -f cypher/init_uaf_graph.cypher
//
// Node identity: exported UAF elements carry only their stereotype label
// (e.g. :Capability, :OperationalPerformer) and are keyed on 'id' (the MSOSA
// element ID — globally unique per model and stable across re-exports).
// Names are NOT unique (elements in different domains may share names).
// =============================================================================

// --- Constraints -------------------------------------------------------------

CREATE CONSTRAINT system_model_id IF NOT EXISTS
  FOR (m:SystemModel) REQUIRE m.id IS UNIQUE;

CREATE CONSTRAINT stereotype_name IF NOT EXISTS
  FOR (s:Stereotype) REQUIRE s.name IS UNIQUE;

CREATE CONSTRAINT modelling_language_name IF NOT EXISTS
  FOR (l:ModellingLanguage) REQUIRE l.name IS UNIQUE;

CREATE CONSTRAINT domain_name IF NOT EXISTS
  FOR (d:Domain) REQUIRE d.name IS UNIQUE;

CREATE INDEX system_model_name IF NOT EXISTS
  FOR (m:SystemModel) ON (m.name);

// --- Full-text search index --------------------------------------------------
// Covers the most commonly queried stereotype labels.

CREATE FULLTEXT INDEX uaf_element_text IF NOT EXISTS
  FOR (n:Capability|OperationalPerformer|OperationalActivity|ResourcePerformer|
       ResourceArtifact|HardwareElement|SoftwareElement|ServicePerformer|
       ServiceFunction|Organization|Project|SecurityDomain|Measurement)
  ON EACH [n.name, n.qualifiedName, n.documentation];

// --- Modelling language anchor nodes -----------------------------------------

MERGE (:ModellingLanguage {name: 'UAF',   version: '1.2'});
MERGE (:ModellingLanguage {name: 'SysML', version: '1.6'});
MERGE (:ModellingLanguage {name: 'UML',   version: '2.5'});
MERGE (:ModellingLanguage {name: 'BPMN',  version: '2.0'});

// --- Domain anchor nodes -----------------------------------------------------

MERGE (:Domain {name: 'STRATEGIC',    description: 'Strategic View (StV)'});
MERGE (:Domain {name: 'OPERATIONAL',  description: 'Operational View (OV)'});
MERGE (:Domain {name: 'RESOURCE',     description: 'Resource View (RsV)'});
MERGE (:Domain {name: 'SERVICE',      description: 'Service View (SvcV)'});
MERGE (:Domain {name: 'PERSONNEL',    description: 'Personnel View (PrV)'});
MERGE (:Domain {name: 'ACQUISITION',  description: 'Acquisition View (AcV)'});
MERGE (:Domain {name: 'SECURITY',     description: 'Security View (SrV)'});
MERGE (:Domain {name: 'SHARED',       description: 'Cross-cutting / Shared'});

// --- UAF 1.2 Stereotype nodes (domain metamodel) -----------------------------
// These are the nodes that exported UAF instances link to via :INSTANCE_OF.

// Strategic
MERGE (:Stereotype {name: 'Capability',               domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'CapabilityConfiguration',  domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'CapabilityComposition',    domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'CapabilityDependency',     domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'CapabilitySpecialization', domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'Vision',                   domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'EndState',                 domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'DesiredEffect',            domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'EnterprisePhase',          domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'CapabilityIncrement',      domain: 'STRATEGIC'});
// Tier-1 #75 RC #6 reconciliation
MERGE (:Stereotype {name: 'EnterpriseVision',         domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'VisionStatement',          domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'CapabilityRole',           domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'PhaseableElement',         domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'Phases',                   domain: 'STRATEGIC'});
MERGE (:Stereotype {name: 'ArchitectureMetadata',     domain: 'STRATEGIC'});

// Operational
MERGE (:Stereotype {name: 'OperationalPerformer',     domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalActivity',      domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalExchange',      domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalCapability',    domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalConnector',     domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalDomain',        domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalProcess',       domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalFunction',      domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalInteraction',   domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalInformation',   domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'NeedLine',                 domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'PerformerPort',            domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalRole',          domain: 'OPERATIONAL'});
// Tier-1 #75 RC #6 reconciliation
MERGE (:Stereotype {name: 'OperationalAgent',           domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalAsset',           domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalArchitecture',    domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalActivityAction',  domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalActivityEdge',    domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalControlFlow',     domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalObjectFlow',      domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalExchangeItem',    domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalInformationRole', domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalInterface',       domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalMessage',         domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalMethod',          domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalMitigation',      domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalParameter',       domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalPort',            domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalSignal',          domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalSignalProperty',  domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalStateDescription',domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalConstraint',      domain: 'OPERATIONAL'});

// BPMN data elements (operational information artifacts used in process diagrams)
MERGE (:Stereotype {name: 'DataObject',               domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'DataInput',                domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'DataOutput',               domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'DataStore',                domain: 'OPERATIONAL'});

// Resource
MERGE (:Stereotype {name: 'ResourcePerformer',        domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceFunction',         domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceInteraction',      domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceArtifact',         domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceInformation',      domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourcePort',             domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceConnector',        domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceArchitecture',     domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceSystem',           domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'HardwareElement',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'SoftwareElement',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'Software',                 domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'NaturalResource',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'SystemBlock',              domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'System',                   domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ActualSystem',             domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'Technology',               domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'LogicalArchitecture',      domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'PhysicalArchitecture',     domain: 'RESOURCE'});
// Tier-1 #75 RC #6 reconciliation
MERGE (:Stereotype {name: 'Resource',                 domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceAsset',            domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceAction',           domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceExchange',         domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceExchangeItem',     domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceInformationRole',  domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceInterface',        domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceMessage',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceMethod',           domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceMitigation',       domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceRole',             domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceService',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceServiceInterface', domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceSignal',           domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceSignalProperty',   domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceStateDescription', domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceConstraint',       domain: 'RESOURCE'});

// Service
MERGE (:Stereotype {name: 'ServicePerformer',         domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceFunction',          domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceSpecification',     domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceInterface',         domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServicePoint',             domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceConnector',         domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceExchange',          domain: 'SERVICE'});
MERGE (:Stereotype {name: 'Service',                  domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceArchitecture',      domain: 'SERVICE'});
// Tier-1 #75 RC #6 reconciliation
MERGE (:Stereotype {name: 'ServiceRole',              domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceParameter',         domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceMethod',            domain: 'SERVICE'});

// Personnel
MERGE (:Stereotype {name: 'Organization',             domain: 'PERSONNEL'});
MERGE (:Stereotype {name: 'OrganizationalResource',   domain: 'PERSONNEL'});
MERGE (:Stereotype {name: 'Post',                     domain: 'PERSONNEL'});
MERGE (:Stereotype {name: 'PersonnelActivity',        domain: 'PERSONNEL'});
MERGE (:Stereotype {name: 'ActualOrganization',       domain: 'PERSONNEL'});
MERGE (:Stereotype {name: 'OrganizationalCapability', domain: 'PERSONNEL'});

// Acquisition
MERGE (:Stereotype {name: 'Project',                  domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'Milestone',                domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectMilestone',         domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectBoundary',          domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'FundingRequest',           domain: 'ACQUISITION'});
// Tier-1 #75 RC #6 reconciliation
MERGE (:Stereotype {name: 'ProjectActivity',          domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectActivityAction',    domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectMilestoneRole',     domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectRole',              domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectSequence',          domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectStatus',            domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'ProjectTheme',             domain: 'ACQUISITION'});
MERGE (:Stereotype {name: 'MilestoneDependency',      domain: 'ACQUISITION'});

// Security
MERGE (:Stereotype {name: 'SecurityDomain',           domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityAsset',            domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityPolicy',           domain: 'SECURITY'});
// Tier-1 #75 RC #6 reconciliation
MERGE (:Stereotype {name: 'SecurityEnclave',          domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityConstraint',       domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityControl',          domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityControlFamily',    domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityProcess',          domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityProcessAction',    domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityRisk',             domain: 'SECURITY'});

// Shared
MERGE (:Stereotype {name: 'Measurement',              domain: 'SHARED'});
MERGE (:Stereotype {name: 'Standard',                 domain: 'SHARED'});
MERGE (:Stereotype {name: 'Condition',                domain: 'SHARED'});
MERGE (:Stereotype {name: 'ConfigurationItem',        domain: 'SHARED'});
MERGE (:Stereotype {name: 'ImplementationConstraint', domain: 'SHARED'});
MERGE (:Stereotype {name: 'Location',                 domain: 'SHARED'});
MERGE (:Stereotype {name: 'ActualLocation',           domain: 'SHARED'});

// ERD / Information modelling (#76).
// Entity / EntityRelationship may be applied by users to UML Class / Association
// for explicit ERD modelling. Attribute and DataType are synthetic — emitted by
// the traverser for first-class attribute representation (Option A in #76).
MERGE (:Stereotype {name: 'Entity',                   domain: 'SHARED'});
MERGE (:Stereotype {name: 'EntityRelationship',       domain: 'SHARED'});
// Tier-1 #75 RC #6 reconciliation — names actually used by the MSOSA UAF profile
MERGE (:Stereotype {name: 'EntityRelation',           domain: 'SHARED'});
MERGE (:Stereotype {name: 'PrimaryKey',               domain: 'SHARED'});
MERGE (:Stereotype {name: 'ForeignKey',               domain: 'SHARED'});
MERGE (:Stereotype {name: 'AlternativeKey',           domain: 'SHARED'});
MERGE (:Stereotype {name: 'FK',                       domain: 'SHARED'});
MERGE (:Stereotype {name: 'Attribute',                domain: 'SHARED'});
MERGE (:Stereotype {name: 'DataType',                 domain: 'SHARED'});

// --- SysML 1.6 Stereotype nodes ----------------------------------------------

MERGE (:Stereotype {name: 'Block',                  language: 'SysML'});
MERGE (:Stereotype {name: 'Requirement',             language: 'SysML'});
MERGE (:Stereotype {name: 'InterfaceBlock',          language: 'SysML'});
MERGE (:Stereotype {name: 'ValueType',               language: 'SysML'});
MERGE (:Stereotype {name: 'ConstraintBlock',         language: 'SysML'});
MERGE (:Stereotype {name: 'FlowSpecification',       language: 'SysML'});
MERGE (:Stereotype {name: 'FlowPort',                language: 'SysML'});
MERGE (:Stereotype {name: 'FullPort',                language: 'SysML'});
MERGE (:Stereotype {name: 'ProxyPort',               language: 'SysML'});
MERGE (:Stereotype {name: 'ItemFlow',                language: 'SysML'});

// --- BPMN 2.0 Stereotype nodes -----------------------------------------------

MERGE (:Stereotype {name: 'Task',                    language: 'BPMN'});
MERGE (:Stereotype {name: 'UserTask',                language: 'BPMN'});
MERGE (:Stereotype {name: 'ServiceTask',             language: 'BPMN'});
MERGE (:Stereotype {name: 'SendTask',                language: 'BPMN'});
MERGE (:Stereotype {name: 'ReceiveTask',             language: 'BPMN'});
MERGE (:Stereotype {name: 'StartEvent',              language: 'BPMN'});
MERGE (:Stereotype {name: 'EndEvent',                language: 'BPMN'});
MERGE (:Stereotype {name: 'IntermediateThrowEvent',  language: 'BPMN'});
MERGE (:Stereotype {name: 'IntermediateCatchEvent',  language: 'BPMN'});
MERGE (:Stereotype {name: 'ExclusiveGateway',        language: 'BPMN'});
MERGE (:Stereotype {name: 'ParallelGateway',         language: 'BPMN'});
MERGE (:Stereotype {name: 'InclusiveGateway',        language: 'BPMN'});
MERGE (:Stereotype {name: 'EventBasedGateway',       language: 'BPMN'});
MERGE (:Stereotype {name: 'SubProcess',              language: 'BPMN'});
MERGE (:Stereotype {name: 'CallActivity',            language: 'BPMN'});
MERGE (:Stereotype {name: 'Lane',                    language: 'BPMN'});
MERGE (:Stereotype {name: 'Pool',                    language: 'BPMN'});
// Tier-1 #75 RC #6 reconciliation — BPMN extras
MERGE (:Stereotype {name: 'BPMNProcess',                    language: 'BPMN'});
MERGE (:Stereotype {name: 'BPMNMessage',                    language: 'BPMN'});
MERGE (:Stereotype {name: 'BusinessRuleTask',               language: 'BPMN'});
MERGE (:Stereotype {name: 'LaneSet',                        language: 'BPMN'});
MERGE (:Stereotype {name: 'TextAnnotation',                 language: 'BPMN'});
MERGE (:Stereotype {name: 'NoneStartEvent',                 language: 'BPMN'});
MERGE (:Stereotype {name: 'NoneEndEvent',                   language: 'BPMN'});
MERGE (:Stereotype {name: 'NoneIntermediateEvent',          language: 'BPMN'});
MERGE (:Stereotype {name: 'MessageCatchIntermediateEvent',  language: 'BPMN'});
MERGE (:Stereotype {name: 'StandardLoopCharacteristics',    language: 'BPMN'});
MERGE (:Stereotype {name: 'FlowProperty',                   language: 'BPMN'});

// --- Wire Stereotype nodes to their Domain -----------------------------------

MATCH (s:Stereotype), (d:Domain {name: s.domain})
MERGE (s)-[:BELONGS_TO]->(d);

// --- Back-fill language on UAF Stereotype nodes (idempotent) -----------------

MATCH (s:Stereotype) WHERE s.language IS NULL
SET s.language = 'UAF';

// --- Mark bare-noun fallback stereotypes (idempotent) ------------------------
// These are legal UAF stereotypes whose simple names act as catchment ancestors
// for more specific custom stereotypes in real profiles (notably the real-world UAF
// profile, where bare `Resource` was applied as an ancestor of operational
// performer subtypes). UAFStereotypeRegistry treats them as fallback during
// element export: the more specific ancestor wins when present. Surfacing the
// flag on the metamodel node lets downstream consumers (ontology codegen,
// SPARQL queries, NeoDash dashboards) reason about ambiguity without reading
// the Java registry. Unset on every other Stereotype — absent ≡ false.

MATCH (s:Stereotype)
WHERE s.name IN ['Resource', 'Service', 'System', 'Software', 'SystemBlock', 'Technology']
SET s.isFallback = true;

// --- Wire all Stereotype nodes to their ModellingLanguage --------------------

MATCH (s:Stereotype), (l:ModellingLanguage {name: s.language})
MERGE (s)-[:DEFINED_BY]->(l);

RETURN "UAF graph initialised." AS status;
