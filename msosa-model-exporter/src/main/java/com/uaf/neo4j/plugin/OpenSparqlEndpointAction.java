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
 * Tools → UAF 1.2 → Neo4j Graph Exporter → Open SPARQL Endpoint
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
        super("UAF_NEO4J_OPEN_SPARQL", "Open SPARQL Endpoint...", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();
        String fusekiUrl = cfg.getProperty("fuseki.url", DEFAULT_URL);
        String browseUrl = toFusekiUiQueryUrl(fusekiUrl);

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            JOptionPane.showMessageDialog(null,
                "Desktop browsing is not supported on this JVM.\n" +
                "Open the SPARQL endpoint manually at:\n" + browseUrl,
                "UAF Neo4j — SPARQL",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(browseUrl));
        } catch (Exception ex) {
            LOG.warning("Could not open SPARQL endpoint: " + ex.getMessage());
            Application.getInstance().getGUILog()
                .showError("UAF Neo4j Plugin: could not open " + browseUrl + " — " + ex.getMessage());
        }
    }

    /**
     * Converts a Fuseki dataset URL into the Fuseki UI query page for that dataset.
     *
     * Examples:
     * <pre>
     *   http://localhost:3030/uaf      → http://localhost:3030/#/dataset/uaf/query
     *   http://localhost:3030/uaf/     → http://localhost:3030/#/dataset/uaf/query
     *   http://host:3030/myds/sparql   → http://host:3030/#/dataset/myds/query
     *   http://host:3030/              → http://host:3030/
     *   http://host:3030               → http://host:3030/
     * </pre>
     *
     * Falls back to the supplied URL (or its origin) when the dataset name can't
     * be inferred — the user confirmed in issue #70 that the bare host page works.
     *
     * Package-private to allow direct unit testing.
     */
    static String toFusekiUiQueryUrl(String fusekiUrl) {
        if (fusekiUrl == null || fusekiUrl.isEmpty()) return fusekiUrl;
        try {
            URI uri = URI.create(fusekiUrl);
            String scheme = uri.getScheme();
            String host   = uri.getHost();
            int    port   = uri.getPort();
            if (scheme == null || host == null) return fusekiUrl;
            String origin = scheme + "://" + host + (port > 0 ? ":" + port : "");
            String path   = uri.getPath() == null ? "" : uri.getPath();
            // Strip leading/trailing slashes and known endpoint suffixes (/sparql, /data, /query, /update)
            String trimmed = path.replaceAll("^/+", "").replaceAll("/+$", "");
            for (String suffix : new String[]{"/sparql", "/data", "/query", "/update"}) {
                if (trimmed.endsWith(suffix)) {
                    trimmed = trimmed.substring(0, trimmed.length() - suffix.length());
                    break;
                }
            }
            if (trimmed.isEmpty()) {
                return origin + "/";
            }
            return origin + "/#/dataset/" + trimmed + "/query";
        } catch (IllegalArgumentException ex) {
            LOG.fine("Could not parse fuseki.url, falling back: " + ex.getMessage());
            return fusekiUrl;
        }
    }
}
