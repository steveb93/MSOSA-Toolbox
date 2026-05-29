package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.ExportLog;
import com.uaf.neo4j.plugin.export.ExportResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Post-export dialog with two tabs: a counts/errors summary and the full
 * timestamped log. An "Open Log File" button launches the saved .log file
 * in the system default text editor.
 */
public class ExportSummaryDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(ExportSummaryDialog.class.getName());

    public ExportSummaryDialog(Frame parent, ExportResult result, ExportLog log) {
        super(parent, "UAF Neo4j Export — Complete", true);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Summary", buildSummaryPanel(result));
        tabs.addTab("Log",     buildLogPanel(log));
        if (!result.unmatchedStereotypes.isEmpty()) {
            tabs.addTab("Unmatched Stereotypes (" + result.unmatchedStereotypes.size() + ")",
                        buildUnmatchedPanel(result));
        }
        if (!result.misDomainHints.isEmpty()) {
            tabs.addTab("Mis-Domain Hints (" + result.misDomainHints.size() + ")",
                        buildMisDomainPanel(result));
        }
        if (!result.shaclViolationLines.isEmpty()) {
            int total = result.shaclViolations + result.shaclWarnings;
            tabs.addTab("SHACL Violations (" + total + ")",
                        buildShaclPanel(result));
        }

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(new EmptyBorder(4, 12, 12, 12));

        if (result.hasErrors()) {
            JButton copyBtn = new JButton("Copy Errors");
            copyBtn.addActionListener(e -> copyToClipboard(result.errors));
            buttons.add(copyBtn);
        }

        Path logFile = log.getLogFile();
        if (logFile != null) {
            JButton openLogBtn = new JButton("Open Log File");
            openLogBtn.addActionListener(e -> openFile(logFile));
            buttons.add(openLogBtn);
        }

        // "Browse Graph…" used to launch a separate inspector; the workbench's
        // Inspect rail now hosts the same view, so users navigate via the rail
        // after dismissing this summary.

        JButton copyRefreshBtn = new JButton("Copy SPARQL Refresh Cmd");
        copyRefreshBtn.setToolTipText(
            "Copies the shell command that re-dumps Neo4j → Fuseki for the SPARQL overlay");
        copyRefreshBtn.addActionListener(e -> copyToClipboard(java.util.List.of(
            "python ontology/codegen/dump_to_rdf.py",
            "docker compose -f docker-compose/docker-compose.yml -f docker-compose/docker-compose.fuseki.yml restart fuseki"
        )));
        buttons.add(copyRefreshBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        buttons.add(closeBtn);

        setLayout(new BorderLayout());
        add(tabs,    BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(480, 340));
        setResizable(true);
        setLocationRelativeTo(parent);
    }

    private JPanel buildSummaryPanel(ExportResult result) {
        List<String[]> rowList = new ArrayList<>();
        rowList.add(new String[]{"Nodes written", String.valueOf(result.nodesWritten)});
        if (!result.languageCounts.isEmpty()) {
            result.languageCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> rowList.add(new String[]{"  └ " + e.getKey(), String.valueOf(e.getValue())}));
        }
        rowList.add(new String[]{"Relationships written", String.valueOf(result.relationshipsWritten)});
        rowList.add(new String[]{"INSTANCE_OF links",     String.valueOf(result.instanceLinksWritten)});
        rowList.add(new String[]{"DEFINES links",         String.valueOf(result.definesLinksWritten)});
        rowList.add(new String[]{"Errors",                String.valueOf(result.errors.size())});
        rowList.add(new String[]{"SHACL conformance",     shaclSummary(result)});

        String[][] rows = rowList.toArray(new String[0][]);
        JTable table = new JTable(new DefaultTableModel(rows, new String[]{"Category", "Count"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setPreferredScrollableViewportSize(new Dimension(360, 110));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(12, 12, 4, 12));
        panel.add(new JScrollPane(table), BorderLayout.NORTH);

        if (result.hasErrors()) {
            JTextArea errorArea = new JTextArea(String.join("\n", result.errors), 6, 50);
            errorArea.setEditable(false);
            errorArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            errorArea.setForeground(Color.RED.darker());
            JLabel errLabel = new JLabel("Errors:");
            errLabel.setForeground(Color.RED.darker());
            JPanel errPanel = new JPanel(new BorderLayout(0, 4));
            errPanel.add(errLabel,                    BorderLayout.NORTH);
            errPanel.add(new JScrollPane(errorArea),  BorderLayout.CENTER);
            panel.add(errPanel, BorderLayout.CENTER);
        } else {
            // SPARQL refresh hint (only shown when there are no errors competing for attention)
            JLabel hint = new JLabel(
                "<html><i>Next:</i> the SPARQL view (Fuseki) is now stale. " +
                "Use <b>Copy SPARQL Refresh Cmd</b> below to grab the dump+restart command, " +
                "or run it manually from the repo root.</html>");
            hint.setBorder(new EmptyBorder(4, 4, 4, 4));
            panel.add(hint, BorderLayout.CENTER);
        }

        return panel;
    }

    /**
     * Render the stereotype-drift diagnostic. Each row is a stereotype name that
     * appeared in the model but is not in {@code UAFStereotypeRegistry} — these
     * elements were dropped from the export. Surfacing them here means future
     * profile changes are visible without grepping the export log.
     */
    private JPanel buildUnmatchedPanel(ExportResult result) {
        List<String[]> rowList = new ArrayList<>();
        result.unmatchedStereotypes.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> rowList.add(new String[]{e.getKey(), String.valueOf(e.getValue())}));
        String[][] rows = rowList.toArray(new String[0][]);

        JTable table = new JTable(new DefaultTableModel(rows, new String[]{"Stereotype", "Elements dropped"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setPreferredScrollableViewportSize(new Dimension(420, 160));

        JLabel hint = new JLabel(
            "<html>These stereotype names appeared on model elements but are not in " +
            "<code>UAFStereotypeRegistry</code>. Each affected element was skipped. " +
            "If any of these should be exported, add them to the registry " +
            "(or verify the profile name matches what the MSOSA scripting console reports).</html>");
        hint.setBorder(new EmptyBorder(0, 0, 8, 0));

        JButton copyBtn = new JButton("Copy Names");
        copyBtn.addActionListener(e -> {
            List<String> lines = new ArrayList<>(result.unmatchedStereotypes.size());
            result.unmatchedStereotypes.forEach((k, v) -> lines.add(k + "\t" + v));
            copyToClipboard(lines);
        });
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.add(copyBtn);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.add(hint,                       BorderLayout.NORTH);
        panel.add(new JScrollPane(table),     BorderLayout.CENTER);
        panel.add(buttonRow,                  BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Render the qualifiedName-based mis-domain diagnostic. Each row is a
     * stereotype whose assigned UAF domain disagrees with a domain hinted by
     * its model-path package segment (e.g. an element assigned RESOURCE that
     * sits under {@code …::Operational Taxonomy::…}). Per #125 Part 1 —
     * observability only, the export is unchanged. Modellers use the list to
     * decide whether the element needs re-stereotyping or whether the hint is
     * a false positive.
     */
    private JPanel buildMisDomainPanel(ExportResult result) {
        List<String[]> rowList = new ArrayList<>();
        result.misDomainHints.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> {
                String[] parts = e.getKey().split("\\|", 3);
                String stereo   = parts.length > 0 ? parts[0] : e.getKey();
                String assigned = parts.length > 1 ? parts[1] : "";
                String hinted   = parts.length > 2 ? parts[2] : "";
                rowList.add(new String[]{stereo, assigned, hinted, String.valueOf(e.getValue())});
            });
        String[][] rows = rowList.toArray(new String[0][]);

        JTable table = new JTable(new DefaultTableModel(rows,
                new String[]{"Stereotype", "Assigned domain", "Hinted domain", "Elements"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setPreferredScrollableViewportSize(new Dimension(520, 180));

        JLabel hint = new JLabel(
            "<html>These elements were exported with the domain in <i>Assigned</i>, but their " +
            "<code>qualifiedName</code> path includes a package segment whose leading token " +
            "matches <i>Hinted</i> (e.g. <code>…::Operational Taxonomy::…</code>). " +
            "This is a likely mis-classification — review and re-stereotype on the model side, " +
            "or treat as a false positive when the package name is incidental.</html>");
        hint.setBorder(new EmptyBorder(0, 0, 8, 0));

        JButton copyBtn = new JButton("Copy Rows");
        copyBtn.addActionListener(e -> {
            List<String> lines = new ArrayList<>(result.misDomainHints.size());
            result.misDomainHints.forEach((k, v) -> lines.add(k.replace('|', '\t') + "\t" + v));
            copyToClipboard(lines);
        });
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.add(copyBtn);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.add(hint,                       BorderLayout.NORTH);
        panel.add(new JScrollPane(table),     BorderLayout.CENTER);
        panel.add(buttonRow,                  BorderLayout.SOUTH);
        return panel;
    }

    /**
     * One-cell summary of SHACL conformance for the Category/Count table.
     * Distinguishes "not run" (LPG-only export, or validator failed to load)
     * from "Pass" and "Fail (N)" so users don't read silence as success.
     */
    private String shaclSummary(ExportResult result) {
        if (result.shaclConformance == null) {
            return "N/A (RDF not enabled)";
        }
        if (result.shaclConformance) {
            return result.shaclWarnings == 0
                ? "Pass"
                : "Pass (" + result.shaclWarnings + " warnings)";
        }
        StringBuilder sb = new StringBuilder("Fail (");
        sb.append(result.shaclViolations).append(" violations");
        if (result.shaclWarnings > 0) sb.append(", ").append(result.shaclWarnings).append(" warnings");
        sb.append(")");
        return sb.toString();
    }

    /**
     * Render SHACL violation rows. One row per ValidationReport entry, formatted
     * by {@code ShaclValidationService}. Mirrors {@link #buildUnmatchedPanel} —
     * hint at top, table in the middle, copy-to-clipboard at the bottom.
     */
    private JPanel buildShaclPanel(ExportResult result) {
        String[][] rows = new String[result.shaclViolationLines.size()][1];
        for (int i = 0; i < result.shaclViolationLines.size(); i++) {
            rows[i][0] = result.shaclViolationLines.get(i);
        }
        JTable table = new JTable(new DefaultTableModel(rows, new String[]{"Result"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setPreferredScrollableViewportSize(new Dimension(600, 200));
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        JLabel hint = new JLabel(
            "<html>SHACL validation ran against <code>ontology/shapes/uaf-shapes.ttl</code> " +
            "with OWL FB reasoning, mirroring the standalone " +
            "<code>validate_shacl.py</code> validator. " +
            "<b>Violations</b> are governance failures; <b>warnings</b> are likely " +
            "issues that should be reviewed but do not block.</html>");
        hint.setBorder(new EmptyBorder(0, 0, 8, 0));

        JButton copyBtn = new JButton("Copy Rows");
        copyBtn.addActionListener(e -> copyToClipboard(result.shaclViolationLines));
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.add(copyBtn);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.add(hint,                       BorderLayout.NORTH);
        panel.add(new JScrollPane(table),     BorderLayout.CENTER);
        panel.add(buttonRow,                  BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildLogPanel(ExportLog log) {
        JTextArea logArea = new JTextArea(log.getText(), 14, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setCaretPosition(0);

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        Path logFile = log.getLogFile();
        if (logFile != null) {
            JLabel pathLabel = new JLabel("Saved: " + logFile);
            pathLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            panel.add(pathLabel, BorderLayout.SOUTH);
        }

        return panel;
    }

    private void openFile(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            LOG.warning("Could not open log file: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Could not open log file:\n" + path,
                "UAF Neo4j Export",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private void copyToClipboard(List<String> lines) {
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(String.join("\n", lines)), null);
    }
}
