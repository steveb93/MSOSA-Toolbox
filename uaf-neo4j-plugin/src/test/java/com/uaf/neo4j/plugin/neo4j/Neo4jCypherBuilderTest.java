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
            .layer("CONCEPTUAL")
            .build();
    }

    // -------------------------------------------------------------------------
    // nodeMergeCypher

    @Test
    void nodeMergeCypher_containsDualLabels() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "Cap A", "Capability"));
        assertTrue(cypher.contains(":UAFElement:Capability"), "Expected dual labels UAFElement + Capability");
    }

    @Test
    void nodeMergeCypher_usesMergeOnIdParam() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "Cap A", "Capability"));
        assertTrue(cypher.contains("MERGE"), "Should use MERGE for idempotency");
        assertTrue(cypher.contains("{id: $id}"), "Should MERGE on parameterised id");
        assertTrue(cypher.contains("$props"), "Properties should be parameterised");
    }

    @Test
    void nodeMergeCypher_setsStereotypeDomainLayer() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "Cap A", "Capability"));
        assertTrue(cypher.contains("$stereotype"));
        assertTrue(cypher.contains("$domain"));
        assertTrue(cypher.contains("$layer"));
    }

    @Test
    void nodeMergeCypher_sanitisesLabelSpecialChars() {
        String cypher = Neo4jCypherBuilder.nodeMergeCypher(element("id-001", "X", "My Label!"));
        assertTrue(cypher.contains(":UAFElement:My_Label_"), "Special chars in label should become underscores");
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
            .neo4jLabel("Capability").domain("STRATEGIC").layer("CONCEPTUAL").build();
        Map<String, Object> params = Neo4jCypherBuilder.nodeParams(dto);

        assertEquals("id-001", params.get("id"));
        assertEquals("STRATEGIC", params.get("domain"));
        assertEquals("CONCEPTUAL", params.get("layer"));
        assertEquals("Capability", params.get("stereotype"));
        assertNotNull(params.get("props"), "props map should be present");
    }

    @Test
    void nodeParams_propsMapContainsCoreProperties() {
        UAFElementDTO dto = UAFElementDTO.builder("id-001", "Cap A", "Capability")
            .neo4jLabel("Capability").domain("STRATEGIC").layer("CONCEPTUAL")
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
            .neo4jLabel("Capability").domain("STRATEGIC").layer("CONCEPTUAL")
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
            .neo4jLabel("Capability").domain("STRATEGIC").layer("CONCEPTUAL")
            .taggedValue("tag-name.here", "val")
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) Neo4jCypherBuilder.nodeParams(dto).get("props");

        assertTrue(props.containsKey("tv_tag_name_here"),
            "Hyphens and dots in tag names should become underscores");
    }

    // -------------------------------------------------------------------------
    // relationshipMergeCypher

    @Test
    void relationshipMergeCypher_matchesBothEndpoints() {
        UAFRelationshipDTO dto = UAFRelationshipDTO
            .builder("r-001", "src-001", "tgt-001", UAFRelationshipDTO.REL_REALISES).build();
        String cypher = Neo4jCypherBuilder.relationshipMergeCypher(dto);

        assertTrue(cypher.contains("MATCH (src:UAFElement {id: $srcId})"));
        assertTrue(cypher.contains("MATCH (tgt:UAFElement {id: $tgtId})"));
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
        assertTrue(cypher.contains(":UAFElement"));
        assertTrue(cypher.contains(":Stereotype"));
    }
}
