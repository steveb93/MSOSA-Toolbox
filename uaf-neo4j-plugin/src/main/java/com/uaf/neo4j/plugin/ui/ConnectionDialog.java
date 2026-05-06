package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Properties;

/**
 * Modal dialog for editing and testing Neo4j connection settings.
 */
public class ConnectionDialog extends JDialog {

    private final JTextField  uriField;
    private final JTextField  userField;
    private final JPasswordField passwordField;
    private final JTextField  databaseField;
    private final JTextField  batchSizeField;
    private final JLabel      statusLabel;

    public ConnectionDialog(Frame parent) {
        super(parent, "UAF Neo4j — Configure Connection", true);

        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();

        uriField      = new JTextField(cfg.getProperty("neo4j.uri",      "bolt://localhost:7687"), 30);
        userField     = new JTextField(cfg.getProperty("neo4j.user",     "neo4j"), 20);
        passwordField = new JPasswordField(cfg.getProperty("neo4j.password", ""), 20);
        databaseField = new JTextField(cfg.getProperty("neo4j.database", "neo4j"), 15);
        batchSizeField = new JTextField(cfg.getProperty("neo4j.batch.size", "500"), 8);
        statusLabel   = new JLabel(" ");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 4, 12));
        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();

        int row = 0;
        addRow(form, "Bolt URI:",      uriField,       lc, fc, row++);
        addRow(form, "Username:",      userField,       lc, fc, row++);
        addRow(form, "Password:",      passwordField,   lc, fc, row++);
        addRow(form, "Database:",      databaseField,   lc, fc, row++);
        addRow(form, "Batch size:",    batchSizeField,  lc, fc, row++);

        // Status row
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row; sc.gridwidth = 2;
        sc.insets = new Insets(6, 0, 0, 0);
        sc.anchor = GridBagConstraints.WEST;
        form.add(statusLabel, sc);

        JButton testBtn   = new JButton("Test Connection");
        JButton saveBtn   = new JButton("Save");
        JButton cancelBtn = new JButton("Cancel");

        testBtn.addActionListener(e -> testConnection());
        saveBtn.addActionListener(e -> { save(); dispose(); });
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(new EmptyBorder(4, 12, 12, 12));
        buttons.add(testBtn);
        buttons.add(saveBtn);
        buttons.add(cancelBtn);

        setLayout(new BorderLayout());
        add(form,    BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void testConnection() {
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Testing...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try (Neo4jExportService svc = new Neo4jExportService(currentProps())) {
                    svc.init();
                    return svc.testConnection();
                } catch (Exception ex) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    statusLabel.setForeground(ok ? new Color(0, 120, 0) : Color.RED);
                    statusLabel.setText(ok ? "Connection successful." : "Connection failed — check URI and credentials.");
                } catch (Exception e) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void save() {
        UAFNeo4jPlugin.getInstance().saveConfig(currentProps());
    }

    private Properties currentProps() {
        Properties p = new Properties();
        p.setProperty("neo4j.uri",       uriField.getText().trim());
        p.setProperty("neo4j.user",      userField.getText().trim());
        p.setProperty("neo4j.password",  new String(passwordField.getPassword()));
        p.setProperty("neo4j.database",  databaseField.getText().trim());
        p.setProperty("neo4j.batch.size", batchSizeField.getText().trim());
        return p;
    }

    // -------------------------------------------------------------------------

    private static void addRow(JPanel panel, String label, JComponent field,
                               GridBagConstraints lc, GridBagConstraints fc, int row) {
        lc.gridy = row;
        fc.gridy = row;
        panel.add(new JLabel(label), lc);
        panel.add(field, fc);
    }

    private static GridBagConstraints labelConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(4, 0, 4, 8);
        return c;
    }

    private static GridBagConstraints fieldConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1; c.anchor = GridBagConstraints.WEST;
        c.fill  = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        return c;
    }
}
