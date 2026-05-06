package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.ExportLog;
import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFModelTraverser;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;
import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry;
import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry.StereotypeInfo;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService.ExportResult;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class ExportConfigDialog extends JDialog {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color HDR_BG         = new Color( 43,  43,  43);
    private static final Color HDR_TITLE       = Color.WHITE;
    private static final Color HDR_SUBTITLE    = new Color(160, 160, 160);
    private static final Color STATUS_IDLE     = new Color(140, 140, 140);
    private static final Color STATUS_OK       = new Color( 72, 199, 116);
    private static final Color STATUS_FAIL     = new Color(230,  80,  80);
    private static final Color LEFT_BG         = new Color(248, 249, 251);
    private static final Color BORDER_SUBTLE   = new Color(218, 219, 224);

    // ── Header ────────────────────────────────────────────────────────────────
    private final JLabel globalStatusLabel = new JLabel("● Not connected");

    // ── Connection ────────────────────────────────────────────────────────────
    private final JTextField     uriField;
    private final JTextField     userField;
    private final JPasswordField passwordField;
    private final JTextField     databaseField;
    private final JTextField     batchSizeField;
    private final JLabel         connTabStatusLabel;

    // ── Options ───────────────────────────────────────────────────────────────
    private final JCheckBox exportTaggedValuesBox;
    private final JCheckBox exportRelationshipsBox;
    private final JCheckBox exportInstanceLinksBox;

    // ── Package selection ─────────────────────────────────────────────────────
    private final LinkedHashMap<String, JCheckBox> packageBoxes   = new LinkedHashMap<>();
    private final Map<String, Integer>             elementsPerPkg  = new LinkedHashMap<>();
    private final JLabel selectionCountLabel = new JLabel("Counting elements…");

    // ── Log / progress ────────────────────────────────────────────────────────
    private final JTextArea    logArea     = new JTextArea(7, 60);
    private final JProgressBar progressBar = new JProgressBar();

    // ── Buttons ───────────────────────────────────────────────────────────────
    private final JButton saveConfigBtn = new JButton("Save Config");
    private final JButton exportBtn     = new JButton("Export to Neo4j…");
    private final JButton cancelBtn     = new JButton("Cancel");

    private final Project project;

    public ExportConfigDialog(Frame parent, Project project) {
        super(parent, "UAF Neo4j Export", true);
        this.project = project;

        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();

        uriField          = new JTextField(cfg.getProperty("neo4j.uri",      "bolt://localhost:7687"), 36);
        userField         = new JTextField(cfg.getProperty("neo4j.user",     "neo4j"), 24);
        passwordField     = new JPasswordField(cfg.getProperty("neo4j.password", ""), 24);
        databaseField     = new JTextField(cfg.getProperty("neo4j.database", "neo4j"), 18);
        batchSizeField    = new JTextField(cfg.getProperty("neo4j.batch.size", "500"), 8);
        connTabStatusLabel = new JLabel(" ");

        exportTaggedValuesBox = new JCheckBox("Export tagged values",
            Boolean.parseBoolean(cfg.getProperty("export.tagged.values", "true")));
        exportTaggedValuesBox.setToolTipText(
            "<html>Include all UAF tagged values as node properties in Neo4j.<br>" +
            "Properties are written with a <code>tv_</code> prefix (e.g. <code>tv_nationality</code>).<br>" +
            "Uncheck to keep the graph lean when tagged values are not needed.</html>");

        exportRelationshipsBox = new JCheckBox("Export relationships",
            Boolean.parseBoolean(cfg.getProperty("export.relationships", "true")));
        exportRelationshipsBox.setToolTipText(
            "<html>Export directed UAF relationships as Neo4j edges.<br>" +
            "Only relationships whose <i>source</i> element is in a selected<br>" +
            "package are included; cross-package targets are preserved.</html>");

        exportInstanceLinksBox = new JCheckBox("Export INSTANCE_OF metamodel links",
            Boolean.parseBoolean(cfg.getProperty("export.instance.links", "true")));
        exportInstanceLinksBox.setToolTipText(
            "<html>Create <code>:INSTANCE_OF</code> edges from each exported element<br>" +
            "to its <code>:Stereotype</code> node in the pre-existing UAF metamodel graph.<br>" +
            "Requires the metamodel to have been initialised with<br>" +
            "<code>init_uaf_graph.cypher</code> before first export.</html>");

        globalStatusLabel.setForeground(STATUS_IDLE);
        globalStatusLabel.setFont(globalStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        globalStatusLabel.setOpaque(false);

        for (String pkg : topLevelPackageNames()) {
            JCheckBox cb = new JCheckBox(pkg, true);
            cb.setOpaque(false);
            cb.addActionListener(e -> updateSelectionCount());
            packageBoxes.put(pkg, cb);
        }

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        progressBar.setVisible(false);

        saveConfigBtn.addActionListener(e -> saveConfig());
        cancelBtn.addActionListener(e -> dispose());
        exportBtn.addActionListener(e -> runExport());

        setLayout(new BorderLayout());
        add(buildHeader(),      BorderLayout.NORTH);
        add(buildMain(),        BorderLayout.CENTER);
        add(buildSouthPanel(),  BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(960, 620));
        setPreferredSize(new Dimension(1040, 700));
        setResizable(true);
        setLocationRelativeTo(parent);

        loadElementCounts();
    }

    // ── Model scanning ────────────────────────────────────────────────────────

    private List<String> topLevelPackageNames() {
        List<String> names = new ArrayList<>();
        for (Element el : project.getPrimaryModel().getOwnedElement()) {
            if (el instanceof Package && el instanceof NamedElement) {
                String n = ((NamedElement) el).getName();
                if (n != null && !n.isEmpty()) names.add(n);
            }
        }
        return names;
    }

    private void loadElementCounts() {
        new SwingWorker<Map<String, Integer>, Void>() {
            @Override
            protected Map<String, Integer> doInBackground() {
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (UAFElementDTO el : new UAFModelTraverser(project).getElements()) {
                    counts.merge(topPackageOf(el.packageName), 1, Integer::sum);
                }
                return counts;
            }

            @Override
            protected void done() {
                try {
                    Map<String, Integer> counts = get();
                    elementsPerPkg.putAll(counts);
                    for (Map.Entry<String, JCheckBox> e : packageBoxes.entrySet()) {
                        int c = counts.getOrDefault(e.getKey(), 0);
                        e.getValue().setText(e.getKey() + "  (" + c + ")");
                        if (c == 0) e.getValue().setForeground(new Color(160, 160, 160));
                    }
                    updateSelectionCount();
                } catch (Exception ex) {
                    selectionCountLabel.setText("Could not count elements");
                }
            }
        }.execute();
    }

    private static String topPackageOf(String pkgName) {
        int sep = pkgName.indexOf("::");
        return sep < 0 ? pkgName : pkgName.substring(0, sep);
    }

    // ── Panel builders ────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(HDR_BG);
        header.setOpaque(true);
        header.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, new Color(65, 65, 65)),
            new EmptyBorder(16, 20, 15, 20)));

        JLabel title = new JLabel("UAF Neo4j Export");
        title.setForeground(HDR_TITLE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setOpaque(false);

        JLabel subtitle = new JLabel(
            "Select model packages, configure the Neo4j connection, and choose export options.");
        subtitle.setForeground(HDR_SUBTITLE);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
        subtitle.setOpaque(false);

        JPanel textBlock = new JPanel(new GridLayout(2, 1, 0, 4));
        textBlock.setOpaque(false);
        textBlock.add(title);
        textBlock.add(subtitle);

        header.add(textBlock,         BorderLayout.CENTER);
        header.add(globalStatusLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMain() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildPackagesPanel(), buildRightPanel());
        split.setDividerLocation(235);
        split.setBorder(null);

        JPanel main = new JPanel(new BorderLayout());
        main.add(split, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildPackagesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(LEFT_BG);
        panel.setOpaque(true);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 0, 1, BORDER_SUBTLE),
            new EmptyBorder(14, 12, 10, 10)));

        // Heading + All/None
        JLabel heading = new JLabel("Model Packages");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));

        JButton selAll   = smallButton("All");
        JButton deselAll = smallButton("None");
        selAll.addActionListener(e -> {
            packageBoxes.values().forEach(cb -> cb.setSelected(true));
            updateSelectionCount();
        });
        deselAll.addActionListener(e -> {
            packageBoxes.values().forEach(cb -> cb.setSelected(false));
            updateSelectionCount();
        });

        JPanel btnPair = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        btnPair.setOpaque(false);
        btnPair.add(selAll);
        btnPair.add(deselAll);

        JPanel headRow = new JPanel(new BorderLayout(6, 0));
        headRow.setOpaque(false);
        headRow.add(heading, BorderLayout.CENTER);
        headRow.add(btnPair, BorderLayout.EAST);

        // Checkbox list
        JPanel cbPanel = new JPanel();
        cbPanel.setLayout(new BoxLayout(cbPanel, BoxLayout.Y_AXIS));
        cbPanel.setBackground(LEFT_BG);
        cbPanel.setOpaque(true);
        for (JCheckBox cb : packageBoxes.values()) {
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.setBackground(LEFT_BG);
            cbPanel.add(cb);
            cbPanel.add(Box.createVerticalStrut(1));
        }

        JScrollPane scroll = new JScrollPane(cbPanel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER_SUBTLE));
        scroll.getViewport().setBackground(LEFT_BG);

        // Count label
        selectionCountLabel.setFont(selectionCountLabel.getFont().deriveFont(Font.ITALIC, 11f));
        selectionCountLabel.setForeground(new Color(100, 100, 110));

        panel.add(headRow,             BorderLayout.NORTH);
        panel.add(scroll,              BorderLayout.CENTER);
        panel.add(selectionCountLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JTabbedPane buildRightPanel() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(null);
        tabs.addTab("Connection", buildConnectionTab());
        tabs.addTab("Options",    buildOptionsTab());
        tabs.addTab("Preview",    buildPreviewTab());
        return tabs;
    }

    private JPanel buildConnectionTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(18, 18, 8, 18));
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
        bc.insets = new Insets(14, 0, 2, 0);
        panel.add(testBtn, bc);

        bc = (GridBagConstraints) bc.clone();
        bc.gridy = row++;
        bc.insets = new Insets(0, 0, 0, 0);
        panel.add(connTabStatusLabel, bc);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0; filler.gridy = row; filler.weighty = 1.0;
        panel.add(new JPanel(), filler);
        return panel;
    }

    private JPanel buildOptionsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel heading = sectionLabel("Export Options");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(8));

        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(14));

        for (JCheckBox cb : new JCheckBox[]{exportTaggedValuesBox, exportRelationshipsBox, exportInstanceLinksBox}) {
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(cb);
            panel.add(Box.createVerticalStrut(10));
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildPreviewTab() {
        String[] cols = {"Stereotype", "Neo4j Label", "Domain"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Map.Entry<String, StereotypeInfo> e : UAFStereotypeRegistry.getAll().entrySet()) {
            model.addRow(new Object[]{e.getKey(), e.getValue().neo4jLabel, e.getValue().domain.name()});
        }
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel hint = new JLabel("UAF stereotype → Neo4j label reference  (click column headers to sort):");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(hint, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));

        JLabel label = sectionLabel("Export Log:");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER_SUBTLE));
        scroll.setPreferredSize(new Dimension(0, 110));

        panel.add(label,       BorderLayout.NORTH);
        panel.add(scroll,      BorderLayout.CENTER);
        panel.add(progressBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSouthPanel() {
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_SUBTLE),
            new EmptyBorder(8, 14, 12, 14)));
        south.add(buildLogPanel(),  BorderLayout.CENTER);
        south.add(buildButtonBar(), BorderLayout.SOUTH);
        return south;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        bar.add(saveConfigBtn);
        bar.add(cancelBtn);
        bar.add(exportBtn);
        return bar;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void updateSelectionCount() {
        long pkgCount = packageBoxes.values().stream().filter(JCheckBox::isSelected).count();
        if (elementsPerPkg.isEmpty()) {
            selectionCountLabel.setText(pkgCount + "/" + packageBoxes.size() + " packages selected");
        } else {
            int elems = elementsPerPkg.entrySet().stream()
                .filter(e -> { JCheckBox cb = packageBoxes.get(e.getKey()); return cb != null && cb.isSelected(); })
                .mapToInt(Map.Entry::getValue).sum();
            selectionCountLabel.setText(
                pkgCount + "/" + packageBoxes.size() + " packages  ·  ~" + elems + " elements");
        }
    }

    private static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
        return l;
    }

    private static JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setMargin(new Insets(1, 6, 1, 6));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
        return b;
    }

    // ── Connection test ────────────────────────────────────────────────────────

    private void testConnection() {
        connTabStatusLabel.setForeground(Color.DARK_GRAY);
        connTabStatusLabel.setText("Testing…");
        globalStatusLabel.setForeground(STATUS_IDLE);
        globalStatusLabel.setText("● Connecting…");

        final Properties connProps = connectionProps();
        final String uriText = uriField.getText().trim();

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
                    if (ok) {
                        connTabStatusLabel.setForeground(new Color(0, 120, 0));
                        connTabStatusLabel.setText("Connection successful.");
                        globalStatusLabel.setForeground(STATUS_OK);
                        globalStatusLabel.setText("● Connected  ·  Neo4j  ·  " + uriText);
                    } else {
                        connTabStatusLabel.setForeground(STATUS_FAIL);
                        connTabStatusLabel.setText("Connection failed — check URI and credentials.");
                        globalStatusLabel.setForeground(STATUS_FAIL);
                        globalStatusLabel.setText("● Not connected");
                    }
                } catch (Exception ex) {
                    connTabStatusLabel.setForeground(STATUS_FAIL);
                    connTabStatusLabel.setText("Error: " + ex.getMessage());
                    globalStatusLabel.setForeground(STATUS_FAIL);
                    globalStatusLabel.setText("● Not connected");
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
        final Set<String> selectedPackages = packageBoxes.entrySet().stream()
            .filter(e -> e.getValue().isSelected())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        if (selectedPackages.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one model package to export.",
                "UAF Neo4j Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final boolean inclTaggedValues  = exportTaggedValuesBox.isSelected();
        final boolean inclRelationships = exportRelationshipsBox.isSelected();
        final boolean inclInstanceLinks = exportInstanceLinksBox.isSelected();
        final Properties connProps      = connectionProps();
        final ExportLog  log            = new ExportLog(project.getName());

        setButtonsEnabled(false);
        logArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        new SwingWorker<ExportResult, String>() {
            @Override
            protected ExportResult doInBackground() throws Exception {
                publish("Traversing UAF model…");
                UAFModelTraverser traverser = new UAFModelTraverser(project);

                List<UAFElementDTO> elements = traverser.getElements().stream()
                    .filter(el -> selectedPackages.stream().anyMatch(pkg ->
                        el.packageName.equals(pkg) || el.packageName.startsWith(pkg + "::")))
                    .collect(Collectors.toList());

                Set<String> selectedIds = elements.stream()
                    .map(el -> el.id).collect(Collectors.toSet());

                List<UAFRelationshipDTO> relationships = inclRelationships
                    ? traverser.getRelationships().stream()
                        .filter(r -> selectedIds.contains(r.sourceId))
                        .collect(Collectors.toList())
                    : Collections.emptyList();

                publish(String.format("Found %d elements, %d relationships. Connecting to Neo4j…",
                    elements.size(), relationships.size()));

                try (Neo4jExportService svc = new Neo4jExportService(connProps)) {
                    svc.init();
                    publish("Writing nodes…");
                    svc.exportNodes(elements, inclTaggedValues);

                    if (!relationships.isEmpty()) {
                        publish("Writing relationships…");
                        svc.exportRelationships(relationships);
                    }
                    if (inclInstanceLinks) {
                        publish("Linking to UAF metamodel stereotypes…");
                        svc.exportInstanceOfLinks(elements);
                    }
                    publish("Writing system model provenance…");
                    svc.exportSystemModel(traverser.getSystemModelId(), traverser.getSystemModelName());
                    svc.exportDefinesLinks(traverser.getSystemModelId(), elements);
                    return svc.getResult();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) { log.add(msg); appendLog(msg); }
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
        c.insets = new Insets(5, 0, 5, 10);
        return c;
    }

    private static GridBagConstraints fieldGbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(5, 0, 5, 0);
        return c;
    }
}
