package com.uaf.neo4j.plugin.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UAFRelationshipDTOTest {

    @Test
    void builder_requiredFields_areSet() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("rel-001", "src-001", "tgt-001", UAFRelationshipDTO.REL_REALISES)
            .build();

        assertEquals("rel-001", dto.id);
        assertEquals("src-001", dto.sourceId);
        assertEquals("tgt-001", dto.targetId);
        assertEquals("REALISES", dto.neo4jType);
    }

    @Test
    void builder_optionalFields_haveCorrectDefaults() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r1", "s1", "t1", UAFRelationshipDTO.REL_DEPENDS_ON)
            .build();

        assertEquals("Dependency", dto.uafType);
        assertEquals("", dto.name);
        assertEquals("UNKNOWN", dto.domain);
        assertTrue(dto.taggedValues.isEmpty());
    }

    @Test
    void builder_allOptionalFieldsSet_areStoredCorrectly() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r1", "s1", "t1", UAFRelationshipDTO.REL_PERFORMS)
            .uafType("Realisation")
            .name("Performs link")
            .domain("OPERATIONAL")
            .taggedValue("weight", 5)
            .build();

        assertEquals("Realisation", dto.uafType);
        assertEquals("Performs link", dto.name);
        assertEquals("OPERATIONAL", dto.domain);
        assertEquals(5, dto.taggedValues.get("weight"));
    }

    @Test
    void builder_nullId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> UAFRelationshipDTO.builder(null, "s", "t", "TYPE"));
    }

    @Test
    void builder_nullSourceId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> UAFRelationshipDTO.builder("id", null, "t", "TYPE"));
    }

    @Test
    void builder_nullTargetId_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> UAFRelationshipDTO.builder("id", "s", null, "TYPE"));
    }

    @Test
    void builder_nullNeo4jType_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> UAFRelationshipDTO.builder("id", "s", "t", null));
    }

    @Test
    void taggedValue_nullValue_isNotStored() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r1", "s1", "t1", UAFRelationshipDTO.REL_TRACES_TO)
            .taggedValue("key", null)
            .build();
        assertFalse(dto.taggedValues.containsKey("key"));
    }

    @Test
    void taggedValues_areImmutable() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r1", "s1", "t1", UAFRelationshipDTO.REL_TRACES_TO)
            .taggedValue("k", "v")
            .build();
        assertThrows(UnsupportedOperationException.class,
            () -> dto.taggedValues.put("x", "y"));
    }

    @Test
    void allRelationshipTypeConstants_arePresentAndNonNull() {
        assertNotNull(UAFRelationshipDTO.REL_REALISES);
        assertNotNull(UAFRelationshipDTO.REL_TRACES_TO);
        assertNotNull(UAFRelationshipDTO.REL_ASSIGNED_TO);
        assertNotNull(UAFRelationshipDTO.REL_SATISFIES);
        assertNotNull(UAFRelationshipDTO.REL_REFINES);
        assertNotNull(UAFRelationshipDTO.REL_INFLUENCES);
        assertNotNull(UAFRelationshipDTO.REL_DEPENDS_ON);
        assertNotNull(UAFRelationshipDTO.REL_COMPOSED_OF);
        assertNotNull(UAFRelationshipDTO.REL_SPECIALISES);
        assertNotNull(UAFRelationshipDTO.REL_EXHIBITS);
        assertNotNull(UAFRelationshipDTO.REL_CONTRIBUTES_TO);
        assertNotNull(UAFRelationshipDTO.REL_EXPOSES);
        assertNotNull(UAFRelationshipDTO.REL_PROVIDES);
        assertNotNull(UAFRelationshipDTO.REL_PERFORMS);
        assertNotNull(UAFRelationshipDTO.REL_CONNECTED_TO);
        assertNotNull(UAFRelationshipDTO.REL_FLOWS_TO);
        assertNotNull(UAFRelationshipDTO.REL_TRIGGERS);
        assertNotNull(UAFRelationshipDTO.REL_PRECEDES);
        assertNotNull(UAFRelationshipDTO.REL_ENABLES);
        assertNotNull(UAFRelationshipDTO.REL_SUPPORTS);
        assertNotNull(UAFRelationshipDTO.REL_IMPLEMENTS);
        assertNotNull(UAFRelationshipDTO.REL_ALLOCATED_TO);
        assertNotNull(UAFRelationshipDTO.REL_INSTANCE_OF);
        assertNotNull(UAFRelationshipDTO.REL_CONTAINED_IN);
        assertNotNull(UAFRelationshipDTO.REL_ASSOCIATED_WITH);
        assertNotNull(UAFRelationshipDTO.REL_DEPENDENCY);
        assertNotNull(UAFRelationshipDTO.REL_GENERALIZATION);
        assertNotNull(UAFRelationshipDTO.REL_INFORMATION_FLOW);
        assertNotNull(UAFRelationshipDTO.REL_CONTROL_FLOW);
    }

    @Test
    void relationshipTypeConstants_areUppercaseStrings() {
        assertEquals("REALISES", UAFRelationshipDTO.REL_REALISES);
        assertEquals("PERFORMS", UAFRelationshipDTO.REL_PERFORMS);
        assertEquals("INSTANCE_OF", UAFRelationshipDTO.REL_INSTANCE_OF);
        assertEquals("CONTROL_FLOW", UAFRelationshipDTO.REL_CONTROL_FLOW);
        assertEquals("INFORMATION_FLOW", UAFRelationshipDTO.REL_INFORMATION_FLOW);
    }
}
