package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;

import java.awt.event.ActionEvent;

/**
 * Tools → MSOSA Knowledge Graph → Open Workbench…
 *
 * <p>Single-window entry point. The per-feature dialogs (Export wizard,
 * Configure Connection, Graph Inspector) are embedded directly as workbench
 * panels — the menu no longer surfaces them separately.
 */
public class OpenWorkbenchAction extends MDAction {

    public OpenWorkbenchAction() {
        super("UAF_KG_WORKBENCH", "Open Workbench…", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        UAFNeo4jPlugin plugin = UAFNeo4jPlugin.getInstance();
        if (plugin != null) {
            plugin.showWorkbench();
        }
    }

    @Override
    public void updateState() {
        setEnabled(true); // workbench is useful even without an open project
    }
}
