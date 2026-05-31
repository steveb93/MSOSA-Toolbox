package com.uaf.neo4j.plugin.rdf;

import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
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
 * In-process SHACL validation against {@code ontology/shapes/uaf-shapes.ttl}.
 *
 * <p>Runs <b>without</b> an in-JVM reasoner. The snapshot supplied by the Validate
 * rail comes straight out of Fuseki's CONSTRUCT, which is already RDFS-Exp inferred
 * by the dataset's assembler — the same triples your SPARQL queries see. SHACL is
 * evaluated directly against that graph (plus the bundled MVO + axioms so
 * {@code sh:class} checks resolve against the UAF type hierarchy).
 *
 * <p>The previous implementation wrapped the dataset with Jena's OWL FB rule reasoner.
 * On real UAF A-Boxes the rule LP interpreter materialised ~170 M intermediate
 * triples and millions of choice-point frames, blowing past 15 GB heap inside
 * MSOSA's JVM and making the Validate rail unusable. OWL FB closure is intentionally
 * out of scope for the in-process validator; if you need full OWL inference run
 * {@code ontology/codegen/validate_shacl.py} (pyshacl + owlrl) outside MSOSA.
 *
 * <p>The shapes file, MVO T-Box, and axioms are bundled into the plugin jar at build
 * time via {@code maven-antrun-plugin} (see {@code pom.xml}) and loaded from the
 * classpath at validation time. Source of truth remains {@code ../ontology/} — the
 * plugin ships a snapshot.
 */
public final class ShaclValidationService {

    private static final Logger LOG = Logger.getLogger(ShaclValidationService.class.getName());

    private static final String RES_MVO    = "/uaf-ontology/uaf-mvo.ttl";
    private static final String RES_AXIOMS = "/uaf-ontology/uaf-mvo-axioms.ttl";
    private static final String RES_SHAPES = "/uaf-ontology/shapes/uaf-shapes.ttl";

    private ShaclValidationService() {}

    /**
     * Validate the supplied A-Box against the bundled UAF shapes. Returns a
     * {@link ShaclReport} with the verdict and any violation lines, or a report
     * whose {@link ShaclReport#conforms} is {@code null} if the validator failed
     * to run (in which case the {@link ShaclReport#errors} list carries the
     * cause).
     */
    public static ShaclReport validate(Model aboxModel) {
        ShaclReport report = new ShaclReport();
        try {
            // Merge MVO + axioms onto the data graph so sh:class and sh:targetClass
            // checks resolve against the UAF type hierarchy. No reasoner — Fuseki
            // has already RDFS-Exp inferred the snapshot, and OWL FB closure here
            // was what blew MSOSA's heap (see class Javadoc).
            Model dataset = ModelFactory.createDefaultModel().add(aboxModel);
            loadResourceInto(dataset, RES_MVO);
            loadResourceInto(dataset, RES_AXIOMS);

            Model shapesModel = ModelFactory.createDefaultModel();
            loadResourceInto(shapesModel, RES_SHAPES);
            Shapes shapes = Shapes.parse(shapesModel.getGraph());

            ValidationReport jenaReport =
                ShaclValidator.get().validate(shapes, dataset.getGraph());

            int violations = 0;
            int warnings   = 0;
            for (ReportEntry e : jenaReport.getEntries()) {
                if (isReasonerArtefact(e.focusNode())) continue;
                Severity sev = e.severity();
                if (Severity.Violation.equals(sev)) violations++;
                else if (Severity.Warning.equals(sev)) warnings++;
                report.lines.add(formatEntry(e, shapesModel));
            }
            report.violations = violations;
            report.warnings   = warnings;
            report.conforms   = (violations == 0);

            LOG.info("SHACL: conforms=" + report.conforms
                     + ", violations=" + violations + ", warnings=" + warnings);
        } catch (Exception e) {
            LOG.warning("SHACL validation failed: " + e.getMessage());
            report.conforms = null;
            report.errors.add("SHACL validation failed: " + e.getMessage());
        }
        return report;
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
