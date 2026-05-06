package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.uaf.neo4j.plugin.ui.GraphInspectorDialog;

import java.awt.event.ActionEvent;
import java.util.Properties;

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
        Properties config = (plugin != null) ? plugin.getConfig() : new Properties();
        Project project = Application.getInstance().getProject();
        new GraphInspectorDialog(null, config, project).setVisible(true);
    }

    @Override
    public void updateState() {
        setEnabled(true); // inspector works even without an open project
    }
}
