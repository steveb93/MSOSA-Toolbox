package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.ActionsConfiguratorsManager;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.plugins.Plugin;

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

    private File getConfigFile() {
        return new File(getDescriptor().getPluginDirectory(), "neo4j-connection.properties");
    }
}
