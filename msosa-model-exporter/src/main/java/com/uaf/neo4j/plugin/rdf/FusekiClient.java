package com.uaf.neo4j.plugin.rdf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Minimal SPARQL 1.1 Graph Store Protocol client. Performs a single
 * {@code PUT {fusekiBaseUrl}/data} of a Turtle body — replaces the default graph
 * of the target Fuseki dataset with the supplied bytes.
 *
 * Uses Java 11's {@link HttpClient} so the plugin gains no extra HTTP dependency.
 *
 * Auth: pre-emptive HTTP Basic when both user and password are non-empty (Fuseki
 * default config does not advertise WWW-Authenticate on protected endpoints, so
 * pre-emptive is the only thing that works reliably).
 */
public final class FusekiClient {

    private final URI dataUrl;
    private final URI sparqlUrl;
    private final String authHeader;
    private final HttpClient client;

    /**
     * @param baseUrl  Fuseki dataset base URL, e.g. {@code http://localhost:3030/uaf}.
     *                 Trailing {@code /} is tolerated.
     * @param user     Username; pass empty string for an unauthenticated endpoint.
     * @param password Password; pass empty string for an unauthenticated endpoint.
     */
    public FusekiClient(String baseUrl, String user, String password) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dataUrl    = URI.create(base + "/data");
        this.sparqlUrl  = URI.create(base + "/sparql");
        this.authHeader = buildAuthHeader(user, password);
        this.client     = HttpClient.newBuilder()
                                    .connectTimeout(Duration.ofSeconds(10))
                                    .build();
    }

    /**
     * Cheap, non-destructive probe — runs {@code ASK WHERE { }} against the dataset's
     * SPARQL query endpoint. Returns true on any 2xx; any other response (including
     * "No endpoint for request" or a connection error) returns false.
     */
    public boolean testConnection() {
        try {
            URI probeUri = URI.create(sparqlUrl + "?query=ASK%20WHERE%20%7B%7D");
            HttpRequest.Builder rb = HttpRequest.newBuilder(probeUri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/sparql-results+json")
                .GET();
            if (authHeader != null) {
                rb.header("Authorization", authHeader);
            }
            HttpResponse<String> resp = client.send(rb.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * PUT the Turtle body to the default graph. Replaces (does not merge) the previous contents.
     *
     * @return HTTP status code (200/201/204 indicate success).
     * @throws java.io.IOException if the request fails, the server returns ≥400, or a network error occurs.
     */
    public int putTurtle(String turtleBody) throws java.io.IOException, InterruptedException {
        HttpRequest.Builder rb = HttpRequest.newBuilder(dataUrl)
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "text/turtle; charset=utf-8")
            .PUT(BodyPublishers.ofString(turtleBody, StandardCharsets.UTF_8));
        if (authHeader != null) {
            rb.header("Authorization", authHeader);
        }
        HttpResponse<String> resp = client.send(rb.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
        int code = resp.statusCode();
        if (code >= 400) {
            throw new java.io.IOException(
                "Fuseki PUT " + dataUrl + " failed with HTTP " + code + ": " + resp.body());
        }
        return code;
    }

    private static String buildAuthHeader(String user, String password) {
        if (user == null || user.isEmpty()) return null;
        String token = (user + ":" + (password == null ? "" : password));
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
