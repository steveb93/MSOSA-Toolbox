package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.neo4j.Neo4jExportService.ExportResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * Post-export summary dialog showing counts and any errors.
 */
public class ExportSummaryDialog extends JDialog {

    public ExportSummaryDialog(Frame parent, ExportResult result) {
        super(parent, "UAF Neo4j Export — Complete", true);

        // Summary table
        String[][] rows = {
            {"Nodes written",          String.valueOf(result.nodesWritten)},
            {"Relationships written",  String.valueOf(result.relationshipsWritten)},
            {"INSTANCE_OF links",      String.valueOf(result.instanceLinksWritten)},
            {"Errors",                 String.valueOf(result.errors.size())}
        };
        String[] cols = {"Category", "Count"};
        JTable table = new JTable(new DefaultTableModel(rows, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        table.setPreferredScrollableViewportSize(new Dimension(360, 80));
        JScrollPane tablePane = new JScrollPane(table);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBorder(new EmptyBorder(12, 12, 4, 12));
        center.add(tablePane, BorderLayout.NORTH);

        // Error list (shown only when there are errors)
        if (result.hasErrors()) {
            JTextArea errorArea = new JTextArea(
                String.join("\n", result.errors), 6, 50);
            errorArea.setEditable(false);
            errorArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            errorArea.setForeground(Color.RED.darker());
            JScrollPane errorPane = new JScrollPane(errorArea);
            JLabel errLabel = new JLabel("Errors:");
            errLabel.setForeground(Color.RED.darker());
            JPanel errPanel = new JPanel(new BorderLayout(0, 4));
            errPanel.add(errLabel,  BorderLayout.NORTH);
            errPanel.add(errorPane, BorderLayout.CENTER);
            center.add(errPanel, BorderLayout.CENTER);
        }

        // Buttons
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(new EmptyBorder(4, 12, 12, 12));

        if (result.hasErrors()) {
            JButton copyBtn = new JButton("Copy Errors");
            copyBtn.addActionListener(e -> copyToClipboard(result.errors));
            buttons.add(copyBtn);
        }
        buttons.add(closeBtn);

        setLayout(new BorderLayout());
        add(center,  BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setResizable(true);
        setLocationRelativeTo(parent);
    }

    private void copyToClipboard(List<String> lines) {
        String text = String.join("\n", lines);
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(text), null);
    }
}
