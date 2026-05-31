package com.uaf.neo4j.plugin;

import com.nomagic.actions.AMConfigurator;
import com.nomagic.actions.ActionsManager;
import com.nomagic.magicdraw.actions.MDActionsCategory;

/**
 * Injects the "MSOSA Knowledge Graph" sub-menu into the MSOSA Tools menu.
 *
 * <p>Post-refresh menu shape: a single Workbench entry that opens the unified
 * window, plus the external SPARQL launcher and About. The legacy per-feature
 * dialogs (Export wizard, Configure Connection, Graph Inspector) now live as
 * embedded panels inside the workbench and are no longer reached via the menu.
 */
public class UAFExporterActionsConfigurator implements AMConfigurator {

    private static final String TOOLS_MENU_ID = "TOOLS";

    @Override
    public void configure(ActionsManager manager) {
        MDActionsCategory tools = (MDActionsCategory) manager.getActionFor(TOOLS_MENU_ID);
        if (tools == null) {
            return;
        }

        MDActionsCategory uafMenu = new MDActionsCategory(
            "UAF_KG_MENU", "MSOSA Knowledge Graph");
        uafMenu.setNested(true);

        uafMenu.addAction(new OpenWorkbenchAction());
        uafMenu.addAction(new OpenSparqlEndpointAction());
        uafMenu.addAction(new AboutAction());

        tools.addAction(uafMenu);
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
