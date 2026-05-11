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

        // --- Security View (SrV) ---
        reg("SecurityDomain",           "SecurityDomain",          Domain.SECURITY);
        reg("SecurityAsset",            "SecurityAsset",           Domain.SECURITY);
        reg("SecurityPolicy",           "SecurityPolicy",          Domain.SECURITY);

        // --- Shared / Cross-cutting ---
        reg("Measurement",              "Measurement",             Domain.SHARED);
        reg("Standard",                 "Standard",                Domain.SHARED);
        reg("Condition",                "Condition",               Domain.SHARED);
        reg("ConfigurationItem",        "ConfigurationItem",       Domain.SHARED);
        reg("ImplementationConstraint", "ImplementationConstraint",Domain.SHARED);
        reg("Location",                 "Location",                Domain.SHARED);
        reg("ActualLocation",           "ActualLocation",          Domain.SHARED);

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
