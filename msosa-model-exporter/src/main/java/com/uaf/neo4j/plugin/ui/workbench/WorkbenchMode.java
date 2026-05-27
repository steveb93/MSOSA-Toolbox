package com.uaf.neo4j.plugin.ui.workbench;

/**
 * The six panels accessible from the workbench's left rail.
 *
 * <p>{@link #roadmap} controls whether the rail item renders greyed out and the
 * panel shows the roadmap-placeholder treatment. See {@code ontology/NEXT-STEPS.md}
 * for what Federate (Stage 3) and Insights (Stage 5) will eventually contain.
 */
public enum WorkbenchMode {
    EXPORT  ("Export",   "Push the active model to Neo4j or RDF.",          false, null),
    INSPECT ("Inspect",  "Browse the exported graph — LPG or SPARQL.",      false, null),
    VALIDATE("Validate", "Run SHACL shapes over the live dataset.",         false, null),
    FEDERATE("Federate", "Reach out to external SPARQL endpoints.",         true,  "Stage 3"),
    INSIGHTS("Insights", "GDS-driven recommendations and gap analysis.",    true,  "Stage 5"),
    SETTINGS("Settings", "Connection and default-target configuration.",    false, null);

    public final String label;
    public final String tagline;
    public final boolean roadmap;
    public final String roadmapStage;

    WorkbenchMode(String label, String tagline, boolean roadmap, String roadmapStage) {
        this.label = label;
        this.tagline = tagline;
        this.roadmap = roadmap;
        this.roadmapStage = roadmapStage;
    }
}
