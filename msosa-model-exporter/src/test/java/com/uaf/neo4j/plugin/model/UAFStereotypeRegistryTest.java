package com.uaf.neo4j.plugin.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UAFStereotypeRegistryTest {

    @Test
    void get_knownStrategicStereotype_returnsCorrectDomainAndLabel() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Capability");
        assertTrue(info.isPresent());
        assertEquals("Capability", info.get().neo4jLabel);
        assertEquals(UAFStereotypeRegistry.Domain.STRATEGIC, info.get().domain);
        assertEquals("UAF", info.get().language);
    }

    @Test
    void get_sysmlBlock_returnsCorrectLanguageAndNullDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Block");
        assertTrue(info.isPresent());
        assertEquals("Block", info.get().neo4jLabel);
        assertNull(info.get().domain, "SysML stereotypes have no UAF domain");
        assertEquals("SysML", info.get().language);
    }

    @Test
    void get_sysmlRequirement_returnsCorrectLanguage() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Requirement");
        assertTrue(info.isPresent());
        assertEquals("SysML", info.get().language);
        assertNull(info.get().domain);
    }

    @Test
    void get_bpmnTask_returnsCorrectLanguageAndNullDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Task");
        assertTrue(info.isPresent());
        assertEquals("Task", info.get().neo4jLabel);
        assertNull(info.get().domain, "BPMN stereotypes have no UAF domain");
        assertEquals("BPMN", info.get().language);
    }

    @Test
    void get_bpmnGateways_areAllRegistered() {
        for (String name : new String[]{"ExclusiveGateway", "ParallelGateway",
                                        "InclusiveGateway", "EventBasedGateway"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals("BPMN", info.get().language, name);
        }
    }

    @Test
    void get_sysmlStructuralElements_areAllRegistered() {
        for (String name : new String[]{"Block", "Requirement", "InterfaceBlock",
                                        "ValueType", "ConstraintBlock", "FlowPort",
                                        "FullPort", "ProxyPort"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals("SysML", info.get().language, name);
        }
    }

    @Test
    void get_unknownStereotype_returnsEmpty() {
        assertFalse(UAFStereotypeRegistry.get("NotAStereotype").isPresent());
        assertFalse(UAFStereotypeRegistry.get("").isPresent());
    }

    @Test
    void get_resourceStereotype_returnsResourceDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("ResourcePerformer");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, info.get().domain);
    }

    @Test
    void get_capabilityConfiguration_returnsResourceDomain() {
        // CapabilityConfiguration is RESOURCE-domain — it extends ResourceArchitecture
        // in UAF 1.2 DMM. Pre-2026-05-29 it was registered as STRATEGIC; mis-domain
        // validation on a real profile (PR #132) confirmed the registry was wrong.
        // Migration consequence: existing exported nodes still carry domain:'STRATEGIC'
        // until re-exported or patched in Cypher.
        Optional<UAFStereotypeRegistry.StereotypeInfo> info =
            UAFStereotypeRegistry.get("CapabilityConfiguration");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, info.get().domain,
            "CapabilityConfiguration must be RESOURCE (was STRATEGIC pre-2026-05-29)");
        assertEquals("UAF", info.get().language);
        assertFalse(info.get().isFallback,
            "CapabilityConfiguration is a concrete UAF stereotype, never a bare-noun fallback");
    }

    @Test
    void get_hardwareElement_returnsResourceDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("HardwareElement");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, info.get().domain);
    }

    @Test
    void get_acquisitionStereotype_returnsAcquisitionDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Project");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.ACQUISITION, info.get().domain);
    }

    @Test
    void get_securityStereotype_returnsSecurityDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("SecurityDomain");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.SECURITY, info.get().domain);
    }

    @Test
    void get_personnelStereotype_returnsPersonnelDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Organization");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.PERSONNEL, info.get().domain);
    }

    @Test
    void get_serviceStereotype_returnsServiceDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("ServicePerformer");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.SERVICE, info.get().domain);
    }

    @Test
    void get_newResourceStereotypes_returnResourceDomain() {
        for (String name : new String[]{"System", "ResourceSystem", "Technology", "Software",
                                        "ResourceArchitecture"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, info.get().domain, name);
        }
    }

    @Test
    void get_newServiceStereotypes_returnServiceDomain() {
        for (String name : new String[]{"Service", "ServiceArchitecture"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.SERVICE, info.get().domain, name);
        }
    }

    @Test
    void get_sharedStereotype_returnsSharedDomain() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Measurement");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.SHARED, info.get().domain);
    }

    @Test
    void get_erdStereotypes_areRegisteredUnderShared() {
        // #76 design A — first-class ERD modelling
        for (String name : new String[]{"Entity", "EntityRelationship", "Attribute", "DataType"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.SHARED, info.get().domain,
                name + " should live in the SHARED domain");
            assertEquals("UAF", info.get().language,
                name + " should carry the UAF language tag");
        }
    }

    // ── Tier-1 #75 RC #6 reconciliation entries ───────────────────────────────

    @Test
    void get_tier1OperationalAdditions_returnOperationalDomain() {
        for (String name : new String[]{"OperationalAgent", "OperationalAsset",
                                        "OperationalArchitecture", "OperationalActivityAction",
                                        "OperationalInformationRole", "OperationalInterface",
                                        "OperationalMessage", "OperationalExchangeItem"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.OPERATIONAL, info.get().domain, name);
        }
    }

    @Test
    void get_tier1ResourceAdditions_returnResourceDomain() {
        for (String name : new String[]{"Resource", "ResourceExchange", "ResourceInterface",
                                        "ResourceRole", "ResourceInformationRole",
                                        "ResourceMessage", "ResourceAsset"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, info.get().domain, name);
        }
    }

    @Test
    void get_tier1StrategicAdditions_returnStrategicDomain() {
        for (String name : new String[]{"EnterpriseVision", "VisionStatement", "CapabilityRole",
                                        "PhaseableElement", "Phases"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.STRATEGIC, info.get().domain, name);
        }
    }

    @Test
    void get_tier1SecurityAdditions_returnSecurityDomain() {
        for (String name : new String[]{"SecurityEnclave", "SecurityConstraint", "SecurityControl",
                                        "SecurityRisk", "SecurityProcess"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.SECURITY, info.get().domain, name);
        }
    }

    @Test
    void get_tier1ErdKeyMarkers_areRegisteredUnderShared() {
        for (String name : new String[]{"EntityRelation", "PrimaryKey", "ForeignKey", "AlternativeKey", "FK"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals(UAFStereotypeRegistry.Domain.SHARED, info.get().domain, name);
        }
    }

    @Test
    void get_tier1BpmnExtras_areRegisteredUnderBpmnLanguage() {
        for (String name : new String[]{"BPMNProcess", "BPMNMessage", "NoneStartEvent",
                                        "NoneEndEvent", "TextAnnotation", "BusinessRuleTask"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " should be registered");
            assertEquals("BPMN", info.get().language, name);
            assertNull(info.get().domain, name + " is a BPMN stereotype, no UAF domain");
        }
    }

    @Test
    void isKnown_knownStereotypes_returnsTrue() {
        assertTrue(UAFStereotypeRegistry.isKnown("Capability"));
        assertTrue(UAFStereotypeRegistry.isKnown("Vision"));
        assertTrue(UAFStereotypeRegistry.isKnown("OperationalActivity"));
        assertTrue(UAFStereotypeRegistry.isKnown("HardwareElement"));
    }

    @Test
    void isKnown_unknownStereotypes_returnsFalse() {
        assertFalse(UAFStereotypeRegistry.isKnown(""));
        assertFalse(UAFStereotypeRegistry.isKnown("capability"));  // case-sensitive
        assertFalse(UAFStereotypeRegistry.isKnown("UnknownType"));
    }

    @Test
    void allStereotypeNames_containsRepresentativeFromEachDomain() {
        Set<String> names = UAFStereotypeRegistry.allStereotypeNames();
        assertFalse(names.isEmpty());
        // Strategic
        assertTrue(names.contains("Capability"));
        assertTrue(names.contains("Vision"));
        // Operational
        assertTrue(names.contains("OperationalPerformer"));
        assertTrue(names.contains("OperationalActivity"));
        // Resource
        assertTrue(names.contains("ResourcePerformer"));
        assertTrue(names.contains("HardwareElement"));
        // Service
        assertTrue(names.contains("ServicePerformer"));
        // Personnel
        assertTrue(names.contains("Organization"));
        // Acquisition
        assertTrue(names.contains("Project"));
        assertTrue(names.contains("Milestone"));
        // Security
        assertTrue(names.contains("SecurityDomain"));
        // Shared
        assertTrue(names.contains("Measurement"));
        assertTrue(names.contains("Location"));
    }

    @Test
    void allStereotypeNames_isUnmodifiable() {
        Set<String> names = UAFStereotypeRegistry.allStereotypeNames();
        assertThrows(UnsupportedOperationException.class, () -> names.add("NewStereo"));
    }

    // ── Fallback (bare-noun catch-all) flagging ───────────────────────────────

    @Test
    void isFallback_isTrueForBareNounCatchAlls() {
        // These six entries are the ones whose general chain is routinely walked
        // through by more specific custom stereotypes. Marking them fallback
        // makes the traverser prefer the more specific ancestor.
        for (String name : new String[]{"Resource", "Service", "System",
                                        "Software", "SystemBlock", "Technology"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " must remain registered");
            assertTrue(info.get().isFallback,
                name + " must be marked isFallback=true so the traverser only "
                + "uses it when no non-fallback ancestor exists");
        }
    }

    @Test
    void isFallback_isFalseForSpecificUAFStereotypes() {
        // Sample across domains — these are concrete, non-collision-prone names
        // and must remain non-fallback so they win against bare-noun ancestors.
        for (String name : new String[]{
                "Capability", "OperationalPerformer", "OperationalActivity",
                "OperationalRole", "ResourcePerformer", "ResourceRole",
                "ResourceArchitecture", "HardwareElement", "SoftwareElement",
                "ServicePerformer", "ServiceFunction",
                "Organization", "Post", "SecurityEnclave"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " must remain registered");
            assertFalse(info.get().isFallback,
                name + " must NOT be a fallback entry — it is a specific UAF "
                + "stereotype and should win over bare-noun ancestors");
        }
    }

    @Test
    void isFallback_isFalseForSysmlAndBpmnEntries() {
        // Fallback is a UAF-only concept (bare-noun ambiguity within UAF). SysML
        // and BPMN entries must always be treated as concrete.
        for (String name : new String[]{"Block", "Requirement", "Task",
                                        "ExclusiveGateway", "StartEvent"}) {
            Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get(name);
            assertTrue(info.isPresent(), name + " must remain registered");
            assertFalse(info.get().isFallback,
                name + " is " + info.get().language + " — must never be fallback");
        }
    }

    // ── qualifiedNameDomainHint (#125 Part 1) ─────────────────────────────────

    @Test
    void qualifiedNameDomainHint_operationalSegment_returnsOperational() {
        Optional<UAFStereotypeRegistry.Domain> hint = UAFStereotypeRegistry
            .qualifiedNameDomainHint("RootModel::Operational Taxonomy::Internal Performer::Analyst");
        assertTrue(hint.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.OPERATIONAL, hint.get());
    }

    @Test
    void qualifiedNameDomainHint_eachDomainSegment_returnsThatDomain() {
        Object[][] cases = {
            {"Root::Strategic Taxonomy::X",     UAFStereotypeRegistry.Domain.STRATEGIC},
            {"Root::Operational Connectivity::X", UAFStereotypeRegistry.Domain.OPERATIONAL},
            {"Root::Resource Structure::X",    UAFStereotypeRegistry.Domain.RESOURCE},
            {"Root::Resources Taxonomy::X",    UAFStereotypeRegistry.Domain.RESOURCE},
            {"Root::Service Processes::X",     UAFStereotypeRegistry.Domain.SERVICE},
            {"Root::Services Taxonomy::X",     UAFStereotypeRegistry.Domain.SERVICE},
            {"Root::Personnel Structure::X",   UAFStereotypeRegistry.Domain.PERSONNEL},
            {"Root::Security Constraints::X",  UAFStereotypeRegistry.Domain.SECURITY},
            {"Root::Acquisition Phasing::X",   UAFStereotypeRegistry.Domain.ACQUISITION},
            {"Root::Projects Taxonomy::X",     UAFStereotypeRegistry.Domain.ACQUISITION},
        };
        for (Object[] c : cases) {
            String qname = (String) c[0];
            UAFStereotypeRegistry.Domain expected = (UAFStereotypeRegistry.Domain) c[1];
            Optional<UAFStereotypeRegistry.Domain> hint =
                UAFStereotypeRegistry.qualifiedNameDomainHint(qname);
            assertTrue(hint.isPresent(), qname + " should hint a domain");
            assertEquals(expected, hint.get(), qname);
        }
    }

    @Test
    void qualifiedNameDomainHint_noKnownPrefix_returnsEmpty() {
        assertFalse(UAFStereotypeRegistry.qualifiedNameDomainHint(
            "RootModel::Generic Library::SomePackage::Element").isPresent());
        assertFalse(UAFStereotypeRegistry.qualifiedNameDomainHint("").isPresent());
        assertFalse(UAFStereotypeRegistry.qualifiedNameDomainHint(null).isPresent());
    }

    @Test
    void qualifiedNameDomainHint_ambiguousAcrossSegments_returnsEmpty() {
        // Path passes through two different domain folders — modeller intent is
        // unclear; refuse to hint rather than guess wrong.
        Optional<UAFStereotypeRegistry.Domain> hint = UAFStereotypeRegistry
            .qualifiedNameDomainHint("Root::Operational Taxonomy::Resource Library::Item");
        assertFalse(hint.isPresent(),
            "Conflicting domain hints across segments must yield empty");
    }

    @Test
    void qualifiedNameDomainHint_repeatedSameDomain_returnsThatDomain() {
        // Multiple segments hinting the same domain are fine — not ambiguous.
        Optional<UAFStereotypeRegistry.Domain> hint = UAFStereotypeRegistry
            .qualifiedNameDomainHint("Root::Operational Taxonomy::Operational Activities::Activity");
        assertTrue(hint.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.OPERATIONAL, hint.get());
    }

    @Test
    void qualifiedNameDomainHint_bareDomainWordOnly_returnsEmpty() {
        // Segment must be "<Domain> <more>" — bare "Operational" with no trailing
        // content is rejected because it overlaps with legitimate element names.
        assertFalse(UAFStereotypeRegistry.qualifiedNameDomainHint(
            "Root::Operational::Item").isPresent());
        assertFalse(UAFStereotypeRegistry.qualifiedNameDomainHint(
            "Root::Resource::Item").isPresent());
    }

    @Test
    void qualifiedNameDomainHint_substringWithinName_doesNotTrigger() {
        // The matcher must be segment-prefix, not raw substring. An element whose
        // own name happens to contain "Operational " in the middle must not be
        // flagged — only segment-leading domain tokens count.
        assertFalse(UAFStereotypeRegistry.qualifiedNameDomainHint(
            "Root::Library::PreOperational Phase Element").isPresent());
    }

    @Test
    void qualifiedNameDomainHint_falsePositiveOnNonTaxonomyFolder_acceptedAsKnownLimit() {
        // "Operational Layout Templates" still triggers — this is a known limit
        // of the segment-prefix heuristic and is acceptable for Phase 1 because
        // the output is observability-only, surfaced to the modeller for review.
        Optional<UAFStereotypeRegistry.Domain> hint = UAFStereotypeRegistry
            .qualifiedNameDomainHint("Root::Operational Layout Templates::Item");
        assertTrue(hint.isPresent(),
            "Segment-prefix matcher fires whenever the leading domain token is present; "
            + "the false-positive risk is documented and surfaced for modeller review");
        assertEquals(UAFStereotypeRegistry.Domain.OPERATIONAL, hint.get());
    }
}
