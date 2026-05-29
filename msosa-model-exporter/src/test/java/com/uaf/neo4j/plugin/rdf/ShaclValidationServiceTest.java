package com.uaf.neo4j.plugin.rdf;

import com.uaf.neo4j.plugin.export.ExportResult;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that {@link ShaclValidationService} returns the right conformance verdict
 * for an empty A-Box (nothing to violate against) and for a deliberately-failing
 * instance (a {@code uaf:Vision} with no {@code uaf:composedOf}, which the
 * {@code uafsh:VisionShape} flags as a Violation).
 *
 * The service falls back to {@code ../ontology/} on the filesystem when the classpath
 * resources are missing, so this test runs in both Maven and IDE contexts.
 */
class ShaclValidationServiceTest {

    @Test
    void emptyAboxConforms() {
        Model abox = ModelFactory.createDefaultModel();
        RDFTripleBuilder.bindPrefixes(abox);

        ExportResult result = new ExportResult();
        ShaclValidationService.validateAndAttach(abox, result);

        // The MVO + axioms themselves should satisfy the cross-cutting hygiene shapes
        // (every MVO class has exactly one uafprop:language). With no instances, the
        // instance-targeting shapes have nothing to fire against. Reasoner-introduced
        // blank-node existentials are filtered out — see ShaclValidationService.isReasonerArtefact.
        assertEquals(Boolean.TRUE, result.shaclConformance,
            "Empty A-Box + MVO should conform. Lines: " + result.shaclViolationLines);
        assertEquals(0, result.shaclViolations);
        assertEquals(0, result.shaclWarnings,
            "Empty A-Box should produce no warnings either; reasoner artefacts must be filtered. "
            + "Lines: " + result.shaclViolationLines);
    }

    @Test
    void visionWithoutComposedOfViolates() {
        Model abox = ModelFactory.createDefaultModel();
        RDFTripleBuilder.bindPrefixes(abox);

        // Hand-build a uaf:Vision instance with no uaf:composedOf — uafsh:VisionShape
        // declares minCount 1 with default sh:Violation severity.
        Resource visionClass = abox.createResource(RDFTripleBuilder.NS_UAF + "Vision");
        Property domain      = abox.createProperty(RDFTripleBuilder.NS_UAF + "domain");
        Resource instance    = abox.createResource(RDFTripleBuilder.NS_INST + "v1");
        instance.addProperty(RDF.type, visionClass);
        instance.addLiteral(RDFS.label, "Test Vision");
        instance.addLiteral(domain,     "STRATEGIC");

        ExportResult result = new ExportResult();
        ShaclValidationService.validateAndAttach(abox, result);

        assertEquals(Boolean.FALSE, result.shaclConformance);
        assertTrue(result.shaclViolations >= 1,
            "Expected at least one Violation, got " + result.shaclViolations
            + " (lines: " + result.shaclViolationLines + ")");
        assertTrue(result.shaclViolationLines.stream()
                       .anyMatch(line -> line.contains("VisionShape")),
            "Expected a VisionShape violation, got: " + result.shaclViolationLines);
    }

    @Test
    void capabilityWithoutExhibitsWarnsButPasses() {
        Model abox = ModelFactory.createDefaultModel();
        RDFTripleBuilder.bindPrefixes(abox);

        // A uaf:Capability with no uaf:exhibits triggers uafsh:CapabilityStrategicAnchorShape
        // (sh:Warning). Warnings should not flip conformance to false.
        Resource capClass = abox.createResource(RDFTripleBuilder.NS_UAF + "Capability");
        Property domain   = abox.createProperty(RDFTripleBuilder.NS_UAF + "domain");
        Resource instance = abox.createResource(RDFTripleBuilder.NS_INST + "c1");
        instance.addProperty(RDF.type, capClass);
        instance.addLiteral(RDFS.label, "Test Capability");
        instance.addLiteral(domain,     "STRATEGIC");

        ExportResult result = new ExportResult();
        ShaclValidationService.validateAndAttach(abox, result);

        assertEquals(Boolean.TRUE, result.shaclConformance,
            "Warnings should not fail conformance. Lines: " + result.shaclViolationLines);
        assertEquals(0, result.shaclViolations);
        assertTrue(result.shaclWarnings >= 1,
            "Expected at least one Warning, got " + result.shaclWarnings);
    }
}
