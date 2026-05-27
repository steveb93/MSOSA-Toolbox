package com.uaf.neo4j.plugin.ui.workbench;

import javax.swing.JComponent;

import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.List;

/**
 * Federate mode — Stage 3 roadmap placeholder. Outbound SPARQL {@code SERVICE}
 * federation lets the local Fuseki query external endpoints (Wikidata,
 * canonical data registries) without introducing a second triplestore. See
 * {@code ontology/NEXT-STEPS.md §Stage 3} and the decision-log row
 * "Federation reframed to outbound SERVICE only".
 */
final class FederateModePanel extends javax.swing.JPanel implements WorkbenchPanel {

    FederateModePanel() {
        super(new BorderLayout());
        List<RoadmapPanel.RoadmapItem> items = Arrays.asList(
            new RoadmapPanel.RoadmapItem("Wikidata SPARQL service",
                "Join UAF Capability concepts to Wikidata equivalents — e.g. enrich operational performers "
              + "with industry-standard URIs for downstream tooling."),
            new RoadmapPanel.RoadmapItem("Endpoint registry",
                "Persisted list of trusted SPARQL endpoints with per-endpoint timeout, auth (basic/bearer), "
              + "and a probe button — mirror of the Settings panel for Fuseki itself."),
            new RoadmapPanel.RoadmapItem("Query helper",
                "Wrap a user's local SPARQL fragment in a SERVICE block against the chosen endpoint, with "
              + "prefix harmonisation between local UAF MVO and the remote ontology.")
        );

        add(new RoadmapPanel(
            "Federate",
            "Stage 3 — outbound SPARQL SERVICE only",
            "Cross-tool federation lives at the network boundary. Reach external endpoints via SPARQL "
          + "<code>SERVICE</code> rather than spinning up a second triplestore inside the toolbox. "
          + "Stays consistent with the simplification goal in the decision log.",
            items), BorderLayout.CENTER);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public WorkbenchMode getMode()   { return WorkbenchMode.FEDERATE; }
}
