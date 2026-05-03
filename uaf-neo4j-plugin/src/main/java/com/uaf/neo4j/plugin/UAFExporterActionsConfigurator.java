package com.uaf.neo4j.plugin;

import com.nomagic.actions.AMConfigurator;
import com.nomagic.actions.ActionsManager;
import com.nomagic.magicdraw.actions.MDActionsCategory;

/**
 * Injects the "UAF Neo4j Export" sub-menu into the MSOSA Tools menu.
 */
public class UAFExporterActionsConfigurator implements AMConfigurator {

    private static final String TOOLS_MENU_ID = "TOOLS_MENU";

    @Override
    public void configure(ActionsManager manager) {
        MDActionsCategory tools = (MDActionsCategory) manager.getActionFor(TOOLS_MENU_ID);
        if (tools == null) {
            return;
        }

        MDActionsCategory uafMenu = new MDActionsCategory(
            "UAF_NEO4J_MENU", "UAF Neo4j Export");
        uafMenu.setNested(true);

        uafMenu.addAction(new ExportAction());
        uafMenu.addAction(new ConfigureAction());
        uafMenu.addAction(new AboutAction());

        tools.addAction(uafMenu);
    }

    @Override
    public int getPriority() {
        return AMConfigurator.MEDIUM_PRIORITY;
    }
}
