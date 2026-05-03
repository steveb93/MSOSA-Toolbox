// =============================================================================
// UAF Neo4j Graph Initialisation
// Run this ONCE against your Docker Neo4j instance before the first export.
// Assumes Neo4j 5.x (APOC available).
//
//   cypher-shell -u neo4j -p Password123 -f init_uaf_graph.cypher
// =============================================================================

// --- Constraints (idempotency guarantees) ------------------------------------

CREATE CONSTRAINT uaf_element_id IF NOT EXISTS
  FOR (n:UAFElement) REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT stereotype_name IF NOT EXISTS
  FOR (s:Stereotype) REQUIRE s.name IS UNIQUE;

CREATE CONSTRAINT domain_name IF NOT EXISTS
  FOR (d:Domain) REQUIRE d.name IS UNIQUE;

CREATE CONSTRAINT layer_name IF NOT EXISTS
  FOR (l:ArchitectureLayer) REQUIRE l.name IS UNIQUE;

// --- Full-text search index --------------------------------------------------

CREATE FULLTEXT INDEX uaf_element_text IF NOT EXISTS
  FOR (n:UAFElement) ON EACH [n.name, n.qualifiedName, n.documentation];

// --- Performance indexes -----------------------------------------------------

CREATE INDEX uaf_element_domain IF NOT EXISTS
  FOR (n:UAFElement) ON (n.domain);

CREATE INDEX uaf_element_layer IF NOT EXISTS
  FOR (n:UAFElement) ON (n.layer);

CREATE INDEX uaf_element_stereotype IF NOT EXISTS
  FOR (n:UAFElement) ON (n.stereotype);

CREATE INDEX uaf_element_diagramId IF NOT EXISTS
  FOR (n:UAFElement) ON (n.diagramId);

// --- Domain anchor nodes -----------------------------------------------------

MERGE (:Domain {name: 'STRATEGIC',    description: 'Strategic View (StV)'});
MERGE (:Domain {name: 'OPERATIONAL',  description: 'Operational View (OV)'});
MERGE (:Domain {name: 'RESOURCE',     description: 'Resource View (RsV)'});
MERGE (:Domain {name: 'SERVICE',      description: 'Service View (SvcV)'});
MERGE (:Domain {name: 'PERSONNEL',    description: 'Personnel View (PrV)'});
MERGE (:Domain {name: 'ACQUISITION',  description: 'Acquisition View (AcV)'});
MERGE (:Domain {name: 'SECURITY',     description: 'Security View (SrV)'});
MERGE (:Domain {name: 'SHARED',       description: 'Cross-cutting / Shared'});

// --- Architecture layer anchor nodes -----------------------------------------

MERGE (:ArchitectureLayer {name: 'CONCEPTUAL', description: 'Conceptual layer — goals, needs'});
MERGE (:ArchitectureLayer {name: 'LOGICAL',    description: 'Logical layer — functions, services'});
MERGE (:ArchitectureLayer {name: 'PHYSICAL',   description: 'Physical layer — implementations'});
MERGE (:ArchitectureLayer {name: 'ALL',        description: 'Spans all layers'});

// --- UAF 1.2 Stereotype nodes (domain metamodel) -----------------------------
// These are the nodes that exported UAF instances link to via :INSTANCE_OF.

// Strategic
MERGE (:Stereotype {name: 'Capability',               domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'CapabilityConfiguration',  domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'CapabilityComposition',    domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'CapabilityDependency',     domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'CapabilitySpecialization', domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'Vision',                   domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'EndState',                 domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'DesiredEffect',            domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'EnterprisePhase',          domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'CapabilityIncrement',      domain: 'STRATEGIC',   layer: 'CONCEPTUAL'});

