package com.uaf.neo4j.plugin.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UAFElementDTOTest {

    @Test
    void builder_requiredFields_areSet() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "My Capability", "Capability").build();
        assertEquals("id-001", dto.id);
        assertEquals("My Capability", dto.name);
        assertEquals("Capability", dto.stereotype);
    }

    @Test
    void builder_optionalFields_haveCorrectDefaults() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "X", "Capability").build();
        assertEquals("", dto.qualifiedName);
        assertEquals("", dto.neo4jLabel);
        assertEquals("UNKNOWN", dto.domain);
        assertEquals("UAF", dto.language);
        assertEquals("", dto.packageName);
        assertEquals("", dto.diagramId);
        assertEquals("", dto.diagramName);
        assertEquals("", dto.documentation);
        assertEquals("", dto.modelFileName);
        assertTrue(dto.taggedValues.isEmpty());
    }

    @Test
    void builder_allOptionalFieldsSet_areStoredCorrectly() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "Cap1", "Capability")
            .qualifiedName("pkg::Cap1")
            .neo4jLabel("Capability")
            .domain("STRATEGIC")
            .packageName("Strategic Package")
            .diagramId("diag-001")
            .diagramName("Context Diagram")
            .documentation("A key capability")
            .modelFileName("MyModel.mdzip")
            .build();

        assertEquals("pkg::Cap1", dto.qualifiedName);
        assertEquals("Capability", dto.neo4jLabel);
        assertEquals("STRATEGIC", dto.domain);
        assertEquals("Strategic Package", dto.packageName);
        assertEquals("diag-001", dto.diagramId);
        assertEquals("Context Diagram", dto.diagramName);
        assertEquals("A key capability", dto.documentation);
        assertEquals("MyModel.mdzip", dto.modelFileName);
    }

    @Test
    void builder_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> UAFElementDTO.builder(null, "Name", "Stereotype"));
    }

    @Test
    void builder_nullName_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> UAFElementDTO.builder("id", null, "Stereotype"));
    }

    @Test
    void builder_nullStereotype_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> UAFElementDTO.builder("id", "Name", null));
    }

    @Test
    void taggedValue_singleValues_areStored() {
        UAFElementDTO dto = UAFElementDTO.builder("id", "Cap", "Capability")
            .taggedValue("nationality", "US")
            .taggedValue("capabilityLevel", 3)
            .build();

        assertEquals("US", dto.taggedValues.get("nationality"));
        assertEquals(3, dto.taggedValues.get("capabilityLevel"));
        assertEquals(2, dto.taggedValues.size());
    }

    @Test
    void taggedValue_nullValue_isNotStored() {
        UAFElementDTO dto = UAFElementDTO.builder("id", "Cap", "Capability")
            .taggedValue("key", null)
            .build();

        assertFalse(dto.taggedValues.containsKey("key"));
        assertTrue(dto.taggedValues.isEmpty());
    }

    @Test
    void taggedValues_bulkMap_isStoredCompletely() {
        Map<String, Object> tvs = Map.of("a", 1, "b", "two", "c", true);
        UAFElementDTO dto = UAFElementDTO.builder("id", "Cap", "Capability")
            .taggedValues(tvs)
            .build();

        assertEquals(1, dto.taggedValues.get("a"));
        assertEquals("two", dto.taggedValues.get("b"));
        assertEquals(true, dto.taggedValues.get("c"));
        assertEquals(3, dto.taggedValues.size());
    }

    @Test
    void taggedValues_nullMap_isIgnored() {
        UAFElementDTO dto = UAFElementDTO.builder("id", "Cap", "Capability")
            .taggedValues(null)
            .build();
        assertTrue(dto.taggedValues.isEmpty());
    }

    @Test
    void taggedValues_map_isImmutable() {
        UAFElementDTO dto = UAFElementDTO.builder("id", "Cap", "Capability")
            .taggedValue("key", "val")
            .build();
        assertThrows(UnsupportedOperationException.class,
            () -> dto.taggedValues.put("new", "value"));
    }

    @Test
    void builder_canBeReusedForMultipleDTOs() {
        UAFElementDTO.Builder b = UAFElementDTO.builder("id-001", "A", "Capability");
        UAFElementDTO dto1 = b.domain("STRATEGIC").build();
        UAFElementDTO dto2 = b.domain("OPERATIONAL").build();
        assertEquals("STRATEGIC", dto1.domain);
        assertEquals("OPERATIONAL", dto2.domain);
        assertEquals("id-001", dto1.id);
        assertEquals("id-001", dto2.id);
    }
}
