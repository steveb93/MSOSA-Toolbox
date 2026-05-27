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
 * Tools → UAF Knowledge Graph → Open SPARQL Endpoint
 *
 * Opens the Fuseki <b>UI</b> dataset query page in the system default browser.
 *
 * Why the UI page, not the SPARQL or data endpoints — the dataset endpoints
 * (`/uaf/sparql`, `/uaf/data`) only respond to API requests; opening either in
 * a browser returns "No endpoint for request" (see issue #70). The UI route
 * `/#/dataset/<name>/query` renders an interactive SPARQL query console for
 * the configured dataset, which is what users actually want when they pick
 * "Open SPARQL Endpoint" from the menu.
 */
public class OpenSparqlEndpointAction extends MDAction {

    private static final Logger LOG = Logger.getLogger(OpenSparqlEndpointAction.class.getName());
    private static final String DEFAULT_URL = "http://localhost:3030/uaf";

    public OpenSparqlEndpointAction() {
        super("UAF_KG_OPEN_SPARQL", "Open SPARQL Endpoint…", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();
        String fusekiUrl = cfg.getProperty("fuseki.url", DEFAULT_URL);
        String browseUrl = FusekiEndpointUrls.toUiQueryUrl(fusekiUrl);

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(null,
                "Desktop browsing is not supported on this JVM.\n" +
                "Open the SPARQL endpoint manually at:\n" + browseUrl,
                "UAF Knowledge Graph — SPARQL",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(browseUrl));
        } catch (Exception ex) {
            LOG.warning("Could not open SPARQL endpoint: " + ex.getMessage());
            Application.getInstance().getGUILog()
                .showError("UAF Knowledge Graph: could not open " + browseUrl + " — " + ex.getMessage());
        }
    }
}
