package com.uaf.neo4j.plugin.neo4j;

import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds parameterised Cypher statements for idempotent MERGE writes.
 *
 * Design decisions:
 * - Each UAF element gets dual labels: UAFElement + its stereotype label,
 *   so queries can hit either the generic (:UAFElement) or specific (:Capability).
 * - All properties are passed as parameters (never string-interpolated) to
 *   prevent Cypher injection.
 * - INSTANCE_OF links point to existing :Stereotype nodes in the UAF
 *   domain metamodel graph (assumed present from init_uaf_graph.cypher).
 */
public class Neo4jCypherBuilder {

    private Neo4jCypherBuilder() {}

    // -------------------------------------------------------------------------
    // Nodes

    /**
     * Returns a Cypher string that MERGEs on id and SETs all properties.
     * Label is injected by string formatting — safe because it comes from
     * UAFStereotypeRegistry (not user input).
     */
    public static String nodeMergeCypher(UAFElementDTO dto) {
        String label = sanitiseLabel(dto.neo4jLabel);
        return String.format(
            "MERGE (n:UAFElement:%s {id: $id})\n" +
            "SET n += $props\n" +
            "SET n.stereotype = $stereotype\n" +
            "SET n.domain     = $domain\n" +
            "SET n.layer      = $layer",
            label);
    }

    public static Map<String, Object> nodeParams(UAFElementDTO dto) {
        Map<String, Object> props = new HashMap<>();
        props.put("name",          dto.name);
        props.put("qualifiedName", dto.qualifiedName);
        props.put("packageName",   dto.packageName);
        props.put("diagramId",     dto.diagramId);
        props.put("diagramName",   dto.diagramName);
        props.put("documentation", dto.documentation);
        props.put("modelFile",     dto.modelFileName);

        // Flatten tagged values into top-level properties (prefixed tv_)
        for (Map.Entry<String, Object> tv : dto.taggedValues.entrySet()) {
            String key = "tv_" + tv.getKey().replaceAll("[^a-zA-Z0-9_]", "_");
            props.put(key, tv.getValue());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("id",         dto.id);
        params.put("props",      props);
        params.put("stereotype", dto.stereotype);
        params.put("domain",     dto.domain);
        params.put("layer",      dto.layer);
        return params;
    }

    // -------------------------------------------------------------------------
    // Relationships

    /**
     * Relationship type is injected by string format — safe because it comes
     * from UAFRelationshipDTO constants (not user input).
     */
    public static String relationshipMergeCypher(UAFRelationshipDTO dto) {
        String type = sanitiseRelType(dto.neo4jType);
        return String.format(
            "MATCH (src:UAFElement {id: $srcId})\n" +
            "MATCH (tgt:UAFElement {id: $tgtId})\n" +
            "MERGE (src)-[r:%s {id: $id}]->(tgt)\n" +
            "SET r.uafType = $uafType\n" +
            "SET r.name    = $name\n" +
            "SET r.domain  = $domain",
            type);
    }

    public static Map<String, Object> relationshipParams(UAFRelationshipDTO dto) {
        Map<String, Object> p = new HashMap<>();
        p.put("id",      dto.id);
        p.put("srcId",   dto.sourceId);
        p.put("tgtId",   dto.targetId);
        p.put("uafType", dto.uafType);
        p.put("name",    dto.name);
        p.put("domain",  dto.domain);
        return p;
    }

    // -------------------------------------------------------------------------
    // SystemModel node + DEFINES provenance link

    public static final String SYSTEM_MODEL_MERGE_CYPHER =
        "MERGE (m:SystemModel {id: $id})\n" +
        "SET m.name = $name";

    public static Map<String, Object> systemModelParams(String id, String name) {
        Map<String, Object> p = new HashMap<>();
        p.put("id",   id);
        p.put("name", name);
        return p;
    }

    /** MERGEs a :DEFINES relationship from the SystemModel to a UAFElement. */
    public static final String DEFINES_CYPHER =
        "MATCH (m:SystemModel {id: $modelId})\n" +
        "MATCH (n:UAFElement {id: $elementId})\n" +
        "MERGE (m)-[:DEFINES]->(n)";

    public static Map<String, Object> definesParams(String modelId, String elementId) {
        Map<String, Object> p = new HashMap<>();
        p.put("modelId",   modelId);
        p.put("elementId", elementId);
        return p;
    }

    // -------------------------------------------------------------------------
    // INSTANCE_OF links to existing metamodel :Stereotype nodes

    public static final String INSTANCE_OF_CYPHER =
        "MATCH (n:UAFElement {id: $elementId})\n" +
        "MATCH (s:Stereotype {name: $stereotypeName})\n" +
        "MERGE (n)-[:INSTANCE_OF]->(s)";

    public static Map<String, Object> instanceOfParams(UAFElementDTO dto) {
        Map<String, Object> p = new HashMap<>();
        p.put("elementId",     dto.id);
        p.put("stereotypeName", dto.stereotype);
        return p;
    }

    // -------------------------------------------------------------------------

    private static String sanitiseLabel(String label) {
        // Only allow alphanumeric + underscore (Neo4j label rules)
        return label.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String sanitiseRelType(String type) {
        return type.replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
    }
}
