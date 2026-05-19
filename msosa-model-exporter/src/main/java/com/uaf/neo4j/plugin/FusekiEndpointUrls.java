package com.uaf.neo4j.plugin;

import java.net.URI;
import java.util.logging.Logger;

/**
 * Pure URL-derivation helpers for the Fuseki endpoint, extracted out of
 * {@link OpenSparqlEndpointAction} so they can be exercised by unit tests without
 * initialising the MSOSA {@code NMAction} class hierarchy — its static initialiser
 * calls {@code Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()} which throws
 * {@code HeadlessException} on a CI runner that has no display.
 */
public final class FusekiEndpointUrls {

    private static final Logger LOG = Logger.getLogger(FusekiEndpointUrls.class.getName());

    private FusekiEndpointUrls() {}

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
     * Falls back to the supplied URL (or its origin) when the dataset name cannot be
     * inferred — the bare Fuseki UI page works as a usable fallback (the user
     * confirmed this in issue #70).
     */
    public static String toUiQueryUrl(String fusekiUrl) {
        if (fusekiUrl == null || fusekiUrl.isEmpty()) return fusekiUrl;
        try {
            URI uri = URI.create(fusekiUrl);
            String scheme = uri.getScheme();
            String host   = uri.getHost();
            int    port   = uri.getPort();
            if (scheme == null || host == null) return fusekiUrl;
            String origin = scheme + "://" + host + (port > 0 ? ":" + port : "");
            String path   = uri.getPath() == null ? "" : uri.getPath();
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
