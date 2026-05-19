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
}
