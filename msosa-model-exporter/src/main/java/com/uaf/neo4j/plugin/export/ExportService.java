package com.uaf.neo4j.plugin.export;

import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;

import java.util.List;

/**
 * Common write contract shared by every emitter that persists the traversed UAF model.
 *
 * Implementations exist for each target store:
 * <ul>
 *   <li>{@code Neo4jExportService} — writes Cypher (LPG) over Bolt.</li>
 *   <li>{@code RDFExportService}   — writes Turtle (RDF) to disk and/or pushes to Fuseki
 *       via Graph Store Protocol. Planned per {@code ontology/NEXT-STEPS.md} Stage 4.</li>
 * </ul>
 *
 * The pipeline ({@code ExportConfigDialog#runExport}) invokes implementations through this
 * interface, so multiple targets can be selected at the same time without the dialog
 * having to know which backends are in play.
 *
 * Lifecycle: {@code init()} → any combination of {@code exportSystemModel}/{@code exportNodes}/
 * {@code exportRelationships}/{@code exportDefinesLinks}/{@code exportInstanceOfLinks} → {@code close()}.
 * Use try-with-resources via the {@link AutoCloseable} parent.
 */
public interface ExportService extends AutoCloseable {

    /** Opens the backend connection and verifies connectivity. Throws if the backend is unreachable. */
    void init();

    /** MERGEs / asserts the project-level system-model node carrying the supplied id and display name. */
    void exportSystemModel(String id, String name);

    /** Persists each element. Tagged values are included by default. */
    void exportNodes(List<UAFElementDTO> elements);

    /** Persists each element. When {@code includeTaggedValues} is false, tv_* properties are omitted. */
    void exportNodes(List<UAFElementDTO> elements, boolean includeTaggedValues);

    /** Persists each relationship. */
    void exportRelationships(List<UAFRelationshipDTO> relationships);

    /** Links the system-model node to every exported element with the backend's "defines" predicate. */
    void exportDefinesLinks(String systemModelId, List<UAFElementDTO> elements);

    /** Links each element to its metamodel stereotype with the backend's "instance-of" predicate. */
    void exportInstanceOfLinks(List<UAFElementDTO> elements);

    /** Returns true if a lightweight connectivity probe succeeds. */
    boolean testConnection();

    /** Returns the running tally of writes plus any errors encountered. */
    ExportResult getResult();

    /** Releases the backend connection. {@link AutoCloseable#close()} narrowed to no checked exceptions. */
    @Override
    void close();
}
