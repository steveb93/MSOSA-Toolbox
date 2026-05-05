// =============================================================================
// UAF Neo4j Graph Initialisation
// Run this ONCE against your Docker Neo4j instance before the first export.
//
//   cypher-shell -u neo4j -p Password123 -f init_uaf_graph.cypher
//
// Node identity: every UAF element node carries only its stereotype label
// (e.g. :Capability, :OperationalPerformer) and is keyed on the 'id' property,
// which holds the MSOSA element ID — globally unique per model and stable
// across re-exports.  Names are NOT unique (elements in different domains
// may share names), so never use name as a merge key.
// =============================================================================

// --- Constraints -------------------------------------------------------------

CREATE CONSTRAINT system_model_id IF NOT EXISTS
  FOR (m:SystemModel) REQUIRE m.id IS UNIQUE;

CREATE CONSTRAINT stereotype_name IF NOT EXISTS
  FOR (s:Stereotype) REQUIRE s.name IS UNIQUE;

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
MERGE (:Stereotype {name: 'NeedLine',                 domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'PerformerPort',            domain: 'OPERATIONAL'});
MERGE (:Stereotype {name: 'OperationalRole',          domain: 'OPERATIONAL'});

// Resource
MERGE (:Stereotype {name: 'ResourcePerformer',        domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceFunction',         domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceInteraction',      domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceArtifact',         domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourcePort',             domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ResourceConnector',        domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'HardwareElement',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'SoftwareElement',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'NaturalResource',          domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'SystemBlock',              domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'ActualSystem',             domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'LogicalArchitecture',      domain: 'RESOURCE'});
MERGE (:Stereotype {name: 'PhysicalArchitecture',     domain: 'RESOURCE'});

// Service
MERGE (:Stereotype {name: 'ServicePerformer',         domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceFunction',          domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceSpecification',     domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceInterface',         domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServicePoint',             domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceConnector',         domain: 'SERVICE'});
MERGE (:Stereotype {name: 'ServiceExchange',          domain: 'SERVICE'});

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

// Security
MERGE (:Stereotype {name: 'SecurityDomain',           domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityAsset',            domain: 'SECURITY'});
MERGE (:Stereotype {name: 'SecurityPolicy',           domain: 'SECURITY'});

// Shared
MERGE (:Stereotype {name: 'Measurement',              domain: 'SHARED'});
MERGE (:Stereotype {name: 'Standard',                 domain: 'SHARED'});
MERGE (:Stereotype {name: 'Condition',                domain: 'SHARED'});
MERGE (:Stereotype {name: 'ConfigurationItem',        domain: 'SHARED'});
MERGE (:Stereotype {name: 'ImplementationConstraint', domain: 'SHARED'});
MERGE (:Stereotype {name: 'Location',                 domain: 'SHARED'});
MERGE (:Stereotype {name: 'ActualLocation',           domain: 'SHARED'});

// --- Wire Stereotype nodes to their Domain -----------------------------------

MATCH (s:Stereotype), (d:Domain {name: s.domain})
MERGE (s)-[:BELONGS_TO]->(d);

RETURN "UAF graph initialised." AS status;
