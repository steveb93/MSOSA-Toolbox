package com.uaf.neo4j.plugin.model;

import java.util.*;

/**
 * Maps every UAF 1.2 stereotype name (as it appears in the MSOSA profile) to
 * a Neo4j node label and UAF domain.
 *
 * Stereotype names are case-sensitive and must match the MSOSA UAF profile
 * exactly. Verify against your installation via:
 *   StereotypesHelper.getAllStereotypes(project)
 */
public class UAFStereotypeRegistry {

    public enum Domain {
        STRATEGIC, OPERATIONAL, RESOURCE, SERVICE, PERSONNEL, ACQUISITION, SECURITY, SHARED
    }

    public static final class StereotypeInfo {
        public final String neo4jLabel;
        public final Domain domain;

        StereotypeInfo(String neo4jLabel, Domain domain) {
            this.neo4jLabel = neo4jLabel;
            this.domain = domain;
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

        // --- Relationship stereotypes (used to map UAF-stereotyped UML relationships) ---
        // These entries drive the rel-type override in UAFModelTraverser.extractRelationships().
        // The neo4jLabel uppercased becomes the Neo4j relationship type.
        reg("Exhibits",                 "Exhibits",                Domain.SHARED);
        reg("Refines",                  "Refines",                 Domain.SHARED);
        reg("Satisfies",                "Satisfies",               Domain.SHARED);
        reg("Exposes",                  "Exposes",                 Domain.SHARED);
        reg("Provides",                 "Provides",                Domain.SHARED);
    }

    private static void reg(String stereotype, String label, Domain domain) {
        REGISTRY.put(stereotype, new StereotypeInfo(label, domain));
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
