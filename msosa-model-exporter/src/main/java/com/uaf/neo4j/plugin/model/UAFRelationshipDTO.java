package com.uaf.neo4j.plugin.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable transfer object representing a directed UAF relationship,
 * destined for a Neo4j relationship (edge).
 */
public final class UAFRelationshipDTO {

    // Covers 35 relationship types (UAF 1.2, SysML 1.6, BPMN 2.0, plus ERD/data-flow);
    // stored as the Neo4j type string.
    public static final String REL_REALISES          = "REALISES";
    public static final String REL_TRACES_TO         = "TRACES_TO";
    public static final String REL_ASSIGNED_TO       = "ASSIGNED_TO";
    public static final String REL_SATISFIES         = "SATISFIES";
    public static final String REL_REFINES           = "REFINES";
    public static final String REL_INFLUENCES        = "INFLUENCES";
    public static final String REL_DEPENDS_ON        = "DEPENDS_ON";
    public static final String REL_COMPOSED_OF       = "COMPOSED_OF";
    public static final String REL_SPECIALISES       = "SPECIALISES";
    public static final String REL_EXHIBITS          = "EXHIBITS";
    public static final String REL_CONTRIBUTES_TO    = "CONTRIBUTES_TO";
    public static final String REL_EXPOSES           = "EXPOSES";
    public static final String REL_PROVIDES          = "PROVIDES";
    public static final String REL_PERFORMS          = "PERFORMS";
    public static final String REL_CONNECTED_TO      = "CONNECTED_TO";
    public static final String REL_FLOWS_TO          = "FLOWS_TO";
    public static final String REL_TRIGGERS          = "TRIGGERS";
    public static final String REL_PRECEDES          = "PRECEDES";
    public static final String REL_ENABLES           = "ENABLES";
    public static final String REL_SUPPORTS          = "SUPPORTS";
    public static final String REL_IMPLEMENTS        = "IMPLEMENTS";
    public static final String REL_ALLOCATED_TO      = "ALLOCATED_TO";
    public static final String REL_INSTANCE_OF       = "INSTANCE_OF";
    public static final String REL_CONTAINED_IN      = "CONTAINED_IN";
    public static final String REL_ASSOCIATED_WITH   = "ASSOCIATED_WITH";
    public static final String REL_DEPENDENCY        = "DEPENDENCY";
    public static final String REL_GENERALIZATION    = "GENERALIZATION";
    public static final String REL_INFORMATION_FLOW  = "INFORMATION_FLOW";
    public static final String REL_CONTROL_FLOW      = "CONTROL_FLOW";
    public static final String REL_SEQUENCE_FLOW     = "SEQUENCE_FLOW";
    public static final String REL_MESSAGE_FLOW      = "MESSAGE_FLOW";

    // --- ERD / data-flow (#76) -----------------------------------------------
    /** BPMN DataInputAssociation — wires a Task to a DataInput it consumes. */
    public static final String REL_DATA_INPUT        = "DATA_INPUT";
    /** BPMN DataOutputAssociation — wires a Task to a DataOutput it produces. */
    public static final String REL_DATA_OUTPUT       = "DATA_OUTPUT";
    /** Entity → Attribute. First-class ERD attribute representation (#76 design A). */
    public static final String REL_HAS_ATTRIBUTE     = "HAS_ATTRIBUTE";
    /** Attribute → DataType (PrimitiveType / Enumeration / DataType). */
    public static final String REL_OF_TYPE           = "OF_TYPE";

    public final String id;
    public final String uafType;         // UML/UAF metaclass name
    public final String neo4jType;       // Resolved Neo4j relationship type constant above
    public final String sourceId;
    public final String targetId;
    public final String name;
    public final String domain;
    public final String language;
    /** Source association-end multiplicity ("1", "0..1", "1..*", "0..*"). Empty when unset. */
    public final String srcMult;
    /** Target association-end multiplicity. Empty when unset. */
    public final String tgtMult;
    /** Source association-end role name. Empty when unset. */
    public final String srcRole;
    /** Target association-end role name. Empty when unset. */
    public final String tgtRole;
    public final Map<String, Object> taggedValues;

    private UAFRelationshipDTO(Builder b) {
        this.id           = b.id;
        this.uafType      = b.uafType;
        this.neo4jType    = b.neo4jType;
        this.sourceId     = b.sourceId;
        this.targetId     = b.targetId;
        this.name         = b.name;
        this.domain       = b.domain;
        this.language     = b.language;
        this.srcMult      = b.srcMult;
        this.tgtMult      = b.tgtMult;
        this.srcRole      = b.srcRole;
        this.tgtRole      = b.tgtRole;
        this.taggedValues = Collections.unmodifiableMap(new LinkedHashMap<>(b.taggedValues));
    }

    public static Builder builder(String id, String sourceId, String targetId, String neo4jType) {
        return new Builder(id, sourceId, targetId, neo4jType);
    }

    public static final class Builder {
        private final String id;
        private final String sourceId;
        private final String targetId;
        private final String neo4jType;

        private String uafType   = "Dependency";
        private String name      = "";
        private String domain    = "UNKNOWN";
        private String language  = "UAF";
        private String srcMult   = "";
        private String tgtMult   = "";
        private String srcRole   = "";
        private String tgtRole   = "";
        private final Map<String, Object> taggedValues = new LinkedHashMap<>();

        private Builder(String id, String sourceId, String targetId, String neo4jType) {
            this.id        = Objects.requireNonNull(id,        "id");
            this.sourceId  = Objects.requireNonNull(sourceId,  "sourceId");
            this.targetId  = Objects.requireNonNull(targetId,  "targetId");
            this.neo4jType = Objects.requireNonNull(neo4jType, "neo4jType");
        }

        public Builder uafType(String v)   { this.uafType   = v; return this; }
        public Builder name(String v)      { this.name      = v; return this; }
        public Builder domain(String v)    { this.domain    = v; return this; }
        public Builder language(String v)  { this.language  = v; return this; }
        public Builder srcMult(String v)   { this.srcMult   = v != null ? v : ""; return this; }
        public Builder tgtMult(String v)   { this.tgtMult   = v != null ? v : ""; return this; }
        public Builder srcRole(String v)   { this.srcRole   = v != null ? v : ""; return this; }
        public Builder tgtRole(String v)   { this.tgtRole   = v != null ? v : ""; return this; }

        public Builder taggedValue(String key, Object value) {
            if (value != null) taggedValues.put(key, value);
            return this;
        }

        public UAFRelationshipDTO build() {
            return new UAFRelationshipDTO(this);
        }
    }
}
