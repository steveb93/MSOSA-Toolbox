package com.uaf.neo4j.plugin.ui.workbench;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.ui.GraphInspectorDialog;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * Inspect mode — embeds the {@link GraphInspectorDialog} form directly. The
 * dialog object exists only as a controller and is never shown; the table,
 * property pane, and JGraphX neighbourhood view live in the workbench's card
 * area.
 *
 * <p>The Inspect rail item is the future home of the {@code FusekiGraphSource}
 * backend toggle and OWL FB inference selector. For now the form runs against
 * Neo4j via Bolt — same as the legacy dialog.
 *
 * @see com.uaf.neo4j.plugin.graph.GraphSource
 */
final class InspectModePanel extends JPanel implements WorkbenchPanel {

    @SuppressWarnings("unused") // held to keep the dialog instance alive for action listeners
    private final GraphInspectorDialog controllerDialog;

    InspectModePanel(UAFWorkbench workbench) {
        super(new BorderLayout());
        setBackground(Color.WHITE);

        controllerDialog = workbench.createInspectorDialog(
            UAFNeo4jPlugin.getInstance().getConfig(),
            workbench.getProject());
        add(controllerDialog.getEmbeddedBody(), BorderLayout.CENTER);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public WorkbenchMode getMode()   { return WorkbenchMode.INSPECT; }
}
