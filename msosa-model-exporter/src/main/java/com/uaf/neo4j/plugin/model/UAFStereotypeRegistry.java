package com.uaf.neo4j.plugin.model;

import java.util.*;

/**
 * Single source of truth mapping stereotype names to Neo4j label, UAF domain,
 * and modelling language (UAF, SysML, or BPMN).
 *
 * UAF entries carry a Domain enum value; SysML and BPMN entries have domain=null
 * since the UAF domain concept does not apply to those languages.
 *
 * Stereotype names are case-sensitive and must match what the MSOSA profile
 * reports exactly. Verify UAF names via the MSOSA scripting console:
 *   StereotypesHelper.getAllStereotypes(project)
 */
public class UAFStereotypeRegistry {

    public enum Domain {
        STRATEGIC, OPERATIONAL, RESOURCE, SERVICE, PERSONNEL, ACQUISITION, SECURITY, SHARED
    }

    public static final class StereotypeInfo {
        public final String neo4jLabel;
        public final Domain domain;   // null for non-UAF stereotypes
        public final String language;

        StereotypeInfo(String neo4jLabel, Domain domain, String language) {
            this.neo4jLabel = neo4jLabel;
            this.domain     = domain;
            this.language   = language;
        }
    }

    private static final Map<String, StereotypeInfo> REGISTRY = new LinkedHashMap<>();

