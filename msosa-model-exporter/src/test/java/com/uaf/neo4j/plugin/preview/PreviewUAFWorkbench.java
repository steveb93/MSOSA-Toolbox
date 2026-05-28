package com.uaf.neo4j.plugin.preview;

import com.nomagic.magicdraw.core.Project;
import com.uaf.neo4j.plugin.ui.ExportConfigDialog;
import com.uaf.neo4j.plugin.ui.GraphInspectorDialog;
import com.uaf.neo4j.plugin.ui.workbench.UAFWorkbench;

import java.util.Properties;

/**
 * Workbench subclass for the UI preview harness. Overrides the dialog
 * factories so the Export and Inspect rails render against canned sample data
 * instead of trying to read a live MSOSA project / Neo4j connection.
 *
 * <p>Use case: visual iteration on the embedded forms outside MSOSA. The
 * production {@link UAFWorkbench} stays untouched — it instantiates the real
 * dialogs and shows the "no project" notice when none is open.
 */
public final class PreviewUAFWorkbench extends UAFWorkbench {

    public PreviewUAFWorkbench() {
        super(null);
    }

    @Override
    protected ExportConfigDialog createExportDialog(Project project) {
        return new PreviewExportConfigDialog();
    }

    @Override
    protected GraphInspectorDialog createInspectorDialog(Properties config, Project project) {
        return new PreviewGraphInspectorDialog(config);
    }
}
