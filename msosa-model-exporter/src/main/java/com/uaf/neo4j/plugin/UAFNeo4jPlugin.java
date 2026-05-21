package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.ActionsConfiguratorsManager;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.plugins.Plugin;
import com.uaf.neo4j.plugin.ui.GraphInspectorDialog;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Entry point for the UAF Neo4j Export Plugin.
 * Registered in plugin.xml; loaded by MSOSA at startup.
 */
public class UAFNeo4jPlugin extends Plugin {

    private static final Logger LOG = Logger.getLogger(UAFNeo4jPlugin.class.getName());
    private static UAFNeo4jPlugin instance;

    private Properties config;
    private GraphInspectorDialog graphInspectorDialog;

    public static UAFNeo4jPlugin getInstance() {
        return instance;
    }

    @Override
    public void init() {
        instance = this;
        loadConfig();
        ActionsConfiguratorsManager.getInstance()
            .addMainMenuConfigurator(new UAFExporterActionsConfigurator());
        LOG.info("UAF Neo4j Export Plugin initialised.");
    }

    @Override
    public boolean close() {
        return true;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    // -------------------------------------------------------------------------

    private void loadConfig() {
        config = new Properties();
        // Defaults matching docker-compose setup
        config.setProperty("neo4j.uri", "bolt://localhost:7687");
        config.setProperty("neo4j.user", "neo4j");
        config.setProperty("neo4j.password", "Password123");
        config.setProperty("neo4j.database", "neo4j");
        config.setProperty("neo4j.batch.size", "500");
        config.setProperty("neo4j.max.connections", "10");
        config.setProperty("export.tagged.values",  "true");
        config.setProperty("export.relationships",  "true");
        config.setProperty("export.instance.links", "true");
        config.setProperty("export.language.uaf",   "true");
        config.setProperty("export.language.sysml", "true");
        config.setProperty("export.language.bpmn",  "true");

        // Stage 2 SPARQL overlay (Apache Jena Fuseki sidecar). The Java plugin
        // does not write to Fuseki directly — the dump script in
        // ontology/codegen/dump_to_rdf.py reads from Neo4j after each export.
        // These properties are referenced by OpenSparqlEndpointAction and
        // ExportSummaryDialog so the user sees a consistent endpoint URL.
        config.setProperty("fuseki.url",      "http://localhost:3030/uaf");
        config.setProperty("fuseki.sparql",   "http://localhost:3030/uaf/sparql");
        config.setProperty("fuseki.user",     "admin");
        config.setProperty("fuseki.password", "Password123");

        File configFile = getConfigFile();
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
            } catch (Exception e) {
                Application.getInstance().getGUILog()
                    .showError("UAF Neo4j Plugin: Failed to load config — " + e.getMessage());
            }
        }
    }

    public void saveConfig(Properties updated) {
        this.config = updated;
        try (FileOutputStream fos = new FileOutputStream(getConfigFile())) {
            updated.store(fos, "UAF Neo4j Plugin Configuration");
        } catch (Exception e) {
            Application.getInstance().getGUILog()
                .showError("UAF Neo4j Plugin: Failed to save config — " + e.getMessage());
        }
    }

    public Properties getConfig() {
        return config;
    }

    /**
     * Shows the Graph Inspector, reusing an existing instance if it is still open.
     * Must be called on the EDT.
     */
    public void showGraphInspector() {
        SwingUtilities.invokeLater(() -> {
            if (graphInspectorDialog == null || !graphInspectorDialog.isDisplayable()) {
                Project project = Application.getInstance().getProject();
                graphInspectorDialog = new GraphInspectorDialog(null, config, project);
            }
            graphInspectorDialog.setVisible(true);
            graphInspectorDialog.toFront();
            graphInspectorDialog.requestFocus();
        });
    }

    /**
     * Opens the Export Configuration dialog. Safe to call from a non-modal context
     * (e.g. the Graph Inspector). The dialog is modal and will block its caller.
     */
    public void showExportDialog() {
        SwingUtilities.invokeLater(() -> {
            Project project = Application.getInstance().getProject();
            if (project == null) {
                Application.getInstance().getGUILog()
                    .showError("No project is open. Please open a UAF project first.");
                return;
            }
            new com.uaf.neo4j.plugin.ui.ExportConfigDialog(null, project).setVisible(true);
        });
    }

    private File getConfigFile() {
        return new File(getDescriptor().getPluginDirectory(), "neo4j-connection.properties");
    }
}
