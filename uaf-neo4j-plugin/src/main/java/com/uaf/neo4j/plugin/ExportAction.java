package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFModelTraverser;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService.ExportResult;
import com.uaf.neo4j.plugin.ui.ExportSummaryDialog;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Properties;

/**
 * Tools → UAF Neo4j Export → Export to Neo4j
 * Runs model traversal + Neo4j write in a background SwingWorker so the UI
 * remains responsive during export.
 */
public class ExportAction extends MDAction {

    public ExportAction() {
        super("UAF_NEO4J_EXPORT", "Export to Neo4j...", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = Application.getInstance().getProject();
        if (project == null) {
            JOptionPane.showMessageDialog(
                null,
                "No project is open. Please open a UAF model before exporting.",
                "UAF Neo4j Export",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        Properties config = UAFNeo4jPlugin.getInstance().getConfig();

        new SwingWorker<ExportResult, String>() {

            @Override
            protected ExportResult doInBackground() throws Exception {
                publish("Traversing UAF model...");
                UAFModelTraverser traverser = new UAFModelTraverser(project);
                List<UAFElementDTO> elements = traverser.getElements();
                List<UAFRelationshipDTO> relationships = traverser.getRelationships();

                publish(String.format("Found %d elements, %d relationships. Connecting to Neo4j...",
                    elements.size(), relationships.size()));

                try (Neo4jExportService service = new Neo4jExportService(config)) {
                    service.init();
                    publish("Writing nodes...");
                    service.exportNodes(elements);
                    publish("Writing relationships...");
                    service.exportRelationships(relationships);
                    publish("Linking to UAF metamodel stereotypes...");
                    service.exportInstanceOfLinks(elements);
                    return service.getResult();
                }
            }

            @Override
            protected void done() {
                try {
                    ExportResult result = get();
                    new ExportSummaryDialog(null, result).setVisible(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Export failed: " + ex.getCause().getMessage(),
                        "UAF Neo4j Export",
                        JOptionPane.ERROR_MESSAGE);
                }
            }

        }.execute();
    }

    @Override
    public void updateState() {
        setEnabled(Application.getInstance().getProject() != null);
    }
}
