package com.uaf.neo4j.plugin.ui.workbench;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Validate mode — skeleton. SHACL validation will run <b>directly from the
 * plugin</b> using the shaded {@code org.apache.jena.shacl} engine, so the
 * MCP server / Python toolchain is not in the critical path. The button and
 * report area are wired but the engine call is stubbed pending the Jena SHACL
 * dependency being added to the shade.
 */
final class ValidateModePanel extends JPanel implements WorkbenchPanel {

    private final UAFWorkbench workbench;
    private final JTextArea report = new JTextArea(
        "No validation run yet.\n\n"
      + "When wired, this panel will:\n"
      + "  1. Pull the live Fuseki dataset (asserted + inferred) via SPARQL CONSTRUCT,\n"
      + "  2. Load ontology/shapes/uaf-shapes.ttl from the plugin's classpath,\n"
      + "  3. Run org.apache.jena.shacl.ShaclValidator and render the violation report here.\n\n"
      + "No MCP / Python dependency — Jena SHACL is shaded into the fat jar alongside the\n"
      + "RDF writer used by the export pipeline.");

    ValidateModePanel(UAFWorkbench workbench) {
        this.workbench = workbench;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        JButton runBtn = new JButton("Run SHACL Validation");
        runBtn.setEnabled(false);   // wired in follow-up
        runBtn.setToolTipText("Pending: pyshacl-free Jena SHACL integration in the fat jar.");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(runBtn);

        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        report.setBackground(new Color(248, 249, 251));
        report.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(report);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(600, 320));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        add(heading("Validate"));
        add(Box.createVerticalStrut(8));
        add(body("Run the SHACL shapes from <code>ontology/shapes/uaf-shapes.ttl</code> against the "
               + "live Fuseki dataset. Report appears below. Runs inside the plugin via the shaded "
               + "Jena engine — no MCP server required."));
        add(Box.createVerticalStrut(18));
        add(buttons);
        add(Box.createVerticalStrut(14));
        add(scroll);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public WorkbenchMode getMode()   { return WorkbenchMode.VALIDATE; }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 18f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel body(String text) {
        JLabel l = new JLabel("<html><body style='width:560px'>" + text + "</body></html>");
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        l.setForeground(new Color(80, 80, 80));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
