package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.uaf.neo4j.plugin.ui.ExportConfigDialog;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Tools → UAF Neo4j Export → Export to Neo4j...
 * Opens ExportConfigDialog where the user selects domains, options, and triggers the export.
 */
public class ExportAction extends MDAction {

    public ExportAction() {
        super("UAF_NEO4J_EXPORT", "Export to Neo4j...", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = Application.getInstance().getProject();
        if (project == null) {
            JOptionPane.showMessageDialog(
                null,
                "No project is open. Please open a UAF model before exporting.",
                "UAF Neo4j Export",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        new ExportConfigDialog(null, project).setVisible(true);
    }

    @Override
    public void updateState() {
        setEnabled(Application.getInstance().getProject() != null);
    }
}
