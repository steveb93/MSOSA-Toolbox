package com.uaf.neo4j.plugin.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UAFStereotypeRegistryTest {

    @Test
    void get_knownStrategicStereotype_returnsCorrectDomainAndLayer() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Capability");
        assertTrue(info.isPresent());
        assertEquals("Capability", info.get().neo4jLabel);
        assertEquals(UAFStereotypeRegistry.Domain.STRATEGIC, info.get().domain);
        assertEquals(UAFStereotypeRegistry.Layer.CONCEPTUAL, info.get().layer);
    }

    @Test
    void get_unknownStereotype_returnsEmpty() {
        assertFalse(UAFStereotypeRegistry.get("NotAStereotype").isPresent());
        assertFalse(UAFStereotypeRegistry.get("").isPresent());
    }

    @Test
    void get_resourceLogicalStereotype_returnsCorrectLayer() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("ResourcePerformer");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, info.get().domain);
        assertEquals(UAFStereotypeRegistry.Layer.LOGICAL, info.get().layer);
    }

    @Test
    void get_resourcePhysicalStereotype_returnsPhysicalLayer() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("HardwareElement");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.RESOURCE, info.get().domain);
        assertEquals(UAFStereotypeRegistry.Layer.PHYSICAL, info.get().layer);
    }

    @Test
    void get_acquisitionStereotype_returnsAllLayer() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Project");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.ACQUISITION, info.get().domain);
        assertEquals(UAFStereotypeRegistry.Layer.ALL, info.get().layer);
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
        assertEquals(UAFStereotypeRegistry.Layer.CONCEPTUAL, info.get().layer);
    }

    @Test
    void get_serviceStereotype_returnsLogicalLayer() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("ServicePerformer");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.SERVICE, info.get().domain);
        assertEquals(UAFStereotypeRegistry.Layer.LOGICAL, info.get().layer);
    }

    @Test
    void get_sharedStereotype_returnsAllLayer() {
        Optional<UAFStereotypeRegistry.StereotypeInfo> info = UAFStereotypeRegistry.get("Measurement");
        assertTrue(info.isPresent());
        assertEquals(UAFStereotypeRegistry.Domain.SHARED, info.get().domain);
        assertEquals(UAFStereotypeRegistry.Layer.ALL, info.get().layer);
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