// Operational
MERGE (:Stereotype {name: 'OperationalPerformer',     domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalActivity',      domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalExchange',      domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalCapability',    domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalConnector',     domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalDomain',        domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalProcess',       domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalFunction',      domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalInteraction',   domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'NeedLine',                 domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'PerformerPort',            domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OperationalRole',          domain: 'OPERATIONAL', layer: 'CONCEPTUAL'});

// Resource
MERGE (:Stereotype {name: 'ResourcePerformer',        domain: 'RESOURCE',    layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ResourceFunction',         domain: 'RESOURCE',    layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ResourceInteraction',      domain: 'RESOURCE',    layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ResourceArtifact',         domain: 'RESOURCE',    layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'HardwareElement',          domain: 'RESOURCE',    layer: 'PHYSICAL'});
MERGE (:Stereotype {name: 'SoftwareElement',          domain: 'RESOURCE',    layer: 'PHYSICAL'});
MERGE (:Stereotype {name: 'NaturalResource',          domain: 'RESOURCE',    layer: 'PHYSICAL'});
MERGE (:Stereotype {name: 'SystemBlock',              domain: 'RESOURCE',    layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ActualSystem',             domain: 'RESOURCE',    layer: 'PHYSICAL'});
MERGE (:Stereotype {name: 'LogicalArchitecture',      domain: 'RESOURCE',    layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'PhysicalArchitecture',     domain: 'RESOURCE',    layer: 'PHYSICAL'});

// Service
MERGE (:Stereotype {name: 'ServicePerformer',         domain: 'SERVICE',     layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ServiceFunction',          domain: 'SERVICE',     layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ServiceSpecification',     domain: 'SERVICE',     layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ServiceInterface',         domain: 'SERVICE',     layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ServicePoint',             domain: 'SERVICE',     layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ServiceConnector',         domain: 'SERVICE',     layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'ServiceExchange',          domain: 'SERVICE',     layer: 'LOGICAL'});

// Personnel
MERGE (:Stereotype {name: 'Organization',             domain: 'PERSONNEL',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'OrganizationalResource',   domain: 'PERSONNEL',   layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'Post',                     domain: 'PERSONNEL',   layer: 'LOGICAL'});
MERGE (:Stereotype {name: 'PersonnelActivity',        domain: 'PERSONNEL',   layer: 'CONCEPTUAL'});
MERGE (:Stereotype {name: 'ActualOrganization',       domain: 'PERSONNEL',   layer: 'PHYSICAL'});
MERGE (:Stereotype {name: 'OrganizationalCapability', domain: 'PERSONNEL',   layer: 'CONCEPTUAL'});

// Acquisition
MERGE (:Stereotype {name: 'Project',                  domain: 'ACQUISITION', layer: 'ALL'});
MERGE (:Stereotype {name: 'Milestone',                domain: 'ACQUISITION', layer: 'ALL'});
MERGE (:Stereotype {name: 'ProjectMilestone',         domain: 'ACQUISITION', layer: 'ALL'});
MERGE (:Stereotype {name: 'ProjectBoundary',          domain: 'ACQUISITION', layer: 'ALL'});

// Security
MERGE (:Stereotype {name: 'SecurityDomain',           domain: 'SECURITY',    layer: 'ALL'});
MERGE (:Stereotype {name: 'SecurityAsset',            domain: 'SECURITY',    layer: 'ALL'});
MERGE (:Stereotype {name: 'SecurityPolicy',           domain: 'SECURITY',    layer: 'ALL'});

// Shared
MERGE (:Stereotype {name: 'Measurement',              domain: 'SHARED',      layer: 'ALL'});
MERGE (:Stereotype {name: 'Standard',                 domain: 'SHARED',      layer: 'ALL'});
MERGE (:Stereotype {name: 'Condition',                domain: 'SHARED',      layer: 'ALL'});
MERGE (:Stereotype {name: 'Location',                 domain: 'SHARED',      layer: 'ALL'});
MERGE (:Stereotype {name: 'ActualLocation',           domain: 'SHARED',      layer: 'PHYSICAL'});

// --- Wire Stereotype nodes to their Domain -----------------------------------

MATCH (s:Stereotype), (d:Domain {name: s.domain})
MERGE (s)-[:BELONGS_TO]->(d);

// --- Wire Stereotype nodes to their ArchitectureLayer ------------------------

MATCH (s:Stereotype), (l:ArchitectureLayer {name: s.layer})
MERGE (s)-[:IN_LAYER]->(l);

RETURN "UAF graph initialised." AS status;
