package com.uaf.neo4j.plugin;

import com.nomagic.magicdraw.actions.MDAction;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Tools → UAF Knowledge Graph → About
 */
public class AboutAction extends MDAction {

    public AboutAction() {
        super("UAF_KG_ABOUT", "About…", null, null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JOptionPane.showMessageDialog(
            null,
            "<html><b>UAF Knowledge Graph</b><br><br>" +
            "Exports UAF 1.2, SysML 1.6 and BPMN 2.0 elements and relationships<br>" +
            "from MSOSA 2022x into a Neo4j knowledge graph (LPG via Bolt) and an<br>" +
            "Apache Jena Fuseki SPARQL endpoint.<br><br>" +
            "<b>Neo4j (system of record):</b> bolt://localhost:7687 (Docker)<br>" +
            "<b>SPARQL overlay:</b> Apache Jena Fuseki at http://localhost:3030/uaf<br>" +
            "<br>" +
            "The plugin writes RDF Turtle directly and optionally PUTs to Fuseki's<br>" +
            "Graph Store Protocol endpoint, removing the manual restart step.<br>" +
            "<br>" +
            "See <code>Ontology-Approach-to-Knowledge.md</code> for the strategy<br>" +
            "and <code>ontology/NEXT-STEPS.md</code> for the migration roadmap.</html>",
            "About — UAF Knowledge Graph",
            JOptionPane.INFORMATION_MESSAGE);
    }
}
