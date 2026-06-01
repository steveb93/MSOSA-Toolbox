package com.uaf.neo4j.plugin.rdf;

import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RDF emitter's IRI conventions and triple-emission rules.
 *
 * IRI shapes are kept byte-identical to {@code ontology/codegen/dump_to_rdf.py};
 * if these tests change, change the Python side too.
 */
class RDFTripleBuilderTest {

    private final Model model = freshModel();

    private static Model freshModel() {
        Model m = ModelFactory.createDefaultModel();
        RDFTripleBuilder.bindPrefixes(m);
        return m;
    }

    // ── IRI helpers ───────────────────────────────────────────────────────────

    @Test
    void instanceIri_sanitisesUnsafeChars() {
        assertEquals(RDFTripleBuilder.NS_INST + "n_01_a",
                     RDFTripleBuilder.instanceIri(model, "n 01:a").getURI());
    }

    @Test
    void instanceIri_preservesSafeChars() {
        assertEquals(RDFTripleBuilder.NS_INST + "MSOSA-id_42",
                     RDFTripleBuilder.instanceIri(model, "MSOSA-id_42").getURI());
    }

    @Test
    void classIri_selectsUafNamespaceByDefault() {
        assertEquals(RDFTripleBuilder.NS_UAF + "Capability",
                     RDFTripleBuilder.classIri(model, "Capability", "UAF").getURI());
        assertEquals(RDFTripleBuilder.NS_UAF + "Capability",
                     RDFTripleBuilder.classIri(model, "Capability", null).getURI());
    }

    @Test
    void classIri_selectsSysmlNamespace() {
        assertEquals(RDFTripleBuilder.NS_SYSML + "Block",
                     RDFTripleBuilder.classIri(model, "Block", "SysML").getURI());
    }

    @Test
    void classIri_selectsBpmnNamespace() {
        assertEquals(RDFTripleBuilder.NS_BPMN + "Task",
                     RDFTripleBuilder.classIri(model, "Task", "BPMN").getURI());
    }

    @Test
    void predicateIri_camelCasesSimpleType() {
        assertEquals(RDFTripleBuilder.NS_UAF + "performs",
                     RDFTripleBuilder.predicateIri(model, "PERFORMS").getURI());
    }

    @Test
    void predicateIri_camelCasesCompoundType() {
        assertEquals(RDFTripleBuilder.NS_UAF + "informationFlow",
                     RDFTripleBuilder.predicateIri(model, "INFORMATION_FLOW").getURI());
        assertEquals(RDFTripleBuilder.NS_UAF + "tracesTo",
                     RDFTripleBuilder.predicateIri(model, "TRACES_TO").getURI());
        assertEquals(RDFTripleBuilder.NS_UAF + "messageFlow",
                     RDFTripleBuilder.predicateIri(model, "MESSAGE_FLOW").getURI());
    }

    @Test
    void predicateIri_isStableForAllRelConstants() throws Exception {
        // Discover every public static REL_* constant via reflection. If a new REL_* is
        // added without a corresponding CamelCase mapping, this loop will catch it (camelCase
        // is deterministic — what we're really verifying is that nothing throws and every
        // mapping lives in the uaf: namespace).
        List<String> relConstants = collectRelConstants();
        assertTrue(relConstants.size() >= 31, "Expected at least 31 REL_* constants, got " + relConstants.size());
        for (String rel : relConstants) {
            String iri = RDFTripleBuilder.predicateIri(model, rel).getURI();
            assertTrue(iri.startsWith(RDFTripleBuilder.NS_UAF),
                       "predicate IRI for " + rel + " should be in uaf: namespace but was " + iri);
            assertFalse(iri.contains("_"),
                        "predicate IRI for " + rel + " should be camelCase, no underscores; got " + iri);
            assertEquals(iri.charAt(RDFTripleBuilder.NS_UAF.length()),
                         Character.toLowerCase(iri.charAt(RDFTripleBuilder.NS_UAF.length())),
                         "first char of local name should be lowercase for " + rel);
        }
    }

    @Test
    void tagPropertyIri_stripsTvPrefix() {
        assertEquals(RDFTripleBuilder.NS_TAG + "nationality",
                     RDFTripleBuilder.tagPropertyIri(model, "tv_nationality").getURI());
        assertEquals(RDFTripleBuilder.NS_TAG + "capability_level",
                     RDFTripleBuilder.tagPropertyIri(model, "tv_capability_level").getURI());
    }

    @Test
    void tagPropertyIri_handlesKeyWithoutTvPrefix() {
        assertEquals(RDFTripleBuilder.NS_TAG + "rawKey",
                     RDFTripleBuilder.tagPropertyIri(model, "rawKey").getURI());
    }

    // ── addElement ────────────────────────────────────────────────────────────

    @Test
    void addElement_emitsRdfTypeAndLabel() {
        UAFElementDTO dto = UAFElementDTO.builder("cap-1", "Air Superiority", "Capability")
            .neo4jLabel("Capability")
            .domain("STRATEGIC")
            .language("UAF")
            .build();
        RDFTripleBuilder.addElement(model, dto, true);

        var iri = RDFTripleBuilder.instanceIri(model, "cap-1");
        assertTrue(model.contains(iri, RDF.type,
                                  model.createResource(RDFTripleBuilder.NS_UAF + "Capability")),
                   "Expected rdf:type uaf:Capability");
        assertTrue(model.contains(iri, RDFS.label),
                   "Expected an rdfs:label triple for the named element");
    }

    @Test
    void addElement_skipsWhenNeo4jLabelMissing() {
        UAFElementDTO dto = UAFElementDTO.builder("x-1", "Unknown", "Unmapped").build();
        long before = model.size();
        RDFTripleBuilder.addElement(model, dto, true);
        assertEquals(before, model.size(), "Element with empty neo4jLabel should emit nothing");
    }

