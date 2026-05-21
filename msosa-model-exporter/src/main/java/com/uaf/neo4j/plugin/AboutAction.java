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
            "<html><b>UAF Neo4j Export Plugin v0.5.0</b><br><br>" +
            "Exports UAF 1.2, SysML 1.6 and BPMN 2.0 elements and relationships<br>" +
            "from MSOSA 2022x into a Neo4j knowledge graph via the Bolt protocol.<br><br>" +
            "<b>Neo4j (system of record):</b> bolt://localhost:7687 (Docker)<br>" +
            "<b>SPARQL overlay (Stage 2):</b> Apache Jena Fuseki at http://localhost:3030/uaf<br>" +
            "<br>" +
            "Refresh the SPARQL view after each export by running<br>" +
            "<code>python ontology/codegen/dump_to_rdf.py</code> from the repo root,<br>" +
            "then restart the <code>fuseki-uaf</code> container.<br>" +
            "<br>" +
            "See <code>Ontology-Approach-to-Knowledge.md</code> for the strategy<br>" +
            "and <code>ontology/NEXT-STEPS.md</code> for the migration roadmap.</html>",
            "UAF Neo4j Export Plugin",
            JOptionPane.INFORMATION_MESSAGE);
    }
}
