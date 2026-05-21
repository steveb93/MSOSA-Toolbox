package com.uaf.neo4j.plugin.rdf;

import com.uaf.neo4j.plugin.export.ExportResult;
import com.uaf.neo4j.plugin.export.ExportService;
import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * RDF emitter — buffers all writes in an in-memory Jena {@link Model} and, on {@link #close()},
 * (a) writes the model to {@code ${rdf.output.path}} as Turtle and (b) optionally PUTs the
 * same bytes to a Fuseki dataset via Graph Store Protocol.
 *
 * Why buffer until close: Turtle is not a streaming format the way Cypher batches are, and the
 * Fuseki PUT replaces the default graph in one shot — partial uploads would corrupt the SPARQL
 * view. A typical UAF model is in the low thousands of triples so memory cost is negligible.
 *
 * Config keys (read from the {@link Properties} passed to the constructor):
 * <ul>
 *   <li>{@code rdf.output.path}      — destination Turtle file. Default {@code ${user.home}/uaf-instance.ttl}.</li>
 *   <li>{@code fuseki.push.enabled}  — when {@code true}, PUT to Fuseki on close. Default {@code false}.</li>
 *   <li>{@code fuseki.url}           — Fuseki dataset base URL, e.g. {@code http://localhost:3030/uaf}. Default left empty.</li>
 *   <li>{@code fuseki.user}          — Fuseki username for HTTP Basic. Default empty (unauthenticated).</li>
 *   <li>{@code fuseki.password}      — Fuseki password. Default empty.</li>
 * </ul>
 */
public class RDFExportService implements ExportService {

    private static final Logger LOG = Logger.getLogger(RDFExportService.class.getName());

    private final Path outputPath;
    private final boolean pushToFuseki;
    private final String fusekiUrl;
    private final String fusekiUser;
    private final String fusekiPassword;

    private Model model;
    private final ExportResult result = new ExportResult();

    public RDFExportService(Properties config) {
        this.outputPath     = Paths.get(config.getProperty("rdf.output.path",
                                  System.getProperty("user.home") + "/uaf-instance.ttl"));
        this.pushToFuseki   = Boolean.parseBoolean(config.getProperty("fuseki.push.enabled", "false"));
        this.fusekiUrl      = config.getProperty("fuseki.url",      "http://localhost:3030/uaf");
        this.fusekiUser     = config.getProperty("fuseki.user",     "");
        this.fusekiPassword = config.getProperty("fuseki.password", "");
    }

    @Override
    public void init() {
        model = ModelFactory.createDefaultModel();
        RDFTripleBuilder.bindPrefixes(model);
        LOG.info("RDFExportService: buffering triples; output → " + outputPath
                 + (pushToFuseki ? "; will PUT to " + fusekiUrl + "/data on close" : ""));
    }

    @Override
    public void exportSystemModel(String id, String name) {
        // The system model is intentionally not emitted as RDF — the equivalent
        // provenance lives in named graphs in stage 3. Mirror Python (which also
        // ignores the system-model node): no-op here keeps both emitters
        // semantically aligned for stage 2 queries.
    }

    @Override
    public void exportNodes(List<UAFElementDTO> elements) {
        exportNodes(elements, true);
    }

    @Override
    public void exportNodes(List<UAFElementDTO> elements, boolean includeTaggedValues) {
        for (UAFElementDTO dto : elements) {
            try {
                RDFTripleBuilder.addElement(model, dto, includeTaggedValues);
                result.nodesWritten++;
            } catch (Exception e) {
                LOG.warning("Element " + dto.id + " failed to translate: " + e.getMessage());
                result.errors.add("RDF element " + dto.id + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void exportRelationships(List<UAFRelationshipDTO> relationships) {
        for (UAFRelationshipDTO dto : relationships) {
            try {
                RDFTripleBuilder.addRelationship(model, dto);
                result.relationshipsWritten++;
            } catch (Exception e) {
                LOG.warning("Relationship " + dto.id + " failed to translate: " + e.getMessage());
                result.errors.add("RDF relationship " + dto.id + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void exportDefinesLinks(String systemModelId, List<UAFElementDTO> elements) {
        // Same rationale as exportSystemModel — no RDF emission for provenance edges in stage 2.
    }

    @Override
    public void exportInstanceOfLinks(List<UAFElementDTO> elements) {
        // RDF carries instance-of via rdf:type triples emitted by addElement(); no separate edge needed.
    }

    @Override
    public boolean testConnection() {
        // No persistent connection — local file write is the source of truth. Probe Fuseki only
        // if push is configured; otherwise success means "we can write the file".
        if (pushToFuseki) {
            try {
                FusekiClient probe = new FusekiClient(fusekiUrl, fusekiUser, fusekiPassword);
                // A no-op PUT of an empty Turtle document is the cheapest connectivity check.
                probe.putTurtle("@prefix uaf: <" + RDFTripleBuilder.NS_UAF + "> .\n");
                return true;
            } catch (Exception e) {
                LOG.warning("Fuseki connectivity probe failed: " + e.getMessage());
                return false;
            }
        }
        return Files.isWritable(outputPath.getParent() != null ? outputPath.getParent() : Paths.get("."));
    }

    @Override
    public ExportResult getResult() {
        return result;
    }

    @Override
    public void close() {
        if (model == null) return;
        try {
            writeTurtle();
        } catch (IOException e) {
            LOG.warning("Failed to write " + outputPath + ": " + e.getMessage());
            result.errors.add("Turtle write: " + e.getMessage());
        }
        if (pushToFuseki) {
            try {
                pushToFuseki();
            } catch (Exception e) {
                LOG.warning("Fuseki PUT failed: " + e.getMessage());
                result.errors.add("Fuseki PUT: " + e.getMessage());
            }
        }
        model.close();
        model = null;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void writeTurtle() throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (OutputStream out = Files.newOutputStream(outputPath)) {
            RDFDataMgr.write(out, model, Lang.TURTLE);
        }
        LOG.info("Wrote " + result.nodesWritten + " elements + "
                 + result.relationshipsWritten + " relationships as Turtle → " + outputPath);
    }

    private void pushToFuseki() throws IOException, InterruptedException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        RDFDataMgr.write(buf, model, Lang.TURTLE);
        String body = buf.toString(StandardCharsets.UTF_8);
        FusekiClient client = new FusekiClient(fusekiUrl, fusekiUser, fusekiPassword);
        int code = client.putTurtle(body);
        LOG.info("Fuseki PUT " + fusekiUrl + "/data returned HTTP " + code);
    }
}