    @Test
    void addElement_flattensTaggedValuesWithTvPrefix() {
        UAFElementDTO dto = UAFElementDTO.builder("p-1", "Pilot", "Person")
            .neo4jLabel("Person")
            .language("UAF")
            .taggedValue("tv_nationality", "British")
            .taggedValue("notTagged",      "ignored")  // no tv_ prefix
            .build();
        RDFTripleBuilder.addElement(model, dto, true);

        var iri  = RDFTripleBuilder.instanceIri(model, "p-1");
        var prop = RDFTripleBuilder.tagPropertyIri(model, "tv_nationality");
        assertTrue(model.contains(iri, prop), "tv_-prefixed tagged values should emit uaftv:* triples");
        // Non-tv_ keys are skipped — no triple should exist for the literal "ignored" anywhere
        var ignoredProp = RDFTripleBuilder.tagPropertyIri(model, "notTagged");
        assertFalse(model.contains(iri, ignoredProp), "Non-tv_ keys must not be emitted");
    }

    @Test
    void addElement_skipsTaggedValuesWhenDisabled() {
        UAFElementDTO dto = UAFElementDTO.builder("p-2", "Pilot", "Person")
            .neo4jLabel("Person")
            .taggedValue("tv_nationality", "British")
            .build();
        RDFTripleBuilder.addElement(model, dto, /*includeTaggedValues=*/ false);

        var iri  = RDFTripleBuilder.instanceIri(model, "p-2");
        var prop = RDFTripleBuilder.tagPropertyIri(model, "tv_nationality");
        assertFalse(model.contains(iri, prop),
                    "Tagged values must be suppressed when includeTaggedValues=false");
    }

    @Test
    void addElement_emitsGdsWriteBackAsTypedTriples() {
        UAFElementDTO dto = UAFElementDTO.builder("g-1", "Hub", "OperationalPerformer")
            .neo4jLabel("OperationalPerformer")
            .language("UAF")
            .taggedValue("gdsPagerank",  0.42d)   // double -> xsd:double
            .taggedValue("gdsLouvain",   7L)      // long   -> xsd:long
            .taggedValue("notGds",       "ignored")
            .build();
        RDFTripleBuilder.addElement(model, dto, true);

        var iri = RDFTripleBuilder.instanceIri(model, "g-1");
        var pagerank   = RDFTripleBuilder.gdsPropertyIri(model, "gdsPagerank");
        var louvain    = RDFTripleBuilder.gdsPropertyIri(model, "gdsLouvain");
        assertNotNull(pagerank);
        assertNotNull(louvain);
        assertEquals(RDFTripleBuilder.NS_GDS + "pagerank", pagerank.getURI());
        assertEquals(RDFTripleBuilder.NS_GDS + "louvain",  louvain.getURI());

        var pagerankStmts = model.listObjectsOfProperty(iri, pagerank).toList();
        assertEquals(1, pagerankStmts.size());
        assertEquals(0.42d, pagerankStmts.get(0).asLiteral().getDouble());

        var louvainStmts = model.listObjectsOfProperty(iri, louvain).toList();
        assertEquals(1, louvainStmts.size());
        assertEquals(7L, louvainStmts.get(0).asLiteral().getLong());

        // Non-gds, non-tv keys remain unemitted.
        assertNull(RDFTripleBuilder.gdsPropertyIri(model, "notGds"));
    }

    @Test
    void addElement_skipsGdsPropertiesWhenTaggedValuesDisabled() {
        UAFElementDTO dto = UAFElementDTO.builder("g-2", "Hub", "OperationalPerformer")
            .neo4jLabel("OperationalPerformer")
            .taggedValue("gdsPagerank", 0.42d)
            .build();
        RDFTripleBuilder.addElement(model, dto, /*includeTaggedValues=*/ false);

        var iri  = RDFTripleBuilder.instanceIri(model, "g-2");
        var prop = RDFTripleBuilder.gdsPropertyIri(model, "gdsPagerank");
        assertNotNull(prop);
        assertFalse(model.contains(iri, prop),
                    "GDS write-back triples follow the includeTaggedValues switch");
    }

    // ── addRelationship ───────────────────────────────────────────────────────

    @Test
    void addRelationship_emitsTriple() {
        UAFRelationshipDTO rel = UAFRelationshipDTO.builder("r-1", "a", "b", "PERFORMS").build();
        RDFTripleBuilder.addRelationship(model, rel);

        assertTrue(model.contains(
            RDFTripleBuilder.instanceIri(model, "a"),
            RDFTripleBuilder.predicateIri(model, "PERFORMS"),
            RDFTripleBuilder.instanceIri(model, "b")));
    }

    @Test
    void addRelationship_skipsInstanceOf() {
        UAFRelationshipDTO rel = UAFRelationshipDTO.builder("r-2", "a", "b", "INSTANCE_OF").build();
        long before = model.size();
        RDFTripleBuilder.addRelationship(model, rel);
        assertEquals(before, model.size(),
                     "INSTANCE_OF is covered by rdf:type and must not emit a uaf:instanceOf triple");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static List<String> collectRelConstants() throws IllegalAccessException {
        List<String> out = new ArrayList<>();
        for (Field f : UAFRelationshipDTO.class.getDeclaredFields()) {
            int mods = f.getModifiers();
            if (Modifier.isPublic(mods) && Modifier.isStatic(mods) && Modifier.isFinal(mods)
                && f.getType() == String.class && f.getName().startsWith("REL_")) {
                out.add((String) f.get(null));
            }
        }
        return out;
    }
}
