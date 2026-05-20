package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.ExportLog;
import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
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

        JButton browseBtn = new JButton("Browse Graph…");
        browseBtn.setToolTipText("Open the Graph Inspector to explore exported nodes in Neo4j");
        browseBtn.addActionListener(e -> {
            dispose(); // close summary before opening inspector
            UAFNeo4jPlugin plugin = UAFNeo4jPlugin.getInstance();
            if (plugin != null) {
                plugin.showGraphInspector();
            }
        });
        buttons.add(browseBtn);

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
