package com.uaf.neo4j.plugin.neo4j;

import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Neo4jCypherBuilderTest {

    private static UAFElementDTO element(String id, String name, String label) {
        return UAFElementDTO.builder(id, name, label)
            .neo4jLabel(label)
            .domain("STRATEGIC")
            .build();
    }

    // -------------------------------------------------------------------------
    // nodeMergeCypher

    @Test
    void nodeMergeCypher_usesStereotypeLabelOnly() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "Cap A", "Capability"));
        assertTrue(cypher.contains(":Capability"), "Expected stereotype label");
        assertFalse(cypher.contains(":UAFElement"), "Must not include generic UAFElement label");
    }

    @Test
    void nodeMergeCypher_usesMergeOnIdParam() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "Cap A", "Capability"));
        assertTrue(cypher.contains("MERGE"), "Should use MERGE for idempotency");
        assertTrue(cypher.contains("{id: $id}"), "Should MERGE on parameterised id");
        assertTrue(cypher.contains("$props"), "Properties should be parameterised");
    }

    @Test
    void nodeMergeCypher_setsStereotypeAndDomain() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "Cap A", "Capability"));
        assertTrue(cypher.contains("$stereotype"));
        assertTrue(cypher.contains("$domain"));
        assertTrue(cypher.contains("$language"));
        assertFalse(cypher.contains("$layer"), "Layer must not appear in Cypher");
    }

    @Test
    void nodeMergeCypher_sanitisesLabelSpecialChars() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "X", "My Label!"));
        assertTrue(cypher.contains(":My_Label_"), "Special chars in label should become underscores");
        assertFalse(cypher.contains("!"), "Exclamation mark must be removed");
        assertFalse(cypher.contains("My Label"), "Spaces in label must be removed");
    }

    @Test
    void nodeMergeCypher_sanitisesLabelWithDashes() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "X", "some-label"));
        assertTrue(cypher.contains(":some_label"), "Dashes in label should become underscores");
    }

    // -------------------------------------------------------------------------
    // nodeParams

    @Test
    void nodeParams_containsAllTopLevelKeys() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "Cap A", "Capability")
            .neo4jLabel("Capability").domain("STRATEGIC").build();
        Map<String, Object> params = Neo4jCypherBuilder.nodeParams(dto);

        assertEquals("id-001", params.get("id"));
        assertEquals("STRATEGIC", params.get("domain"));
        assertEquals("UAF", params.get("language"));
        assertNull(params.get("layer"), "Layer must not be a top-level param");
        assertEquals("Capability", params.get("stereotype"));
        assertNotNull(params.get("props"), "props map should be present");
    }

    @Test
    void nodeParams_propsMapContainsCoreProperties() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "Cap A", "Capability")
            .neo4jLabel("Capability").domain("STRATEGIC")
            .qualifiedName("pkg::Cap A")
            .packageName("Strategic Package")
            .diagramId("diag-001")
            .diagramName("Context Diagram")
            .documentation("Some docs")
            .modelFileName("My.mdzip")
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) Neo4jCypherBuilder.nodeParams(dto).get("props");

        assertEquals("Cap A", props.get("name"));
        assertEquals("pkg::Cap A", props.get("qualifiedName"));
        assertEquals("Strategic Package", props.get("packageName"));
        assertEquals("diag-001", props.get("diagramId"));
        assertEquals("Context Diagram", props.get("diagramName"));
        assertEquals("Some docs", props.get("documentation"));
        assertEquals("My.mdzip", props.get("modelFile"));
    }

    @Test
    void nodeParams_taggedValues_prefixedWithTv() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "Cap A", "Capability")
            .neo4jLabel("Capability").domain("STRATEGIC")
            .taggedValue("nationality", "US")
            .taggedValue("capabilityLevel", 3)
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) Neo4jCypherBuilder.nodeParams(dto).get("props");

        assertEquals("US", props.get("tv_nationality"));
        assertEquals(3, props.get("tv_capabilityLevel"));
    }

    @Test
    void nodeParams_taggedValueSpecialChars_replacedWithUnderscore() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "X", "Capability")
            .neo4jLabel("Capability").domain("STRATEGIC")
            .taggedValue("tag-name.here", "val")
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) Neo4jCypherBuilder.nodeParams(dto).get("props");

        assertTrue(props.containsKey("tv_tag_name_here"),
            "Hyphens and dots in tag names should become underscores");
    }

    @Test
    void nodeParams_excludesTaggedValues_whenFlagFalse() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "Cap A", "Capability")
            .neo4jLabel("Capability").domain("STRATEGIC")
            .taggedValue("nationality", "US")
            .taggedValue("level", 3)
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) Neo4jCypherBuilder.nodeParams(dto, false).get("props");

        assertFalse(props.containsKey("tv_nationality"), "tv_ keys must be absent when includeTaggedValues=false");
        assertFalse(props.containsKey("tv_level"),       "tv_ keys must be absent when includeTaggedValues=false");
        assertTrue(props.containsKey("name"),            "core props must still be present when tagged values excluded");
    }

    // -------------------------------------------------------------------------
    // relationshipMergeCypher

    @Test
    void relationshipMergeCypher_matchesBothEndpointsById() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-001", "src-001", "tgt-001", UAFRelationshipDTO.REL_REALISES).build();
        String cypher = Neo4jCypherBuilder.relationshipMergeCypher(dto);

        assertTrue(cypher.contains("MATCH (src {id: $srcId})"),
            "Source must be matched by id only — no label filter");
        assertTrue(cypher.contains("MATCH (tgt {id: $tgtId})"),
            "Target must be matched by id only — no label filter");
        assertFalse(cypher.contains(":UAFElement"), "Must not reference removed UAFElement label");
    }

    @Test
    void relationshipMergeCypher_mergesRelationshipType() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-001", "src-001", "tgt-001", UAFRelationshipDTO.REL_REALISES).build();
        String cypher = Neo4jCypherBuilder.relationshipMergeCypher(dto);

        assertTrue(cypher.contains("MERGE (src)-[r:REALISES {id: $id}]->(tgt)"));
    }

    @Test
    void relationshipMergeCypher_setsMetadataParams() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-001", "src-001", "tgt-001", UAFRelationshipDTO.REL_PERFORMS).build();
        String cypher = Neo4jCypherBuilder.relationshipMergeCypher(dto);

        assertTrue(cypher.contains("$uafType"));
        assertTrue(cypher.contains("$name"));
        assertTrue(cypher.contains("$domain"));
        assertTrue(cypher.contains("$language"));
    }

    @Test
    void relationshipMergeCypher_sanitisesRelTypeSpecialChars() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-001", "src-001", "tgt-001", "my-rel type!").build();
        String cypher = Neo4jCypherBuilder.relationshipMergeCypher(dto);

        assertTrue(cypher.contains(":MY_REL_TYPE_"),
            "Special chars should be replaced with _ and type uppercased");
    }

    @Test
    void relationshipMergeCypher_lowercaseType_isUppercased() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-001", "src-001", "tgt-001", "performs").build();
        String cypher = Neo4jCypherBuilder.relationshipMergeCypher(dto);

        assertTrue(cypher.contains(":PERFORMS"), "Rel type should be uppercased in Cypher");
    }

    // -------------------------------------------------------------------------
    // relationshipParams

    @Test
    void relationshipParams_containsAllRequiredKeys() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-001", "src-001", "tgt-001", UAFRelationshipDTO.REL_PERFORMS)
            .uafType("Realisation")
            .name("performs link")
            .domain("OPERATIONAL")
            .build();
        Map<String, Object> p = Neo4jCypherBuilder.relationshipParams(dto);

        assertEquals("r-001", p.get("id"));
        assertEquals("src-001", p.get("srcId"));
        assertEquals("tgt-001", p.get("tgtId"));
        assertEquals("Realisation", p.get("uafType"));
        assertEquals("performs link", p.get("name"));
        assertEquals("OPERATIONAL", p.get("domain"));
        assertEquals("UAF", p.get("language"));
    }

    @Test
    void relationshipMergeCypher_setsMultiplicityAndRoleProperties() {
        // #76: Cypher edges carry srcMult/tgtMult/srcRole/tgtRole so ERD multiplicity
        // is queryable. RDF side stays plain triples for now (separate follow-up).
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-002", "src-002", "tgt-002", UAFRelationshipDTO.REL_ASSOCIATED_WITH)
            .build();
        String cypher = Neo4jCypherBuilder.relationshipMergeCypher(dto);

        assertTrue(cypher.contains("$srcMult"), "Cypher should SET $srcMult");
        assertTrue(cypher.contains("$tgtMult"), "Cypher should SET $tgtMult");
        assertTrue(cypher.contains("$srcRole"), "Cypher should SET $srcRole");
        assertTrue(cypher.contains("$tgtRole"), "Cypher should SET $tgtRole");
    }

    @Test
    void relationshipParams_containsMultiplicityAndRole() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-003", "src-003", "tgt-003", UAFRelationshipDTO.REL_ASSOCIATED_WITH)
            .srcMult("1")
            .tgtMult("0..*")
            .srcRole("owner")
            .tgtRole("members")
            .build();
        Map<String, Object> p = Neo4jCypherBuilder.relationshipParams(dto);

        assertEquals("1",       p.get("srcMult"));
        assertEquals("0..*",    p.get("tgtMult"));
        assertEquals("owner",   p.get("srcRole"));
        assertEquals("members", p.get("tgtRole"));
    }

    @Test
    void relationshipParams_defaultsMultiplicityAndRoleToEmptyString() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-004", "src-004", "tgt-004", UAFRelationshipDTO.REL_REALISES).build();
        Map<String, Object> p = Neo4jCypherBuilder.relationshipParams(dto);

        assertEquals("", p.get("srcMult"));
        assertEquals("", p.get("tgtMult"));
        assertEquals("", p.get("srcRole"));
        assertEquals("", p.get("tgtRole"));
    }

    // -------------------------------------------------------------------------
    // SystemModel + DEFINES

    @Test
    void systemModelMergeCypher_mergesOnId() {
        assertTrue(Neo4jCypherBuilder.SYSTEM_MODEL_MERGE_CYPHER.contains("MERGE"));
        assertTrue(Neo4jCypherBuilder.SYSTEM_MODEL_MERGE_CYPHER.contains(":SystemModel"));
        assertTrue(Neo4jCypherBuilder.SYSTEM_MODEL_MERGE_CYPHER.contains("{id: $id}"));
        assertTrue(Neo4jCypherBuilder.SYSTEM_MODEL_MERGE_CYPHER.contains("$name"));
    }

    @Test
    void systemModelParams_containsIdAndName() {
        Map<String, Object> p = Neo4jCypherBuilder.systemModelParams("model-001", "MyProject");
        assertEquals("model-001", p.get("id"));
        assertEquals("MyProject", p.get("name"));
    }

    @Test
    void definesCypher_matchesModelAndElementById() {
        assertTrue(Neo4jCypherBuilder.DEFINES_CYPHER.contains(":SystemModel"));
        assertFalse(Neo4jCypherBuilder.DEFINES_CYPHER.contains(":UAFElement"),
            "DEFINES must not use removed UAFElement label");
        assertTrue(Neo4jCypherBuilder.DEFINES_CYPHER.contains("MERGE"));
        assertTrue(Neo4jCypherBuilder.DEFINES_CYPHER.contains(":DEFINES"));
        assertTrue(Neo4jCypherBuilder.DEFINES_CYPHER.contains("$modelId"));
        assertTrue(Neo4jCypherBuilder.DEFINES_CYPHER.contains("$elementId"));
    }

    @Test
    void definesParams_containsModelIdAndElementId() {
        Map<String, Object> p = Neo4jCypherBuilder.definesParams("model-001", "elem-001");
        assertEquals("model-001", p.get("modelId"));
        assertEquals("elem-001", p.get("elementId"));
    }

    // -------------------------------------------------------------------------
    // instanceOfParams / INSTANCE_OF_CYPHER

    @Test
    void instanceOfParams_containsElementIdAndStereotypeName() {
        UAFElementDTO dto = element("elem-001", "Cap A", "Capability");
        Map<String, Object> p = Neo4jCypherBuilder.instanceOfParams(dto);

        assertEquals("elem-001", p.get("elementId"));
        assertEquals("Capability", p.get("stereotypeName"));
    }

    @Test
    void instanceOfCypher_constant_isWellFormed() {
        String cypher = Neo4jCypherBuilder.INSTANCE_OF_CYPHER;
        assertNotNull(cypher);
        assertTrue(cypher.contains("INSTANCE_OF"));
        assertTrue(cypher.contains("$elementId"));
        assertTrue(cypher.contains("$stereotypeName"));
        assertFalse(cypher.contains(":UAFElement"),
            "INSTANCE_OF must not use removed UAFElement label");
        assertTrue(cypher.contains(":Stereotype"));
    }
}
