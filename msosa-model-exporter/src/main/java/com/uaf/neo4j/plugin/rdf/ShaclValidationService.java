package com.uaf.neo4j.plugin.rdf;

import com.uaf.neo4j.plugin.export.ExportResult;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.OWLFBRuleReasonerFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.shacl.validation.Severity;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * In-process SHACL validation of the RDF emitter's in-memory A-Box against
 * {@code ontology/shapes/uaf-shapes.ttl}, running with an OWL FB reasoner so
 * results match Fuseki's Stage 3 reasoner profile (the same conformance the
 * standalone {@code ontology/codegen/validate_shacl.py} reports).
 *
 * The shapes file, MVO T-Box, and axioms are bundled into the plugin jar at
 * build time via {@code maven-resources-plugin} (see {@code pom.xml}) and
 * loaded from the classpath at validation time. Source of truth remains
 * {@code ../ontology/} — the plugin ships a snapshot.
 *
 * On success the {@link ExportResult} carries:
 * <ul>
 *   <li>{@link ExportResult#shaclConformance} — {@code true} if zero violations.</li>
 *   <li>{@link ExportResult#shaclViolations} / {@link ExportResult#shaclWarnings} — severity tallies.</li>
 *   <li>{@link ExportResult#shaclViolationLines} — one formatted line per report entry.</li>
 * </ul>
 *
 * On failure (missing resources, reasoner crash) {@code shaclConformance} stays {@code null}
 * and a one-line error is appended to {@link ExportResult#errors}. Leaving the field {@code null}
 * lets the summary dialog render "N/A" rather than misleadingly showing "Pass".
 */
public final class ShaclValidationService {

    private static final Logger LOG = Logger.getLogger(ShaclValidationService.class.getName());

    private static final String RES_MVO    = "/uaf-ontology/uaf-mvo.ttl";
    private static final String RES_AXIOMS = "/uaf-ontology/uaf-mvo-axioms.ttl";
    private static final String RES_SHAPES = "/uaf-ontology/shapes/uaf-shapes.ttl";

    private ShaclValidationService() {}

    public static void validateAndAttach(Model aboxModel, ExportResult result) {
        try {
            Model dataset = ModelFactory.createDefaultModel().add(aboxModel);
            loadResourceInto(dataset, RES_MVO);
            loadResourceInto(dataset, RES_AXIOMS);

            Reasoner reasoner = OWLFBRuleReasonerFactory.theInstance().create(null);
            InfModel inferred = ModelFactory.createInfModel(reasoner, dataset);

            Model shapesModel = ModelFactory.createDefaultModel();
            loadResourceInto(shapesModel, RES_SHAPES);
            Shapes shapes = Shapes.parse(shapesModel.getGraph());

            ValidationReport report =
                ShaclValidator.get().validate(shapes, inferred.getGraph());

            int violations = 0;
            int warnings   = 0;
            for (ReportEntry e : report.getEntries()) {
                if (isReasonerArtefact(e.focusNode())) continue;
                Severity sev = e.severity();
                if (Severity.Violation.equals(sev)) violations++;
                else if (Severity.Warning.equals(sev)) warnings++;
                result.shaclViolationLines.add(formatEntry(e, shapesModel));
            }
            result.shaclViolations  = violations;
            result.shaclWarnings    = warnings;
            result.shaclConformance = (violations == 0);

            LOG.info("SHACL: conforms=" + result.shaclConformance
                     + ", violations=" + violations + ", warnings=" + warnings);
        } catch (Exception e) {
            LOG.warning("SHACL validation failed: " + e.getMessage());
            result.shaclConformance = null;
            result.errors.add("SHACL validation failed: " + e.getMessage());
        }
    }

    private static void loadResourceInto(Model model, String resourcePath) {
        try (InputStream in = openResource(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Ontology resource missing on classpath and filesystem: " + resourcePath
                    + " (bundled by maven-resources-plugin; see pom.xml)");
            }
            RDFDataMgr.read(model, in, Lang.TURTLE);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to load " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Resolve an ontology resource. Production code finds the file on the classpath
     * (bundled by the build); tests running outside Maven's resource-copy phase fall
     * back to {@code ../ontology/<rest>} relative to the working directory, which
     * resolves to the repo's canonical ontology tree.
     */
    private static InputStream openResource(String resourcePath) throws java.io.IOException {
        InputStream cp = ShaclValidationService.class.getResourceAsStream(resourcePath);
        if (cp != null) return cp;
        String stripped = resourcePath.startsWith("/uaf-ontology/")
            ? resourcePath.substring("/uaf-ontology/".length())
            : resourcePath;
        Path fileFallback = Paths.get("..", "ontology", stripped);
        if (Files.isReadable(fileFallback)) {
            return Files.newInputStream(fileFallback);
        }
        return null;
    }

    /**
     * Jena's OWL FB reasoner materialises anonymous individuals to satisfy
     * {@code owl:someValuesFrom} restrictions during TBox closure, even when the
     * A-Box is empty. Those blank-node instances then trip the same SHACL shapes
     * that are meant to govern modeller-authored elements. Filter them: the export
     * only produces URI-named instances under {@code NS_INST}, so anything else
     * is a reasoner artefact and not actionable.
     */
    private static boolean isReasonerArtefact(Node focus) {
        if (focus == null) return true;
        if (focus.isBlank()) return true;
        if (!focus.isURI()) return true;
        return !focus.getURI().startsWith(RDFTripleBuilder.NS_INST);
    }

    private static String formatEntry(ReportEntry entry, Model shapesModel) {
        String shape = resolveShapeName(entry.source(), shapesModel);
        String focus = nodeLabel(entry.focusNode());
        String msg   = entry.message() == null ? "" : entry.message();
        return "[" + severityLabel(entry.severity()) + "] " + shape + ": " + focus + " — " + msg;
    }

    /**
     * SHACL inline property shapes ({@code sh:property [...]}) report as blank nodes,
     * so {@code source()} typically isn't a named shape. Walk back via {@code sh:property}
     * to find the named NodeShape that hosts the failing constraint — that's the name a
     * modeller recognises ("CapabilityConfigurationShape" rather than "_:abc123").
     */
    private static String resolveShapeName(Node source, Model shapesModel) {
        if (source == null) return "AnonymousShape";
        if (!source.isBlank()) return shortName(source);

        Property shProperty = shapesModel.createProperty("http://www.w3.org/ns/shacl#property");
        Resource current = shapesModel.asRDFNode(source).asResource();
        // Bounded walk in case of circular sh:property — bail after 5 hops.
        for (int i = 0; i < 5 && current != null; i++) {
            Resource parent = shapesModel.listSubjectsWithProperty(shProperty, current)
                                         .nextOptional()
                                         .orElse(null);
            if (parent == null) break;
            if (!parent.isAnon()) return shortName(parent.asNode());
            current = parent;
        }
        return "AnonymousShape";
    }

    /**
     * {@link Severity} is a class (not an enum) in Jena 4.10 — compare against the static
     * constants rather than calling {@code name()}. Falls back to the IRI's local name when
     * a future Jena release adds a severity we don't recognise.
     */
    private static String severityLabel(Severity severity) {
        if (severity == null)                return "Violation";
        if (severity.equals(Severity.Violation)) return "Violation";
        if (severity.equals(Severity.Warning))   return "Warning";
        if (severity.equals(Severity.Info))      return "Info";
        return shortName(severity.level());
    }

    private static String shortName(Node node) {
        if (node == null) return "AnonymousShape";
        if (node.isBlank()) return "AnonymousShape";
        if (!node.isURI()) return node.toString();
        String uri = node.getURI();
        int hash = uri.lastIndexOf('#');
        if (hash >= 0 && hash < uri.length() - 1) return uri.substring(hash + 1);
        int slash = uri.lastIndexOf('/');
        if (slash >= 0 && slash < uri.length() - 1) return uri.substring(slash + 1);
        return uri;
    }

    private static String nodeLabel(Node node) {
        if (node == null) return "(none)";
        if (node.isBlank()) return "_:" + node.getBlankNodeLabel();
        if (node.isURI()) return shortName(node);
        return node.toString();
    }
}
