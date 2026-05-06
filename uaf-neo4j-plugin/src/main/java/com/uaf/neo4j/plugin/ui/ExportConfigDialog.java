package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.ExportLog;
import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFModelTraverser;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;
import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry;
import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry.Domain;
import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry.StereotypeInfo;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService.ExportResult;
import com.nomagic.magicdraw.core.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ExportConfigDialog extends JDialog {

    private final JTextField     uriField;
    private final JTextField     userField;
    private final JPasswordField passwordField;
    private final JTextField     databaseField;
    private final JTextField     batchSizeField;
    private final JLabel         connectionStatusLabel;

    private final JCheckBox exportTaggedValuesBox;
    private final JCheckBox exportRelationshipsBox;
    private final JCheckBox exportInstanceLinksBox;

    private final EnumMap<Domain, JCheckBox> domainBoxes = new EnumMap<>(Domain.class);
    private final JLabel domainCountLabel = new JLabel();

    private final JTextArea    logArea     = new JTextArea(7, 60);
    private final JProgressBar progressBar = new JProgressBar();

    private final JButton saveConfigBtn = new JButton("Save Config");
    private final JButton exportBtn     = new JButton("Export to Neo4j...");
    private final JButton cancelBtn     = new JButton("Cancel");

    private TableRowSorter<DefaultTableModel> previewSorter;

    private final Project project;

    public ExportConfigDialog(Frame parent, Project project) {
        super(parent, "UAF Neo4j — Export", true);
        this.project = project;

        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();

        uriField          = new JTextField(cfg.getProperty("neo4j.uri",      "bolt://localhost:7687"), 32);
        userField         = new JTextField(cfg.getProperty("neo4j.user",     "neo4j"), 20);
        passwordField     = new JPasswordField(cfg.getProperty("neo4j.password", ""), 20);
        databaseField     = new JTextField(cfg.getProperty("neo4j.database", "neo4j"), 15);
        batchSizeField    = new JTextField(cfg.getProperty("neo4j.batch.size", "500"), 8);
        connectionStatusLabel = new JLabel(" ");

        exportTaggedValuesBox  = new JCheckBox("Export tagged values",
            Boolean.parseBoolean(cfg.getProperty("export.tagged.values", "true")));
        exportRelationshipsBox = new JCheckBox("Export relationships",
            Boolean.parseBoolean(cfg.getProperty("export.relationships", "true")));
        exportInstanceLinksBox = new JCheckBox("Export INSTANCE_OF metamodel links",
            Boolean.parseBoolean(cfg.getProperty("export.instance.links", "true")));

        for (Domain d : Domain.values()) {
            JCheckBox cb = new JCheckBox(domainLabel(d), true);
            cb.addActionListener(e -> { updateDomainCount(); refreshPreviewFilter(); });
            domainBoxes.put(d, cb);
        }
        updateDomainCount();

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        progressBar.setVisible(false);

        saveConfigBtn.addActionListener(e -> saveConfig());
        cancelBtn.addActionListener(e -> dispose());
        exportBtn.addActionListener(e -> runExport());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildDomainsPanel(), buildRightPanel());
        split.setDividerLocation(195);
        split.setBorder(new EmptyBorder(8, 8, 0, 8));

        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setBorder(new EmptyBorder(4, 8, 8, 8));
        south.add(buildLogPanel(),   BorderLayout.CENTER);
        south.add(buildButtonBar(),  BorderLayout.SOUTH);

        setLayout(new BorderLayout(0, 0));
        add(split, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(760, 560));
        setResizable(true);
        setLocationRelativeTo(parent);
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private JPanel buildDomainsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("UAF Domains"));

        JButton selAll   = new JButton("All");
        JButton deselAll = new JButton("None");
        selAll.addActionListener(e -> {
            domainBoxes.values().forEach(cb -> cb.setSelected(true));
            updateDomainCount();
            refreshPreviewFilter();
        });
        deselAll.addActionListener(e -> {
            domainBoxes.values().forEach(cb -> cb.setSelected(false));
            updateDomainCount();
            refreshPreviewFilter();
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        btnRow.add(selAll);
        btnRow.add(deselAll);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnRow.getPreferredSize().height));
        panel.add(btnRow);
        panel.add(Box.createVerticalStrut(4));

        for (JCheckBox cb : domainBoxes.values()) {
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(cb);
        }
        panel.add(Box.createVerticalGlue());

        domainCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        domainCountLabel.setFont(domainCountLabel.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(domainCountLabel);
        return panel;
    }

    private JTabbedPane buildRightPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Connection", buildConnectionTab());
        tabs.addTab("Options",    buildOptionsTab());
        tabs.addTab("Preview",    buildPreviewTab());
        return tabs;
    }

    private JPanel buildConnectionTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(12, 12, 4, 12));
        GridBagConstraints lc = labelGbc();
        GridBagConstraints fc = fieldGbc();

        int row = 0;
        addFormRow(panel, "Bolt URI:",   uriField,       lc, fc, row++);
        addFormRow(panel, "Username:",   userField,       lc, fc, row++);
        addFormRow(panel, "Password:",   passwordField,   lc, fc, row++);
        addFormRow(panel, "Database:",   databaseField,   lc, fc, row++);
        addFormRow(panel, "Batch size:", batchSizeField,  lc, fc, row++);

        JButton testBtn = new JButton("Test Connection");
        testBtn.addActionListener(e -> testConnection());

        GridBagConstraints bc = new GridBagConstraints();
        bc.gridx = 0; bc.gridy = row++; bc.gridwidth = 2;
        bc.anchor = GridBagConstraints.WEST;
        bc.insets = new Insets(10, 0, 2, 0);
        panel.add(testBtn, bc);

        bc = (GridBagConstraints) bc.clone();
        bc.gridy = row++;
        bc.insets = new Insets(0, 0, 0, 0);
        panel.add(connectionStatusLabel, bc);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0; filler.gridy = row; filler.weighty = 1.0;
        panel.add(new JPanel(), filler);

        return panel;
    }

    private JPanel buildOptionsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        for (JCheckBox cb : new JCheckBox[]{exportTaggedValuesBox, exportRelationshipsBox, exportInstanceLinksBox}) {
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(cb);
            panel.add(Box.createVerticalStrut(8));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildPreviewTab() {
        String[] cols = {"Stereotype", "Neo4j Label", "Domain"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Map.Entry<String, StereotypeInfo> entry : UAFStereotypeRegistry.getAll().entrySet()) {
            model.addRow(new Object[]{
                entry.getKey(),
                entry.getValue().neo4jLabel,
                entry.getValue().domain.name()
            });
        }

        JTable table = new JTable(model);
        previewSorter = new TableRowSorter<>(model);
        table.setRowSorter(previewSorter);
        refreshPreviewFilter();

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel hint = new JLabel("Stereotypes for selected domains:");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(hint, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        JLabel label = new JLabel("Export Log:");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(0, 110));
        panel.add(label,       BorderLayout.NORTH);
        panel.add(scroll,      BorderLayout.CENTER);
        panel.add(progressBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        bar.add(saveConfigBtn);
        bar.add(cancelBtn);
        bar.add(exportBtn);
        return bar;
    }

    // ── Domain helpers ─────────────────────────────────────────────────────────

    private void updateDomainCount() {
        long n = domainBoxes.values().stream().filter(JCheckBox::isSelected).count();
        domainCountLabel.setText(n + "/" + domainBoxes.size() + " selected");
    }

    private void refreshPreviewFilter() {
        if (previewSorter == null) return;
        final Set<String> active = domainBoxes.entrySet().stream()
            .filter(e -> e.getValue().isSelected())
            .map(e -> e.getKey().name())
            .collect(Collectors.toSet());
        previewSorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                return active.contains(entry.getStringValue(2));
            }
        });
    }

    // ── Connection test ────────────────────────────────────────────────────────

    private void testConnection() {
        connectionStatusLabel.setForeground(Color.DARK_GRAY);
        connectionStatusLabel.setText("Testing...");
        final Properties connProps = connectionProps();
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try (Neo4jExportService svc = new Neo4jExportService(connProps)) {
                    svc.init();
                    return svc.testConnection();
                } catch (Exception ex) { return false; }
            }
            @Override protected void done() {
                try {
                    boolean ok = get();
                    connectionStatusLabel.setForeground(ok ? new Color(0, 120, 0) : Color.RED);
                    connectionStatusLabel.setText(ok
                        ? "Connection successful."
                        : "Connection failed — check URI and credentials.");
                } catch (Exception ex) {
                    connectionStatusLabel.setForeground(Color.RED);
                    connectionStatusLabel.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    private void saveConfig() {
        UAFNeo4jPlugin.getInstance().saveConfig(allProps());
    }

    private Properties connectionProps() {
        Properties p = new Properties();
        p.setProperty("neo4j.uri",        uriField.getText().trim());
        p.setProperty("neo4j.user",       userField.getText().trim());
        p.setProperty("neo4j.password",   new String(passwordField.getPassword()));
        p.setProperty("neo4j.database",   databaseField.getText().trim());
        p.setProperty("neo4j.batch.size", batchSizeField.getText().trim());
        return p;
    }

    private Properties allProps() {
        Properties p = connectionProps();
        p.setProperty("export.tagged.values",  String.valueOf(exportTaggedValuesBox.isSelected()));
        p.setProperty("export.relationships",  String.valueOf(exportRelationshipsBox.isSelected()));
        p.setProperty("export.instance.links", String.valueOf(exportInstanceLinksBox.isSelected()));
        return p;
    }

    // ── Export ─────────────────────────────────────────────────────────────────

    private void runExport() {
        final Set<String> selectedDomains = domainBoxes.entrySet().stream()
            .filter(e -> e.getValue().isSelected())
            .map(e -> e.getKey().name())
            .collect(Collectors.toSet());

        if (selectedDomains.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one UAF domain to export.",
                "UAF Neo4j Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final boolean inclTaggedValues  = exportTaggedValuesBox.isSelected();
        final boolean inclRelationships = exportRelationshipsBox.isSelected();
        final boolean inclInstanceLinks = exportInstanceLinksBox.isSelected();
        final Properties connProps = connectionProps();
        final ExportLog log = new ExportLog(project.getName());

        setButtonsEnabled(false);
        logArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        new SwingWorker<ExportResult, String>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                publish("Traversing UAF model...");
                UAFModelTraverser traverser = new UAFModelTraverser(project);

                List<UAFElementDTO> elements = traverser.getElements().stream()
                    .filter(el -> selectedDomains.contains(el.domain))
                    .collect(Collectors.toList());

                List<UAFRelationshipDTO> relationships = inclRelationships
                    ? traverser.getRelationships().stream()
                        .filter(r -> selectedDomains.contains(r.domain))
                        .collect(Collectors.toList())
                    : Collections.emptyList();

                publish(String.format("Found %d elements, %d relationships. Connecting to Neo4j...",
                    elements.size(), relationships.size()));

                try (Neo4jExportService svc = new Neo4jExportService(connProps)) {
                    svc.init();

                    publish("Writing nodes...");
                    svc.exportNodes(elements, inclTaggedValues);

                    if (!relationships.isEmpty()) {
                        publish("Writing relationships...");
                        svc.exportRelationships(relationships);
                    }

                    if (inclInstanceLinks) {
                        publish("Linking to UAF metamodel stereotypes...");
                        svc.exportInstanceOfLinks(elements);
                    }

                    publish("Writing system model provenance...");
                    svc.exportSystemModel(traverser.getSystemModelId(), traverser.getSystemModelName());
                    svc.exportDefinesLinks(traverser.getSystemModelId(), elements);

                    return svc.getResult();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    log.add(msg);
                    appendLog(msg);
                }
            }

            @Override
            protected void done() {
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                setButtonsEnabled(true);
                try {
                    ExportResult result = get();
                    log.finish(result);
                    appendLog("Export complete — " + result.nodesWritten + " nodes, "
                        + result.relationshipsWritten + " relationships.");
                    new ExportSummaryDialog(null, result, log).setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log.finishWithException(cause.getMessage());
                    appendLog("ERROR: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void appendLog(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void setButtonsEnabled(boolean enabled) {
        saveConfigBtn.setEnabled(enabled);
        exportBtn.setEnabled(enabled);
        cancelBtn.setEnabled(enabled);
    }

    // ── Form helpers ──────────────────────────────────────────────────────────

    private static String domainLabel(Domain d) {
        switch (d) {
            case STRATEGIC:   return "Strategic (StV)";
            case OPERATIONAL: return "Operational (OV)";
            case RESOURCE:    return "Resource (RsV)";
            case SERVICE:     return "Service (SvcV)";
            case PERSONNEL:   return "Personnel (PrV)";
            case ACQUISITION: return "Acquisition (AcV)";
            case SECURITY:    return "Security (SrV)";
            case SHARED:      return "Shared";
            default:          return d.name();
        }
    }

    private static void addFormRow(JPanel panel, String label, JComponent field,
                                   GridBagConstraints lc, GridBagConstraints fc, int row) {
        lc.gridy = row;
        fc.gridy = row;
        panel.add(new JLabel(label), lc);
        panel.add(field, fc);
    }

    private static GridBagConstraints labelGbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(4, 0, 4, 8);
        return c;
    }

    private static GridBagConstraints fieldGbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(4, 0, 4, 0);
        return c;
    }
}
