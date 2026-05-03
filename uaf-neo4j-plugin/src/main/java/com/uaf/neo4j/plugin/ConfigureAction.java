package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;
import com.uaf.neo4j.plugin.ui.ConnectionDialog;

import java.awt.event.ActionEvent;

/**
 * Tools → UAF Neo4j Export → Configure Connection...
 */
public class ConfigureAction extends MDAction {

    public ConfigureAction() {
        super("UAF_NEO4J_CONFIGURE", "Configure Connection...", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new ConnectionDialog(null).setVisible(true);
    }
}
