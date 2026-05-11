package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;

import java.awt.event.ActionEvent;

/**
 * Tools → UAF Neo4j Export → Browse Graph…
 * Opens the non-modal Graph Inspector dialog.
 */
public class GraphInspectorAction extends MDAction {

    public GraphInspectorAction() {
        super("UAF_NEO4J_GRAPH_INSPECTOR", "Browse Graph…", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        UAFNeo4jPlugin plugin = UAFNeo4jPlugin.getInstance();
        if (plugin != null) {
            plugin.showGraphInspector();
        }
    }

    @Override
    public void updateState() {
        setEnabled(true); // inspector works even without an open project
    }
}
