package com.uaf.neo4j.plugin.ui.workbench;

import javax.swing.JComponent;

import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.List;

/**
 * Insights mode — Stage 5 roadmap placeholder. Composite-AI features — the
 * graph + ML + reasoning + LLM combination from {@code Ontology-Approach-to-Knowledge.md}
 * — land here. Neo4j Graph Data Science (GDS) is already in the Docker image,
 * so the algorithm side is the first thing that gets wired.
 */
final class InsightsModePanel extends javax.swing.JPanel implements WorkbenchPanel {

    InsightsModePanel() {
        super(new BorderLayout());
        List<RoadmapPanel.RoadmapItem> items = Arrays.asList(
            new RoadmapPanel.RoadmapItem("PageRank — critical Operational Activities",
                "Surface the activities the architecture leans on hardest. GDS-driven; results written back "
              + "as <code>uafprop:pagerank</code> so SPARQL queries can sort by relevance."),
            new RoadmapPanel.RoadmapItem("Capability-gap recommender",
                "Suggest ResourceArtifacts that could fill detected capability gaps, using graph-position "
              + "features. Targets the Decision-makers persona in the ontology approach doc §8."),
            new RoadmapPanel.RoadmapItem("Community detection",
                "Identify clusters of strongly-coupled performers / activities — basis for proposing "
              + "logical groupings that the modeller may have missed."),
            new RoadmapPanel.RoadmapItem("Decision-intelligence dashboard",
                "External Streamlit/Dash/Grafana surface consuming both SPARQL and GDS outputs. Hosted "
              + "outside the plugin; this card just links out.")
        );

        add(new RoadmapPanel(
            "Insights",
            "Stage 5 — composite AI",
            "Decision-intelligence over the graph: <i>tell me what I should do, not just what exists</i>. "
          + "Trigger is real after Stage 2 has been in active use for ≥1 quarter and demand emerges. "
          + "No infrastructure gates — Stage 5 is incremental and additive.",
            items), BorderLayout.CENTER);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public WorkbenchMode getMode()   { return WorkbenchMode.INSIGHTS; }
}
