package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.ExportLog;
import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService.ExportResult;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
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
            UAFNeo4jPlugin plugin = UAFNeo4jPlugin.getInstance();
            Project project = Application.getInstance().getProject();
            new GraphInspectorDialog(
                parent,
                plugin != null ? plugin.getConfig() : new java.util.Properties(),
                project
            ).setVisible(true);
        });
        buttons.add(browseBtn);

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
        String[][] rows = {
            {"Nodes written",          String.valueOf(result.nodesWritten)},
            {"Relationships written",  String.valueOf(result.relationshipsWritten)},
            {"INSTANCE_OF links",      String.valueOf(result.instanceLinksWritten)},
            {"DEFINES links",          String.valueOf(result.definesLinksWritten)},
            {"Errors",                 String.valueOf(result.errors.size())}
        };
        JTable table = new JTable(new DefaultTableModel(rows, new String[]{"Category", "Count"}) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setPreferredScrollableViewportSize(new Dimension(360, 80));

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
        }

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
