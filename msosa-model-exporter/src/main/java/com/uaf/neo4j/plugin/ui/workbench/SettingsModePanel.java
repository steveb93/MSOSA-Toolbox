package com.uaf.neo4j.plugin.ui.workbench;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.uaf.neo4j.plugin.rdf.FusekiClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Properties;

/**
 * Settings mode — absorbs the legacy {@code ConnectionDialog}. Holds Neo4j +
 * Fuseki connection details, per-section "Test" buttons, and a single Save
 * that persists via {@link UAFNeo4jPlugin#saveConfig(Properties)} and refreshes
 * the workbench's bottom status strip.
 */
final class SettingsModePanel extends JPanel implements WorkbenchPanel {

    private final UAFWorkbench workbench;

    private final JTextField     uriField;
    private final JTextField     userField;
    private final JPasswordField passwordField;
    private final JTextField     databaseField;
    private final JTextField     batchSizeField;

    private final JTextField     fusekiUrlField;
    private final JTextField     fusekiUserField;
    private final JPasswordField fusekiPasswordField;

    private final JLabel         statusLabel = new JLabel(" ");

    SettingsModePanel(UAFWorkbench workbench) {
        this.workbench = workbench;
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();
        uriField            = new JTextField(cfg.getProperty("neo4j.uri",       "bolt://localhost:7687"), 30);
        userField           = new JTextField(cfg.getProperty("neo4j.user",      "neo4j"), 20);
        passwordField       = new JPasswordField(cfg.getProperty("neo4j.password", ""), 20);
        databaseField       = new JTextField(cfg.getProperty("neo4j.database",  "neo4j"), 15);
        batchSizeField      = new JTextField(cfg.getProperty("neo4j.batch.size", "500"), 8);
        fusekiUrlField      = new JTextField(cfg.getProperty("fuseki.url",      "http://localhost:3030/uaf"), 30);
        fusekiUserField     = new JTextField(cfg.getProperty("fuseki.user",     "admin"), 20);
        fusekiPasswordField = new JPasswordField(cfg.getProperty("fuseki.password", ""), 20);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints lc = labelConstraints();
        GridBagConstraints fc = fieldConstraints();

        int row = 0;
        addHeading(form, "Neo4j (Bolt — system of record)", row++);
        addRow(form, "Bolt URI:",     uriField,       lc, fc, row++);
        addRow(form, "Username:",     userField,      lc, fc, row++);
        addRow(form, "Password:",     passwordField,  lc, fc, row++);
        addRow(form, "Database:",     databaseField,  lc, fc, row++);
        addRow(form, "Batch size:",   batchSizeField, lc, fc, row++);

        addHeading(form, "Fuseki SPARQL (Stage 2 overlay)", row++);
        addRow(form, "Fuseki URL:",     fusekiUrlField,      lc, fc, row++);
        addRow(form, "Fuseki user:",    fusekiUserField,     lc, fc, row++);
        addRow(form, "Fuseki password:", fusekiPasswordField, lc, fc, row++);

        statusLabel.setForeground(new Color(80, 80, 80));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));

        JButton testNeo4j  = new JButton("Test Neo4j");
        JButton testFuseki = new JButton("Test Fuseki");
        JButton save       = new JButton("Save");
        testNeo4j.addActionListener(e -> testNeo4j());
        testFuseki.addActionListener(e -> testFuseki());
        save.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(testNeo4j);
        buttons.add(testFuseki);
        buttons.add(save);

        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.gridy = row; sc.gridwidth = 2;
        sc.anchor = GridBagConstraints.WEST;
        sc.insets = new Insets(14, 0, 0, 0);
        form.add(buttons, sc);

        sc.gridy = ++row;
        sc.insets = new Insets(6, 0, 0, 0);
        form.add(statusLabel, sc);

        setLayout(new java.awt.BorderLayout());
        JPanel scrollHost = new JPanel(new java.awt.BorderLayout());
        scrollHost.setOpaque(false);
        scrollHost.add(form, java.awt.BorderLayout.NORTH);
        add(scrollHost, java.awt.BorderLayout.CENTER);
    }

    // ── Test buttons ────────────────────────────────────────────────────────

    private void testNeo4j() {
        setStatus("Testing Neo4j…", false);
        Properties props = currentProps();
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try (Neo4jExportService svc = new Neo4jExportService(props)) {
                    svc.init();
                    return svc.testConnection();
                } catch (Exception ex) {
                    return false;
                }
            }
            @Override protected void done() {
                try { setStatus(get() ? "Neo4j ✓ connection successful." : "Neo4j ✗ check Bolt URI and credentials.", !get()); }
                catch (Exception ex) { setStatus("Neo4j ✗ " + ex.getMessage(), true); }
            }
        }.execute();
    }

    private void testFuseki() {
        setStatus("Testing Fuseki…", false);
        Properties props = currentProps();
        String url = props.getProperty("fuseki.url", "");
        String user = props.getProperty("fuseki.user", "");
        String pwd = props.getProperty("fuseki.password", "");
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try { return new FusekiClient(url, user, pwd).testConnection(); }
                catch (Exception ex) { return false; }
            }
            @Override protected void done() {
                try { setStatus(get() ? "Fuseki ✓ SPARQL endpoint reachable." : "Fuseki ✗ check URL (e.g. http://host:3030/uaf) and credentials.", !get()); }
                catch (Exception ex) { setStatus("Fuseki ✗ " + ex.getMessage(), true); }
            }
        }.execute();
    }

    private void save() {
        Properties next = currentProps();
        UAFNeo4jPlugin.getInstance().saveConfig(next);
        workbench.getStatusStrip().setConfig(next);
        setStatus("Settings saved.", false);
    }

    private Properties currentProps() {
        Properties p = new Properties();
        p.putAll(UAFNeo4jPlugin.getInstance().getConfig());
        p.setProperty("neo4j.uri",        uriField.getText().trim());
        p.setProperty("neo4j.user",       userField.getText().trim());
        p.setProperty("neo4j.password",   new String(passwordField.getPassword()));
        p.setProperty("neo4j.database",   databaseField.getText().trim());
        p.setProperty("neo4j.batch.size", batchSizeField.getText().trim());
        String fusekiUrl = fusekiUrlField.getText().trim();
        p.setProperty("fuseki.url",       fusekiUrl);
        p.setProperty("fuseki.sparql",    fusekiUrl.endsWith("/sparql") ? fusekiUrl : fusekiUrl + "/sparql");
        p.setProperty("fuseki.user",      fusekiUserField.getText().trim());
        p.setProperty("fuseki.password",  new String(fusekiPasswordField.getPassword()));
        return p;
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setForeground(error ? new Color(180, 30, 30) : new Color(0, 130, 0));
        statusLabel.setText(text);
    }

    @Override public JComponent getComponent() { return this; }
    @Override public WorkbenchMode getMode()   { return WorkbenchMode.SETTINGS; }

    // ── Layout helpers ──────────────────────────────────────────────────────

    private static void addHeading(JPanel panel, String text, int row) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setForeground(new Color(40, 40, 40));
        l.setBorder(BorderFactory.createEmptyBorder(row == 0 ? 0 : 14, 0, 6, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(l, c);
    }

    private static void addRow(JPanel panel, String label, JComponent field,
                               GridBagConstraints lc, GridBagConstraints fc, int row) {
        lc.gridy = row; fc.gridy = row;
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
