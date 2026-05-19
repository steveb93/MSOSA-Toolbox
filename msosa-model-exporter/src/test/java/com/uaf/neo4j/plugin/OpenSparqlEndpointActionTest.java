package com.uaf.neo4j.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for issue #70 — "Open SPARQL Endpoint" must not browse to the
 * dataset's API endpoint (`/uaf`, `/uaf/sparql`, …), which Fuseki responds to with
 * "No endpoint for request". The UI's query page at `/#/dataset/<name>/query` is the
 * route that actually works in a browser.
 */
class OpenSparqlEndpointActionTest {

    @Test
    void datasetUrl_isRewrittenToFusekiUiQueryPage() {
        assertEquals(
            "http://localhost:3030/#/dataset/uaf/query",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://localhost:3030/uaf"));
    }

    @Test
    void datasetUrl_withTrailingSlash_isRewritten() {
        assertEquals(
            "http://localhost:3030/#/dataset/uaf/query",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://localhost:3030/uaf/"));
    }

    @Test
    void sparqlEndpointSuffix_isStrippedFromDatasetName() {
        assertEquals(
            "http://host:3030/#/dataset/myds/query",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://host:3030/myds/sparql"));
    }

    @Test
    void dataEndpointSuffix_isStrippedFromDatasetName() {
        assertEquals(
            "http://host:3030/#/dataset/myds/query",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://host:3030/myds/data"));
    }

    @Test
    void rootUrl_fallsBackToOrigin() {
        assertEquals(
            "http://host:3030/",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://host:3030/"));
        assertEquals(
            "http://host:3030/",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://host:3030"));
    }

    @Test
    void datasetWithDashesAndUnderscores_isPreservedInName() {
        assertEquals(
            "http://host:3030/#/dataset/my-data_set/query",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://host:3030/my-data_set"));
    }

    @Test
    void nonStandardPort_isPreserved() {
        assertEquals(
            "http://192.168.1.11:3030/#/dataset/uaf/query",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("http://192.168.1.11:3030/uaf"));
    }

    @Test
    void httpsScheme_isPreserved() {
        assertEquals(
            "https://fuseki.example.com:8443/#/dataset/uaf/query",
            OpenSparqlEndpointAction.toFusekiUiQueryUrl("https://fuseki.example.com:8443/uaf"));
    }

    @Test
    void nullOrEmpty_returnsAsIs() {
        assertNull(OpenSparqlEndpointAction.toFusekiUiQueryUrl(null));
        assertEquals("", OpenSparqlEndpointAction.toFusekiUiQueryUrl(""));
    }
}
