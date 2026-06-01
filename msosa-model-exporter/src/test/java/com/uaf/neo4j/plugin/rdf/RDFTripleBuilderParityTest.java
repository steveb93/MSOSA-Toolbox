package com.uaf.neo4j.plugin.rdf;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java side of the RDF-emitter parity test (GitHub issue #73).
 *
 * Loads the shared fixture at {@code ontology/codegen/parity-fixture.json} and
 * asserts that {@link RDFTripleBuilder}'s IRI helpers produce the same outputs
 * the matching Python test ({@code Test/test_rdf_parity.py}) expects from
 * {@code dump_to_rdf.py}. If either implementation drifts on namespaces,
 * sanitisation, or camelCase rules, one of the two tests fails.
 *
 * Uses Apache Jena's Atlas JSON parser (already on the classpath via jena-arq)
 * so no extra test dependency is required.
 */
class RDFTripleBuilderParityTest {

    private static JsonObject fixture;
    private static Map<String, String> namespaces;
    private static Model model;

    @BeforeAll
    static void loadFixture() throws Exception {
        Path fixturePath = resolveFixturePath();
        try (InputStream in = Files.newInputStream(fixturePath)) {
            fixture = JSON.parse(in);
        }
        namespaces = new HashMap<>();
        JsonObject ns = fixture.get("namespaces").getAsObject();
        for (String key : ns.keys()) {
            namespaces.put(key, ns.get(key).getAsString().value());
        }
        model = ModelFactory.createDefaultModel();
        RDFTripleBuilder.bindPrefixes(model);
    }

    /**
     * Locate {@code ontology/codegen/parity-fixture.json} relative to the working
     * directory. Surefire runs from the Maven module directory, so the fixture
     * is one level up at {@code ../ontology/codegen/parity-fixture.json}. Fall
     * back to walking up to two more parents in case the test is invoked from a
     * different cwd (e.g. an IDE).
     */
    private static Path resolveFixturePath() {
        Path base = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4; i++) {
            Path candidate = base.resolve("ontology").resolve("codegen").resolve("parity-fixture.json");
            if (Files.isRegularFile(candidate)) return candidate;
            Path parentCandidate = base.resolve("..").resolve("ontology").resolve("codegen").resolve("parity-fixture.json").normalize();
            if (Files.isRegularFile(parentCandidate)) return parentCandidate;
            base = base.getParent();
            if (base == null) break;
        }
        throw new IllegalStateException(
            "Could not locate ontology/codegen/parity-fixture.json from cwd "
            + Paths.get("").toAbsolutePath());
    }

    private static String expand(String curie) {
        int colon = curie.indexOf(':');
        if (colon < 0) throw new AssertionError("fixture CURIE missing prefix: " + curie);
        String prefix = curie.substring(0, colon);
        String local  = curie.substring(colon + 1);
        String ns = namespaces.get(prefix);
        if (ns == null) {
            throw new AssertionError("fixture references unknown prefix '" + prefix + "' in " + curie);
        }
        return ns + local;
    }

    private static String stringOrNull(JsonValue v) {
        if (v == null || v.isNull()) return null;
        return v.getAsString().value();
    }

    // ── Namespace constants stay in sync with the fixture ─────────────────────

    @Test
    void namespacesMatchBuilderConstants() {
        assertEquals(namespaces.get("uaf"),     RDFTripleBuilder.NS_UAF);
        assertEquals(namespaces.get("sysml"),   RDFTripleBuilder.NS_SYSML);
        assertEquals(namespaces.get("bpmn"),    RDFTripleBuilder.NS_BPMN);
        assertEquals(namespaces.get("uafinst"), RDFTripleBuilder.NS_INST);
        assertEquals(namespaces.get("uaftv"),   RDFTripleBuilder.NS_TAG);
        assertEquals(namespaces.get("uafgds"),  RDFTripleBuilder.NS_GDS);
    }

    // ── instance IRI parity ───────────────────────────────────────────────────

    @Test
    void instanceIriCases() {
        JsonArray cases = fixture.get("instance_iri").getAsArray();
        for (JsonValue v : cases) {
            JsonObject c = v.getAsObject();
            String id       = c.get("id").getAsString().value();
            String expected = expand(c.get("expected_curie").getAsString().value());
            String actual   = RDFTripleBuilder.instanceIri(model, id).getURI();
            assertEquals(expected, actual,
                "instanceIri(" + id + ") parity drift");
        }
    }

    // ── class IRI parity ──────────────────────────────────────────────────────

    @Test
    void classIriCases() {
        JsonArray cases = fixture.get("class_iri").getAsArray();
        for (JsonValue v : cases) {
            JsonObject c = v.getAsObject();
            String label    = c.get("label").getAsString().value();
            String language = stringOrNull(c.get("language"));
            String expected = expand(c.get("expected_curie").getAsString().value());
            String actual   = RDFTripleBuilder.classIri(model, label, language).getURI();
            assertEquals(expected, actual,
                "classIri(" + label + ", " + language + ") parity drift");
        }
    }

    // ── predicate IRI parity ──────────────────────────────────────────────────

    @Test
    void predicateIriCases() {
        JsonArray cases = fixture.get("predicate_iri").getAsArray();
        for (JsonValue v : cases) {
            JsonObject c = v.getAsObject();
            String relType  = c.get("rel_type").getAsString().value();
            String expected = expand(c.get("expected_curie").getAsString().value());
            String actual   = RDFTripleBuilder.predicateIri(model, relType).getURI();
            assertEquals(expected, actual,
                "predicateIri(" + relType + ") parity drift");
        }
    }

    // ── tag-property IRI parity ───────────────────────────────────────────────

    @Test
    void tagPropertyIriCases() {
        JsonArray cases = fixture.get("tag_property_iri").getAsArray();
        for (JsonValue v : cases) {
            JsonObject c = v.getAsObject();
            String key      = c.get("key").getAsString().value();
            String expected = expand(c.get("expected_curie").getAsString().value());
            String actual   = RDFTripleBuilder.tagPropertyIri(model, key).getURI();
            assertEquals(expected, actual,
                "tagPropertyIri(" + key + ") parity drift");
        }
    }

    // ── GDS write-back property IRI parity ────────────────────────────────────

    @Test
    void gdsPropertyIriCases() {
        JsonArray cases = fixture.get("gds_property_iri").getAsArray();
        for (JsonValue v : cases) {
            JsonObject c = v.getAsObject();
            String key      = c.get("key").getAsString().value();
            JsonValue ec    = c.get("expected_curie");
            org.apache.jena.rdf.model.Property actual = RDFTripleBuilder.gdsPropertyIri(model, key);
            if (ec == null || ec.isNull()) {
                org.junit.jupiter.api.Assertions.assertNull(actual,
                    "gdsPropertyIri(" + key + "): expected null, got " + actual);
                continue;
            }
            String expected = expand(ec.getAsString().value());
            org.junit.jupiter.api.Assertions.assertNotNull(actual,
                "gdsPropertyIri(" + key + "): expected " + expected + ", got null");
            assertEquals(expected, actual.getURI(),
                "gdsPropertyIri(" + key + ") parity drift");
        }
    }
}