    static {
        // --- Strategic View (StV) ---
        reg("Capability",               "Capability",              Domain.STRATEGIC);
        reg("CapabilityConfiguration",  "CapabilityConfiguration", Domain.STRATEGIC);
        reg("CapabilityComposition",    "CapabilityComposition",   Domain.STRATEGIC);
        reg("CapabilityDependency",     "CapabilityDependency",    Domain.STRATEGIC);
        reg("CapabilitySpecialization", "CapabilitySpecialization",Domain.STRATEGIC);
        reg("Vision",                   "Vision",                  Domain.STRATEGIC);
        reg("EndState",                 "EndState",                Domain.STRATEGIC);
        reg("DesiredEffect",            "DesiredEffect",           Domain.STRATEGIC);
        reg("EnterprisePhase",          "EnterprisePhase",         Domain.STRATEGIC);
        reg("CapabilityIncrement",      "CapabilityIncrement",     Domain.STRATEGIC);
        // Tier-1 #75 RC #6 reconciliation — added from real-world profile diff. The MSOSA UAF 1.2
        // profile here uses EnterpriseVision / VisionStatement rather than the bare
        // Vision; both are kept for back-compat across other UAF profile versions.
        reg("EnterpriseVision",         "EnterpriseVision",        Domain.STRATEGIC);
        reg("VisionStatement",          "VisionStatement",         Domain.STRATEGIC);
        reg("CapabilityRole",           "CapabilityRole",          Domain.STRATEGIC);
        reg("PhaseableElement",         "PhaseableElement",        Domain.STRATEGIC);
        reg("Phases",                   "Phases",                  Domain.STRATEGIC);
        reg("ArchitectureMetadata",     "ArchitectureMetadata",    Domain.STRATEGIC);

        // --- Operational View (OV) ---
        reg("OperationalPerformer",     "OperationalPerformer",    Domain.OPERATIONAL);
        reg("OperationalActivity",      "OperationalActivity",     Domain.OPERATIONAL);
        reg("OperationalExchange",      "OperationalExchange",     Domain.OPERATIONAL);
        reg("OperationalCapability",    "OperationalCapability",   Domain.OPERATIONAL);
        reg("OperationalConnector",     "OperationalConnector",    Domain.OPERATIONAL);
        reg("OperationalDomain",        "OperationalDomain",       Domain.OPERATIONAL);
        reg("OperationalProcess",       "OperationalProcess",      Domain.OPERATIONAL);
        reg("OperationalFunction",      "OperationalFunction",     Domain.OPERATIONAL);
        reg("OperationalInteraction",   "OperationalInteraction",  Domain.OPERATIONAL);
        reg("OperationalInformation",   "OperationalInformation",  Domain.OPERATIONAL);
        reg("NeedLine",                 "NeedLine",                Domain.OPERATIONAL);
        reg("PerformerPort",            "PerformerPort",           Domain.OPERATIONAL);
        reg("OperationalRole",          "OperationalRole",         Domain.OPERATIONAL);
        // Tier-1 #75 RC #6 reconciliation — added from real-world UAF profile diff.
        // Stereotypes confirmed present in the MSOSA UAF 1.2 profile and applied to
        // model elements; pre-reconciliation these were silently dropped.
        reg("OperationalAgent",         "OperationalAgent",        Domain.OPERATIONAL);
        reg("OperationalAsset",         "OperationalAsset",        Domain.OPERATIONAL);
        reg("OperationalArchitecture",  "OperationalArchitecture", Domain.OPERATIONAL);
        reg("OperationalActivityAction","OperationalActivityAction",Domain.OPERATIONAL);
        reg("OperationalActivityEdge",  "OperationalActivityEdge", Domain.OPERATIONAL);
        reg("OperationalControlFlow",   "OperationalControlFlow",  Domain.OPERATIONAL);
        reg("OperationalObjectFlow",    "OperationalObjectFlow",   Domain.OPERATIONAL);
        reg("OperationalExchangeItem",  "OperationalExchangeItem", Domain.OPERATIONAL);
        reg("OperationalInformationRole","OperationalInformationRole",Domain.OPERATIONAL);
        reg("OperationalInterface",     "OperationalInterface",    Domain.OPERATIONAL);
        reg("OperationalMessage",       "OperationalMessage",      Domain.OPERATIONAL);
        reg("OperationalMethod",        "OperationalMethod",       Domain.OPERATIONAL);
        reg("OperationalMitigation",    "OperationalMitigation",   Domain.OPERATIONAL);
        reg("OperationalParameter",     "OperationalParameter",    Domain.OPERATIONAL);
        reg("OperationalPort",          "OperationalPort",         Domain.OPERATIONAL);
        reg("OperationalSignal",        "OperationalSignal",       Domain.OPERATIONAL);
        reg("OperationalSignalProperty","OperationalSignalProperty",Domain.OPERATIONAL);
        reg("OperationalStateDescription","OperationalStateDescription",Domain.OPERATIONAL);
        reg("OperationalConstraint",    "OperationalConstraint",   Domain.OPERATIONAL);

        // --- UAF-wrapped BPMN data elements (used in UAF operational process diagrams) ---
        // These are UAF stereotypes applied to BPMN data artefacts in an OV context,
        // distinct from the native BPMN 2.0 process elements registered below.
        reg("DataObject",               "DataObject",              Domain.OPERATIONAL);
        reg("DataInput",                "DataInput",               Domain.OPERATIONAL);
        reg("DataOutput",               "DataOutput",              Domain.OPERATIONAL);
        reg("DataStore",                "DataStore",               Domain.OPERATIONAL);

        // --- Resource View (RsV) ---
        reg("ResourcePerformer",        "ResourcePerformer",       Domain.RESOURCE);
        reg("ResourceFunction",         "ResourceFunction",        Domain.RESOURCE);
        reg("ResourceInteraction",      "ResourceInteraction",     Domain.RESOURCE);
        reg("ResourceArtifact",         "ResourceArtifact",        Domain.RESOURCE);
        reg("ResourceInformation",      "ResourceInformation",     Domain.RESOURCE);
        reg("ResourcePort",             "ResourcePort",            Domain.RESOURCE);
        reg("ResourceConnector",        "ResourceConnector",       Domain.RESOURCE);
        reg("ResourceArchitecture",     "ResourceArchitecture",    Domain.RESOURCE);
        reg("ResourceSystem",           "ResourceSystem",          Domain.RESOURCE);
        reg("HardwareElement",          "HardwareElement",         Domain.RESOURCE);
        reg("SoftwareElement",          "SoftwareElement",         Domain.RESOURCE);
        reg("Software",                 "Software",                Domain.RESOURCE);
        reg("NaturalResource",          "NaturalResource",         Domain.RESOURCE);
        reg("SystemBlock",              "SystemBlock",             Domain.RESOURCE);
        reg("System",                   "System",                  Domain.RESOURCE);
        reg("ActualSystem",             "ActualSystem",            Domain.RESOURCE);
        reg("Technology",               "Technology",              Domain.RESOURCE);
        reg("LogicalArchitecture",      "LogicalArchitecture",     Domain.RESOURCE);
        reg("PhysicalArchitecture",     "PhysicalArchitecture",    Domain.RESOURCE);
        // Tier-1 #75 RC #6 reconciliation — added from real-world profile diff.
        reg("Resource",                 "Resource",                Domain.RESOURCE);
        reg("ResourceAsset",            "ResourceAsset",           Domain.RESOURCE);
        reg("ResourceAction",           "ResourceAction",          Domain.RESOURCE);
        reg("ResourceExchange",         "ResourceExchange",        Domain.RESOURCE);
        reg("ResourceExchangeItem",     "ResourceExchangeItem",    Domain.RESOURCE);
        reg("ResourceInformationRole",  "ResourceInformationRole", Domain.RESOURCE);
        reg("ResourceInterface",        "ResourceInterface",       Domain.RESOURCE);
        reg("ResourceMessage",          "ResourceMessage",         Domain.RESOURCE);
        reg("ResourceMethod",           "ResourceMethod",          Domain.RESOURCE);
        reg("ResourceMitigation",       "ResourceMitigation",      Domain.RESOURCE);
        reg("ResourceRole",             "ResourceRole",            Domain.RESOURCE);
        reg("ResourceService",          "ResourceService",         Domain.RESOURCE);
        reg("ResourceServiceInterface", "ResourceServiceInterface",Domain.RESOURCE);
        reg("ResourceSignal",           "ResourceSignal",          Domain.RESOURCE);
        reg("ResourceSignalProperty",   "ResourceSignalProperty",  Domain.RESOURCE);
        reg("ResourceStateDescription", "ResourceStateDescription",Domain.RESOURCE);
        reg("ResourceConstraint",       "ResourceConstraint",      Domain.RESOURCE);

        // --- Service View (SvcV) ---
        reg("ServicePerformer",         "ServicePerformer",        Domain.SERVICE);
        reg("ServiceFunction",          "ServiceFunction",         Domain.SERVICE);
        reg("ServiceSpecification",     "ServiceSpecification",    Domain.SERVICE);
        reg("ServiceInterface",         "ServiceInterface",        Domain.SERVICE);
        reg("ServicePoint",             "ServicePoint",            Domain.SERVICE);
        reg("ServiceConnector",         "ServiceConnector",        Domain.SERVICE);
        reg("ServiceExchange",          "ServiceExchange",         Domain.SERVICE);
        reg("Service",                  "Service",                 Domain.SERVICE);
        reg("ServiceArchitecture",      "ServiceArchitecture",     Domain.SERVICE);
        // Tier-1 #75 RC #6 reconciliation — added from real-world profile diff.
        reg("ServiceRole",              "ServiceRole",             Domain.SERVICE);
        reg("ServiceParameter",         "ServiceParameter",        Domain.SERVICE);
        reg("ServiceMethod",            "ServiceMethod",           Domain.SERVICE);

        // --- Personnel View (PrV) ---
        reg("Organization",             "Organization",            Domain.PERSONNEL);
        reg("OrganizationalResource",   "OrganizationalResource",  Domain.PERSONNEL);
        reg("Post",                     "Post",                    Domain.PERSONNEL);
        reg("PersonnelActivity",        "PersonnelActivity",       Domain.PERSONNEL);
        reg("ActualOrganization",       "ActualOrganization",      Domain.PERSONNEL);
        reg("OrganizationalCapability", "OrganizationalCapability",Domain.PERSONNEL);

        // --- Acquisition View (AcV) ---
        reg("Project",                  "Project",                 Domain.ACQUISITION);
        reg("Milestone",                "Milestone",               Domain.ACQUISITION);
        reg("ProjectMilestone",         "ProjectMilestone",        Domain.ACQUISITION);
        reg("ProjectBoundary",          "ProjectBoundary",         Domain.ACQUISITION);
        reg("FundingRequest",           "FundingRequest",          Domain.ACQUISITION);
        // Tier-1 #75 RC #6 reconciliation — added from real-world profile diff.
        reg("ProjectActivity",          "ProjectActivity",         Domain.ACQUISITION);
        reg("ProjectActivityAction",    "ProjectActivityAction",   Domain.ACQUISITION);
        reg("ProjectMilestoneRole",     "ProjectMilestoneRole",    Domain.ACQUISITION);
        reg("ProjectRole",              "ProjectRole",             Domain.ACQUISITION);
        reg("ProjectSequence",          "ProjectSequence",         Domain.ACQUISITION);
        reg("ProjectStatus",            "ProjectStatus",           Domain.ACQUISITION);
        reg("ProjectTheme",             "ProjectTheme",            Domain.ACQUISITION);
        reg("MilestoneDependency",      "MilestoneDependency",     Domain.ACQUISITION);

        // --- Security View (SrV) ---
        reg("SecurityDomain",           "SecurityDomain",          Domain.SECURITY);
        reg("SecurityAsset",            "SecurityAsset",           Domain.SECURITY);
        reg("SecurityPolicy",           "SecurityPolicy",          Domain.SECURITY);
        // Tier-1 #75 RC #6 reconciliation — added from real-world profile diff. The MSOSA UAF 1.2
        // profile in real-world profile uses SecurityEnclave/SecurityControl/etc. rather than the
        // older SecurityDomain/Asset/Policy; both name families coexist for back-compat.
        reg("SecurityEnclave",          "SecurityEnclave",         Domain.SECURITY);
        reg("SecurityConstraint",       "SecurityConstraint",      Domain.SECURITY);
        reg("SecurityControl",          "SecurityControl",         Domain.SECURITY);
        reg("SecurityControlFamily",    "SecurityControlFamily",   Domain.SECURITY);
        reg("SecurityProcess",          "SecurityProcess",         Domain.SECURITY);
        reg("SecurityProcessAction",    "SecurityProcessAction",   Domain.SECURITY);
        reg("SecurityRisk",             "SecurityRisk",            Domain.SECURITY);

        // --- Shared / Cross-cutting ---
        reg("Measurement",              "Measurement",             Domain.SHARED);
        reg("Standard",                 "Standard",                Domain.SHARED);
        reg("Condition",                "Condition",               Domain.SHARED);
        reg("ConfigurationItem",        "ConfigurationItem",       Domain.SHARED);
        reg("ImplementationConstraint", "ImplementationConstraint",Domain.SHARED);
        reg("Location",                 "Location",                Domain.SHARED);
        reg("ActualLocation",           "ActualLocation",          Domain.SHARED);

        // --- ERD / Information modelling (#76) ---
        // Stereotyped entries (matched by name when MSOSA reports them):
        reg("Entity",                   "Entity",                  Domain.SHARED);
        reg("EntityRelationship",       "EntityRelationship",      Domain.SHARED);
        // Tier-1 #75 RC #6 — the MSOSA UAF profile uses EntityRelation (not the
        // EntityRelationship name) and exposes PK/FK marker stereotypes on Properties.
        reg("EntityRelation",           "EntityRelation",          Domain.SHARED);
        reg("PrimaryKey",               "PrimaryKey",              Domain.SHARED);
        reg("ForeignKey",               "ForeignKey",              Domain.SHARED);
        reg("AlternativeKey",           "AlternativeKey",          Domain.SHARED);
        reg("FK",                       "FK",                      Domain.SHARED);
        // Synthetic entries — the traverser emits these for first-class attribute
        // representation (#76 design A). They are NOT directly applied stereotypes
        // in any MSOSA profile; entries exist so the :Stereotype seed nodes and the
        // MVO codegen pick them up uniformly with the rest of the registry.
        reg("Attribute",                "Attribute",               Domain.SHARED);
        reg("DataType",                 "DataType",                Domain.SHARED);

        // --- SysML 1.6 ---
        reg("Block",                    "Block",                   "SysML");
        reg("Requirement",              "Requirement",             "SysML");
        reg("InterfaceBlock",           "InterfaceBlock",          "SysML");
        reg("ValueType",                "ValueType",               "SysML");
        reg("ConstraintBlock",          "ConstraintBlock",         "SysML");
        reg("FlowSpecification",        "FlowSpecification",       "SysML");
        reg("FlowPort",                 "FlowPort",                "SysML");
        reg("FullPort",                 "FullPort",                "SysML");
        reg("ProxyPort",                "ProxyPort",               "SysML");
        reg("ItemFlow",                 "ItemFlow",                "SysML");

        // --- BPMN 2.0 ---
        reg("Task",                     "Task",                    "BPMN");
        reg("UserTask",                 "UserTask",                "BPMN");
        reg("ServiceTask",              "ServiceTask",             "BPMN");
        reg("SendTask",                 "SendTask",                "BPMN");
        reg("ReceiveTask",              "ReceiveTask",             "BPMN");
        reg("StartEvent",               "StartEvent",              "BPMN");
        reg("EndEvent",                 "EndEvent",                "BPMN");
        reg("IntermediateThrowEvent",   "IntermediateThrowEvent",  "BPMN");
        reg("IntermediateCatchEvent",   "IntermediateCatchEvent",  "BPMN");
        reg("ExclusiveGateway",         "ExclusiveGateway",        "BPMN");
        reg("ParallelGateway",          "ParallelGateway",         "BPMN");
        reg("InclusiveGateway",         "InclusiveGateway",        "BPMN");
        reg("EventBasedGateway",        "EventBasedGateway",       "BPMN");
        reg("SubProcess",               "SubProcess",              "BPMN");
        reg("CallActivity",             "CallActivity",            "BPMN");
        reg("Lane",                     "Lane",                    "BPMN");
        reg("Pool",                     "Pool",                    "BPMN");
        // Tier-1 #75 RC #6 reconciliation — BPMN extras found applied in real-world profile.
        reg("BPMNProcess",              "BPMNProcess",             "BPMN");
        reg("BPMNMessage",              "BPMNMessage",             "BPMN");
        reg("BusinessRuleTask",         "BusinessRuleTask",        "BPMN");
        reg("LaneSet",                  "LaneSet",                 "BPMN");
        reg("TextAnnotation",           "TextAnnotation",          "BPMN");
        reg("NoneStartEvent",           "NoneStartEvent",          "BPMN");
        reg("NoneEndEvent",             "NoneEndEvent",            "BPMN");
        reg("NoneIntermediateEvent",    "NoneIntermediateEvent",   "BPMN");
        reg("MessageCatchIntermediateEvent","MessageCatchIntermediateEvent","BPMN");
        reg("StandardLoopCharacteristics","StandardLoopCharacteristics","BPMN");
        reg("FlowProperty",             "FlowProperty",            "BPMN");

    }

    private static void reg(String stereotype, String label, Domain domain) {
        REGISTRY.put(stereotype, new StereotypeInfo(label, domain, "UAF"));
    }

    private static void reg(String stereotype, String label, String language) {
        REGISTRY.put(stereotype, new StereotypeInfo(label, null, language));
    }

    public static Optional<StereotypeInfo> get(String stereotypeName) {
        return Optional.ofNullable(REGISTRY.get(stereotypeName));
    }

    public static boolean isKnown(String stereotypeName) {
        return REGISTRY.containsKey(stereotypeName);
    }

    public static Set<String> allStereotypeNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    public static Map<String, StereotypeInfo> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
