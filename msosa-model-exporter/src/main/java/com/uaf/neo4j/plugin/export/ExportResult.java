package com.uaf.neo4j.plugin.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable tally returned by {@link ExportService#getResult()}. Implementations update the
 * counters as batches complete and append a one-line message to {@link #errors} when a batch
 * fails. The {@link ExportSummaryDialog} renders the result after the pipeline finishes.
 *
 * Promoted out of {@code Neo4jExportService} so both LPG and RDF emitters share a single
 * counter shape — {@code ExportSummaryDialog} reads one result regardless of backend.
 */
public final class ExportResult {

    public int nodesWritten         = 0;
    public int relationshipsWritten = 0;
    public int instanceLinksWritten = 0;
    public int definesLinksWritten  = 0;

    public final List<String>         errors         = new ArrayList<>();
    public final Map<String, Integer> languageCounts = new LinkedHashMap<>();

    /**
     * Stereotype names that appeared on elements in the source model but did not
     * resolve to any {@link com.uaf.neo4j.plugin.model.UAFStereotypeRegistry} entry
     * — neither directly nor via their inheritance chain. Each value is the count
     * of distinct elements that carried that stereotype name and were dropped.
     *
     * Populated by {@link com.uaf.neo4j.plugin.model.UAFModelTraverser#getUnmatchedStereotypes()};
     * surfaced by the summary dialog so profile drift becomes visible (#75).
     */
    public final Map<String, Integer> unmatchedStereotypes = new LinkedHashMap<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
