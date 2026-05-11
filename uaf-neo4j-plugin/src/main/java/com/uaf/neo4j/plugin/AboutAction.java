package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Tools → UAF Neo4j Export → About
 */
public class AboutAction extends MDAction {

    public AboutAction() {
        super("UAF_NEO4J_ABOUT", "About...", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JOptionPane.showMessageDialog(
            null,
            "<html><b>UAF Neo4j Export Plugin v0.4.0</b><br><br>" +
            "Exports UAF 1.2 elements and relationships from MSOSA 2022x<br>" +
            "into a Neo4j knowledge graph via the Bolt protocol.<br><br>" +
            "Target: bolt://localhost:7687 (Docker container)<br>" +
            "UAF version: 1.2 | Neo4j driver: 5.x</html>",
            "UAF Neo4j Export Plugin",
            JOptionPane.INFORMATION_MESSAGE);
    }
}
