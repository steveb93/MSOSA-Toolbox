package com.uaf.neo4j.plugin.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable transfer object representing a single UAF element destined for a
 * Neo4j node.  Use UAFElementDTO.builder() to construct instances.
 */
public final class UAFElementDTO {

    // Core identity — id is the MSOSA element ID (globally unique per model)
    public final String id;
    public final String name;
    public final String qualifiedName;

    // UAF classification
    public final String stereotype;
    public final String neo4jLabel;
    public final String domain;
    public final String language;

    // Context
    public final String packageName;
    public final String diagramId;
    public final String diagramName;
    public final String documentation;

    // All UAF tagged values (property name → string value)
    public final Map<String, Object> taggedValues;

    // Provenance
    public final String modelFileName;

    private UAFElementDTO(Builder b) {
        this.id            = b.id;
        this.name          = b.name;
        this.qualifiedName = b.qualifiedName;
        this.stereotype    = b.stereotype;
        this.neo4jLabel    = b.neo4jLabel;
        this.domain        = b.domain;
        this.language      = b.language;
        this.packageName   = b.packageName;
        this.diagramId     = b.diagramId;
        this.diagramName   = b.diagramName;
        this.documentation = b.documentation;
        this.taggedValues  = Collections.unmodifiableMap(new LinkedHashMap<>(b.taggedValues));
        this.modelFileName = b.modelFileName;
    }

    public static Builder builder(String id, String name, String stereotype) {
        return new Builder(id, name, stereotype);
    }

    public static final class Builder {
        private final String id;
        private final String name;
        private final String stereotype;

        private String qualifiedName = "";
        private String neo4jLabel    = "";
        private String domain        = "UNKNOWN";
        private String language      = "UAF";
        private String packageName   = "";
        private String diagramId     = "";
        private String diagramName   = "";
        private String documentation = "";
        private String modelFileName = "";
        private final Map<String, Object> taggedValues = new LinkedHashMap<>();

        private Builder(String id, String name, String stereotype) {
            this.id         = Objects.requireNonNull(id,         "id");
            this.name       = Objects.requireNonNull(name,       "name");
            this.stereotype = Objects.requireNonNull(stereotype, "stereotype");
        }

        public Builder qualifiedName(String v) { this.qualifiedName = v; return this; }
        public Builder neo4jLabel(String v)    { this.neo4jLabel    = v; return this; }
        public Builder domain(String v)        { this.domain        = v; return this; }
        public Builder language(String v)      { this.language      = v; return this; }
        public Builder packageName(String v)   { this.packageName   = v; return this; }
        public Builder diagramId(String v)     { this.diagramId     = v; return this; }
        public Builder diagramName(String v)   { this.diagramName   = v; return this; }
        public Builder documentation(String v) { this.documentation = v; return this; }
        public Builder modelFileName(String v) { this.modelFileName = v; return this; }

        public Builder taggedValue(String key, Object value) {
            if (value != null) taggedValues.put(key, value);
            return this;
        }

        public Builder taggedValues(Map<String, Object> values) {
            if (values != null) taggedValues.putAll(values);
            return this;
        }

        public UAFElementDTO build() {
            return new UAFElementDTO(this);
        }
    }
}
