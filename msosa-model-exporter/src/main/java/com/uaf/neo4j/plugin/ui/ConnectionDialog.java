package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.uaf.neo4j.plugin.rdf.FusekiClient;

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
    private final JTextField  fusekiUrlField;
    private final JTextField  fusekiUserField;
    private final JPasswordField fusekiPasswordField;
    private final JLabel      statusLabel;

    public ConnectionDialog(Frame parent) {
        super(parent, "UAF Neo4j — Configure Connection", true);

        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();

        uriField      = new JTextField(cfg.getProperty("neo4j.uri",      "bolt://localhost:7687"), 30);
        userField     = new JTextField(cfg.getProperty("neo4j.user",     "neo4j"), 20);
        passwordField = new JPasswordField(cfg.getProperty("neo4j.password", ""), 20);
        databaseField = new JTextField(cfg.getProperty("neo4j.database", "neo4j"), 15);
        batchSizeField = new JTextField(cfg.getProperty("neo4j.batch.size", "500"), 8);
        fusekiUrlField = new JTextField(cfg.getProperty("fuseki.url", "http://localhost:3030/uaf"), 30);
        fusekiUserField = new JTextField(cfg.getProperty("fuseki.user", "admin"), 20);
        fusekiPasswordField = new JPasswordField(cfg.getProperty("fuseki.password", ""), 20);
        statusLabel   = new JLabel(" ");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 4, 12));
        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();

        int row = 0;

        // --- Neo4j (system of record) -------------------------------------------------
        addSeparator(form, "Neo4j (Bolt)", row++);
        addRow(form, "Bolt URI:",      uriField,       lc, fc, row++);
        addRow(form, "Username:",      userField,       lc, fc, row++);
        addRow(form, "Password:",      passwordField,   lc, fc, row++);
        addRow(form, "Database:",      databaseField,   lc, fc, row++);
        addRow(form, "Batch size:",    batchSizeField,  lc, fc, row++);

        // --- Fuseki (SPARQL overlay, Stage 2 ontology) --------------------------------
        addSeparator(form, "Fuseki SPARQL (Stage 2 ontology overlay)", row++);
        addRow(form, "Fuseki URL:",    fusekiUrlField,      lc, fc, row++);
        addRow(form, "Fuseki user:",   fusekiUserField,     lc, fc, row++);
        addRow(form, "Fuseki password:", fusekiPasswordField, lc, fc, row++);

        // Status row
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row; sc.gridwidth = 2;
        sc.insets = new Insets(6, 0, 0, 0);
        sc.anchor = GridBagConstraints.WEST;
        form.add(statusLabel, sc);

        JButton testNeo4jBtn  = new JButton("Test Neo4j");
        JButton testFusekiBtn = new JButton("Test Fuseki");
        JButton saveBtn       = new JButton("Save");
        JButton cancelBtn     = new JButton("Cancel");

        testNeo4jBtn .addActionListener(e -> testNeo4j());
        testFusekiBtn.addActionListener(e -> testFuseki());
        saveBtn      .addActionListener(e -> { save(); dispose(); });
        cancelBtn    .addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBorder(new EmptyBorder(4, 12, 12, 12));
        buttons.add(testNeo4jBtn);
        buttons.add(testFusekiBtn);
        buttons.add(saveBtn);
        buttons.add(cancelBtn);

        setLayout(new BorderLayout());
        add(form,    BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private void testNeo4j() {
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Testing Neo4j…");

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
                    statusLabel.setText(ok ? "Neo4j connection successful." :
                        "Neo4j connection failed — check Bolt URI and credentials.");
                } catch (Exception e) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Neo4j error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void testFuseki() {
        statusLabel.setForeground(Color.DARK_GRAY);
        statusLabel.setText("Testing Fuseki…");

        Properties cfg = currentProps();
        final String url  = cfg.getProperty("fuseki.url",      "");
        final String user = cfg.getProperty("fuseki.user",     "");
        final String pwd  = cfg.getProperty("fuseki.password", "");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    return new FusekiClient(url, user, pwd).testConnection();
                } catch (Exception ex) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    statusLabel.setForeground(ok ? new Color(0, 120, 0) : Color.RED);
                    statusLabel.setText(ok ? "Fuseki SPARQL endpoint reachable." :
                        "Fuseki connection failed — check URL (must include dataset name, e.g. /uaf) and credentials.");
                } catch (Exception e) {
                    statusLabel.setForeground(Color.RED);
                    statusLabel.setText("Fuseki error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void save() {
        UAFNeo4jPlugin.getInstance().saveConfig(currentProps());
    }

    private Properties currentProps() {
        Properties p = new Properties();
        // Preserve any non-form properties (export flags etc.) from the live config.
        p.putAll(UAFNeo4jPlugin.getInstance().getConfig());
        p.setProperty("neo4j.uri",       uriField.getText().trim());
        p.setProperty("neo4j.user",      userField.getText().trim());
        p.setProperty("neo4j.password",  new String(passwordField.getPassword()));
        p.setProperty("neo4j.database",  databaseField.getText().trim());
        p.setProperty("neo4j.batch.size", batchSizeField.getText().trim());
        String fusekiUrl = fusekiUrlField.getText().trim();
        p.setProperty("fuseki.url",      fusekiUrl);
        p.setProperty("fuseki.sparql",   fusekiUrl.endsWith("/sparql") ? fusekiUrl : fusekiUrl + "/sparql");
        p.setProperty("fuseki.user",     fusekiUserField.getText().trim());
        p.setProperty("fuseki.password", new String(fusekiPasswordField.getPassword()));
        return p;
    }

    private static void addSeparator(JPanel panel, String label, int row) {
        JLabel header = new JLabel(label);
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(8, 0, 4, 0);
        panel.add(header, c);
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
