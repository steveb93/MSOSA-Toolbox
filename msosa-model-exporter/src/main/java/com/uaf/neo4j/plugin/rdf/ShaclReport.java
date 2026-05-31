package com.uaf.neo4j.plugin.rdf;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of {@link ShaclValidationService#validate}. Carries the SHACL verdict and
 * formatted violation lines so the Validate rail can render them without depending on
 * Jena types.
 *
 * <p>Promoted out of {@code ExportResult}: SHACL is no longer part of the export
 * pipeline (running OWL FB + SHACL inside {@code RDFExportService.close()} was a
 * many-minute regression on real models). The Validate rail is the single SHACL
 * entry point and owns this type.
 */
public final class ShaclReport {

    /** {@code true} = no Violations; {@code false} = at least one; {@code null} = validator did not run. */
    public Boolean conforms;

    /** Count of entries with severity {@code sh:Violation}. */
    public int violations;

    /** Count of entries with severity {@code sh:Warning}. Warnings do not flip {@link #conforms} to false. */
    public int warnings;

    /** One formatted line per ValidationReport entry — {@code "[Severity] ShapeName: focus — message"}. */
    public final List<String> lines = new ArrayList<>();

    /** Non-empty when the validator failed to run (e.g. missing ontology resources). */
    public final List<String> errors = new ArrayList<>();
}
