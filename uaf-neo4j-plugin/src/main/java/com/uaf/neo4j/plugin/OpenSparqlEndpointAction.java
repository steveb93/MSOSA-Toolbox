package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;

import javax.swing.JOptionPane;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Tools → UAF Neo4j Export → Open SPARQL Endpoint
 *
 * Opens the Fuseki SPARQL endpoint (Stage 2 ontology overlay) in the system
 * default browser. The URL is taken from {@code fuseki.url} in
 * neo4j-connection.properties; default targets the local docker-compose stack.
 */
public class OpenSparqlEndpointAction extends MDAction {

    private static final Logger LOG = Logger.getLogger(OpenSparqlEndpointAction.class.getName());
    private static final String DEFAULT_URL = "http://localhost:3030/uaf";

    public OpenSparqlEndpointAction() {
        super("UAF_NEO4J_OPEN_SPARQL", "Open SPARQL Endpoint...", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();
        String url = cfg.getProperty("fuseki.url", DEFAULT_URL);

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(null,
                "Desktop browsing is not supported on this JVM.\n" +
                "Open the SPARQL endpoint manually at:\n" + url,
                "UAF Neo4j — SPARQL",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            LOG.warning("Could not open SPARQL endpoint: " + ex.getMessage());
            Application.getInstance().getGUILog()
                .showError("UAF Neo4j Plugin: could not open " + url + " — " + ex.getMessage());
        }
    }
}
