package com.uaf.neo4j.plugin.model;

import java.util.*;

/**
 * Maps every UAF 1.2 stereotype name (as it appears in the MSOSA profile) to
 * a Neo4j node label, UAF domain, and architecture layer.
 *
 * Stereotype names are case-sensitive and must match the MSOSA UAF profile
 * exactly. Verify against your installation via:
 *   StereotypesHelper.getAllStereotypes(project)
 */
public class UAFStereotypeRegistry {

    public enum Domain {
        STRATEGIC, OPERATIONAL, RESOURCE, SERVICE, PERSONNEL, ACQUISITION, SECURITY, SHARED
    }

    public enum Layer {
        CONCEPTUAL, LOGICAL, PHYSICAL, ALL
    }

    public static final class StereotypeInfo {
        public final String neo4jLabel;
        public final Domain domain;
        public final Layer layer;

        StereotypeInfo(String neo4jLabel, Domain domain, Layer layer) {
            this.neo4jLabel = neo4jLabel;
            this.domain = domain;
            this.layer = layer;
        }
    }

    private static final Map<String, StereotypeInfo> REGISTRY = new LinkedHashMap<>();

    static {
        // --- Strategic View (StV) ---
        reg("Capability",               "Capability",              Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("CapabilityConfiguration",  "CapabilityConfiguration", Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("CapabilityComposition",    "CapabilityComposition",   Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("CapabilityDependency",     "CapabilityDependency",    Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("CapabilitySpecialization", "CapabilitySpecialization",Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("Vision",                   "Vision",                  Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("EndState",                 "EndState",                Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("DesiredEffect",            "DesiredEffect",           Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("EnterprisePhase",          "EnterprisePhase",         Domain.STRATEGIC,    Layer.CONCEPTUAL);
        reg("CapabilityIncrement",      "CapabilityIncrement",     Domain.STRATEGIC,    Layer.CONCEPTUAL);

        // --- Operational View (OV) ---
        reg("OperationalPerformer",     "OperationalPerformer",    Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalActivity",      "OperationalActivity",     Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalExchange",      "OperationalExchange",     Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalCapability",    "OperationalCapability",   Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalConnector",     "OperationalConnector",    Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalDomain",        "OperationalDomain",       Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalProcess",       "OperationalProcess",      Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalFunction",      "OperationalFunction",     Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalInteraction",   "OperationalInteraction",  Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("NeedLine",                 "NeedLine",                Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("PerformerPort",            "PerformerPort",           Domain.OPERATIONAL,  Layer.CONCEPTUAL);
        reg("OperationalRole",          "OperationalRole",         Domain.OPERATIONAL,  Layer.CONCEPTUAL);

        // --- Resource View (RsV) --- (Systems in older UAF versions)
        reg("ResourcePerformer",        "ResourcePerformer",       Domain.RESOURCE,     Layer.LOGICAL);
        reg("ResourceFunction",         "ResourceFunction",        Domain.RESOURCE,     Layer.LOGICAL);
        reg("ResourceInteraction",      "ResourceInteraction",     Domain.RESOURCE,     Layer.LOGICAL);
        reg("ResourceArtifact",         "ResourceArtifact",        Domain.RESOURCE,     Layer.LOGICAL);
        reg("ResourcePort",             "ResourcePort",            Domain.RESOURCE,     Layer.LOGICAL);
        reg("ResourceConnector",        "ResourceConnector",       Domain.RESOURCE,     Layer.LOGICAL);
        reg("HardwareElement",          "HardwareElement",         Domain.RESOURCE,     Layer.PHYSICAL);
        reg("SoftwareElement",          "SoftwareElement",         Domain.RESOURCE,     Layer.PHYSICAL);
        reg("NaturalResource",          "NaturalResource",         Domain.RESOURCE,     Layer.PHYSICAL);
        reg("SystemBlock",              "SystemBlock",             Domain.RESOURCE,     Layer.LOGICAL);
        reg("ActualSystem",             "ActualSystem",            Domain.RESOURCE,     Layer.PHYSICAL);
        reg("LogicalArchitecture",      "LogicalArchitecture",     Domain.RESOURCE,     Layer.LOGICAL);
        reg("PhysicalArchitecture",     "PhysicalArchitecture",    Domain.RESOURCE,     Layer.PHYSICAL);

        // --- Service View (SvcV) ---
        reg("ServicePerformer",         "ServicePerformer",        Domain.SERVICE,      Layer.LOGICAL);
        reg("ServiceFunction",          "ServiceFunction",         Domain.SERVICE,      Layer.LOGICAL);
        reg("ServiceSpecification",     "ServiceSpecification",    Domain.SERVICE,      Layer.LOGICAL);
        reg("ServiceInterface",         "ServiceInterface",        Domain.SERVICE,      Layer.LOGICAL);
        reg("ServicePoint",             "ServicePoint",            Domain.SERVICE,      Layer.LOGICAL);
        reg("ServiceConnector",         "ServiceConnector",        Domain.SERVICE,      Layer.LOGICAL);
        reg("ServiceExchange",          "ServiceExchange",         Domain.SERVICE,      Layer.LOGICAL);

        // --- Personnel View (PrV) ---
        reg("Organization",             "Organization",            Domain.PERSONNEL,    Layer.CONCEPTUAL);
        reg("OrganizationalResource",   "OrganizationalResource",  Domain.PERSONNEL,    Layer.LOGICAL);
        reg("Post",                     "Post",                    Domain.PERSONNEL,    Layer.LOGICAL);
        reg("PersonnelActivity",        "PersonnelActivity",       Domain.PERSONNEL,    Layer.CONCEPTUAL);
        reg("ActualOrganization",       "ActualOrganization",      Domain.PERSONNEL,    Layer.PHYSICAL);
        reg("OrganizationalCapability", "OrganizationalCapability",Domain.PERSONNEL,    Layer.CONCEPTUAL);

        // --- Acquisition View (AcV) ---
        reg("Project",                  "Project",                 Domain.ACQUISITION,  Layer.ALL);
        reg("Milestone",                "Milestone",               Domain.ACQUISITION,  Layer.ALL);
        reg("ProjectMilestone",         "ProjectMilestone",        Domain.ACQUISITION,  Layer.ALL);
        reg("ProjectBoundary",          "ProjectBoundary",         Domain.ACQUISITION,  Layer.ALL);
        reg("FundingRequest",           "FundingRequest",          Domain.ACQUISITION,  Layer.ALL);

        // --- Security View (SrV) ---
        reg("SecurityDomain",           "SecurityDomain",          Domain.SECURITY,     Layer.ALL);
        reg("SecurityAsset",            "SecurityAsset",           Domain.SECURITY,     Layer.ALL);
        reg("SecurityPolicy",           "SecurityPolicy",          Domain.SECURITY,     Layer.ALL);

        // --- Shared / Cross-cutting ---
        reg("Measurement",              "Measurement",             Domain.SHARED,       Layer.ALL);
        reg("Standard",                 "Standard",                Domain.SHARED,       Layer.ALL);
        reg("Condition",                "Condition",               Domain.SHARED,       Layer.ALL);
        reg("ConfigurationItem",        "ConfigurationItem",       Domain.SHARED,       Layer.ALL);
        reg("ImplementationConstraint", "ImplementationConstraint",Domain.SHARED,       Layer.ALL);
        reg("Location",                 "Location",                Domain.SHARED,       Layer.ALL);
        reg("ActualLocation",           "ActualLocation",          Domain.SHARED,       Layer.PHYSICAL);
    }

    private static void reg(String stereotype, String label, Domain domain, Layer layer) {
        REGISTRY.put(stereotype, new StereotypeInfo(label, domain, layer));
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
}
