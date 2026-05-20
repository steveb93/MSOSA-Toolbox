package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.ExportLog;
import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.model.UAFElementDTO;
import com.uaf.neo4j.plugin.model.UAFModelTraverser;
import com.uaf.neo4j.plugin.model.UAFRelationshipDTO;
import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry;
import com.uaf.neo4j.plugin.model.UAFStereotypeRegistry.StereotypeInfo;
import com.uaf.neo4j.plugin.export.ExportResult;
import com.uaf.neo4j.plugin.export.ExportService;
import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.uaf.neo4j.plugin.rdf.RDFExportService;
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
    private static final Color HDR_BG       = new Color( 43,  43,  43);
    private static final Color HDR_TITLE    = Color.WHITE;
    private static final Color HDR_SUBTITLE = new Color(160, 160, 160);
    private static final Color STATUS_IDLE  = new Color(140, 140, 140);
    private static final Color STATUS_OK    = new Color( 72, 199, 116);
    private static final Color STATUS_FAIL  = new Color(230,  80,  80);
    private static final Color LEFT_BG      = new Color(248, 249, 251);
    private static final Color BORDER_SUBTLE= new Color(218, 219, 224);

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
    private final JCheckBox exportUAFBox;
    private final JCheckBox exportSysMLBox;
    private final JCheckBox exportBPMNBox;

    // ── Targets ───────────────────────────────────────────────────────────────
    private final JCheckBox  targetLpgBox;
    private final JCheckBox  targetRdfBox;
    private final JCheckBox  fusekiPushBox;
    private final JTextField rdfOutputPathField;

    // ── Package selection ─────────────────────────────────────────────────────
    private final LinkedHashMap<String, JCheckBox> packageBoxes  = new LinkedHashMap<>();
    private final Map<String, Integer>             elementsPerPkg = new LinkedHashMap<>();
    private final JLabel selectionCountLabel = new JLabel("Counting elements…");

    // ── Log / progress ────────────────────────────────────────────────────────
    private final JTextArea    logArea     = new JTextArea(9, 60);
    private final JProgressBar progressBar = new JProgressBar();

    // ── Buttons ───────────────────────────────────────────────────────────────
    private final JButton saveConfigBtn  = new JButton("Save Config");
    private final JButton exportBtn      = new JButton("Export to Neo4j…");
    private final JButton cancelBtn      = new JButton("Cancel");
    private final JButton browseGraphBtn = new JButton("Browse Graph…");

    private final Project project;

    public ExportConfigDialog(Frame parent, Project project) {
        super(parent, "UAF Neo4j Export", true);
        this.project = project;

        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();

        uriField           = new JTextField(cfg.getProperty("neo4j.uri",      "bolt://localhost:7687"), 36);
        userField          = new JTextField(cfg.getProperty("neo4j.user",     "neo4j"), 24);
        passwordField      = new JPasswordField(cfg.getProperty("neo4j.password", ""), 24);
        databaseField      = new JTextField(cfg.getProperty("neo4j.database", "neo4j"), 18);
        batchSizeField     = new JTextField(cfg.getProperty("neo4j.batch.size", "500"), 8);
        connTabStatusLabel = new JLabel(" ");

        exportTaggedValuesBox = new JCheckBox("Export tagged values",
            Boolean.parseBoolean(cfg.getProperty("export.tagged.values", "true")));
        exportTaggedValuesBox.setToolTipText(
            "<html>Include all tagged values as node properties in Neo4j.<br>" +
            "Properties are written with a <code>tv_</code> prefix (e.g. <code>tv_nationality</code>).<br>" +
            "Uncheck to keep the graph lean when tagged values are not needed.</html>");

        exportRelationshipsBox = new JCheckBox("Export relationships",
            Boolean.parseBoolean(cfg.getProperty("export.relationships", "true")));
        exportRelationshipsBox.setToolTipText(
            "<html>Export directed relationships as Neo4j edges.<br>" +
            "Only relationships whose <i>source</i> element is in a selected<br>" +
            "package are included; cross-package targets are preserved.</html>");

        exportInstanceLinksBox = new JCheckBox("Export INSTANCE_OF metamodel links",
            Boolean.parseBoolean(cfg.getProperty("export.instance.links", "true")));
        exportInstanceLinksBox.setToolTipText(
            "<html>Create <code>:INSTANCE_OF</code> edges from each exported element<br>" +
            "to its <code>:Stereotype</code> node in the pre-existing metamodel graph.<br>" +
            "Requires the metamodel to have been initialised with<br>" +
            "<code>init_uaf_graph.cypher</code> before first export.</html>");

        exportUAFBox   = new JCheckBox("UAF 1.2",
            Boolean.parseBoolean(cfg.getProperty("export.language.uaf",   "true")));
        exportSysMLBox = new JCheckBox("SysML 1.6",
            Boolean.parseBoolean(cfg.getProperty("export.language.sysml", "true")));
        exportBPMNBox  = new JCheckBox("BPMN 2.0",
            Boolean.parseBoolean(cfg.getProperty("export.language.bpmn",  "true")));

        targetLpgBox = new JCheckBox("Neo4j (LPG via Cypher)",
            Boolean.parseBoolean(cfg.getProperty("export.target.lpg", "true")));
        targetLpgBox.setToolTipText(
            "<html>Write the traversed model to Neo4j over Bolt as Cypher MERGE statements.<br>" +
            "This is the system of record. Connection settings live under the <i>Connection</i> tab.</html>");

        targetRdfBox = new JCheckBox("RDF Turtle file (and optionally PUT to Fuseki)",
            Boolean.parseBoolean(cfg.getProperty("export.target.rdf", "false")));
        targetRdfBox.setToolTipText(
            "<html>Write the traversed model as an RDF Turtle dump to the path below.<br>" +
            "If <i>Also PUT to Fuseki</i> is ticked, the same TTL bytes are pushed to the configured<br>" +
            "Fuseki dataset via SPARQL 1.1 Graph Store Protocol — refreshing the SPARQL view in one step.</html>");

        fusekiPushBox = new JCheckBox("Also PUT to Fuseki /data endpoint",
            Boolean.parseBoolean(cfg.getProperty("fuseki.push.enabled", "false")));
        fusekiPushBox.setToolTipText(
            "<html>Push the generated Turtle to the configured Fuseki dataset.<br>" +
            "Fuseki URL and credentials live under the <i>Connection</i> tab.<br>" +
            "Removes the need for the manual <code>dump_to_rdf.py</code> + restart step.</html>");

        rdfOutputPathField = new JTextField(cfg.getProperty("rdf.output.path",
            System.getProperty("user.home") + "/uaf-instance.ttl"), 36);
        rdfOutputPathField.setToolTipText(
            "<html>Where the Turtle dump is written. Absolute path recommended — the plugin's working " +
            "directory is MSOSA's install dir, so relative paths land in unexpected places.</html>");

        // Sub-options of the RDF target follow its enabled state
        java.awt.event.ItemListener rdfToggle = e -> {
            boolean on = targetRdfBox.isSelected();
            fusekiPushBox.setEnabled(on);
            rdfOutputPathField.setEnabled(on);
        };
        targetRdfBox.addItemListener(rdfToggle);
        rdfToggle.itemStateChanged(null); // sync initial state

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

        browseGraphBtn.setToolTipText("Open the Graph Inspector to explore nodes in Neo4j");
        browseGraphBtn.addActionListener(e -> {
            UAFNeo4jPlugin plugin = UAFNeo4jPlugin.getInstance();
            if (plugin != null) plugin.showGraphInspector();
        });

        setLayout(new BorderLayout());
        add(buildHeader(),     BorderLayout.NORTH);
        add(buildMain(),       BorderLayout.CENTER);
        add(buildSouthPanel(), BorderLayout.SOUTH);

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
        addFormRow(panel, "Bolt URI:",   uriField,      lc, fc, row++);
        addFormRow(panel, "Username:",   userField,      lc, fc, row++);
        addFormRow(panel, "Password:",   passwordField,  lc, fc, row++);
        addFormRow(panel, "Database:",   databaseField,  lc, fc, row++);
        addFormRow(panel, "Batch size:", batchSizeField, lc, fc, row++);

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

        // ── Export Targets ────────────────────────────────────────────────────
        JLabel targetsHeading = sectionLabel("Export Targets");
        targetsHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(targetsHeading);
        panel.add(Box.createVerticalStrut(8));

        JSeparator sep0 = new JSeparator();
        sep0.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep0.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(sep0);
        panel.add(Box.createVerticalStrut(10));

        targetLpgBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(targetLpgBox);
        panel.add(Box.createVerticalStrut(8));

        targetRdfBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(targetRdfBox);
        panel.add(Box.createVerticalStrut(4));

        // Indented RDF sub-options
        JPanel rdfSub = new JPanel();
        rdfSub.setLayout(new BoxLayout(rdfSub, BoxLayout.Y_AXIS));
        rdfSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        rdfSub.setBorder(new EmptyBorder(0, 24, 0, 0));

        JPanel pathRow = new JPanel(new BorderLayout(8, 0));
        pathRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel pathLbl = new JLabel("Turtle output:");
        pathRow.add(pathLbl, BorderLayout.WEST);
        pathRow.add(rdfOutputPathField, BorderLayout.CENTER);
        rdfSub.add(pathRow);
        rdfSub.add(Box.createVerticalStrut(6));

        fusekiPushBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        rdfSub.add(fusekiPushBox);

        panel.add(rdfSub);
        panel.add(Box.createVerticalStrut(14));

        // ── Export Data ───────────────────────────────────────────────────────
        JLabel dataHeading = sectionLabel("Export Data");
        dataHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(dataHeading);
        panel.add(Box.createVerticalStrut(8));

        JSeparator sep1 = new JSeparator();
        sep1.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(sep1);
        panel.add(Box.createVerticalStrut(10));

        for (JCheckBox cb : new JCheckBox[]{exportTaggedValuesBox, exportRelationshipsBox, exportInstanceLinksBox}) {
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(cb);
            panel.add(Box.createVerticalStrut(8));
        }

        // ── Modelling Languages ───────────────────────────────────────────────
        panel.add(Box.createVerticalStrut(12));

        JLabel langHeading = sectionLabel("Modelling Languages");
        langHeading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(langHeading);
        panel.add(Box.createVerticalStrut(8));

        JSeparator sep2 = new JSeparator();
        sep2.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(sep2);
        panel.add(Box.createVerticalStrut(10));

        for (JCheckBox cb : new JCheckBox[]{exportUAFBox, exportSysMLBox, exportBPMNBox}) {
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(cb);
            panel.add(Box.createVerticalStrut(8));
        }

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JPanel buildPreviewTab() {
        String[] cols = {"Stereotype", "Neo4j Label", "Domain", "Language"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Map.Entry<String, StereotypeInfo> e : UAFStereotypeRegistry.getAll().entrySet()) {
            StereotypeInfo info = e.getValue();
            String domainStr   = info.domain   != null ? info.domain.name() : "—";
            String languageStr = info.language != null ? info.language      : "UAF";
            model.addRow(new Object[]{e.getKey(), info.neo4jLabel, domainStr, languageStr});
        }
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.getColumnModel().getColumn(3).setPreferredWidth(70);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel hint = new JLabel(
            "Stereotype → Neo4j label mapping for all registered modelling languages  (click column headers to sort):");
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
        scroll.setPreferredSize(new Dimension(0, 130));

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
        JPanel bar = new JPanel(new BorderLayout());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.add(browseGraphBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        right.add(saveConfigBtn);
        right.add(cancelBtn);
        right.add(exportBtn);

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    // ── Connection test ───────────────────────────────────────────────────────

    private void testConnection() {
        connTabStatusLabel.setForeground(Color.DARK_GRAY);
        connTabStatusLabel.setText("Testing…");
        globalStatusLabel.setForeground(STATUS_IDLE);
        globalStatusLabel.setText("● Connecting…");

        final Properties connProps = connectionProps();
        final String uriText = uriField.getText().trim();

        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try (ExportService svc = new Neo4jExportService(connProps)) {
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

    // ── Config ────────────────────────────────────────────────────────────────

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
        // Start from the plugin's saved config so fuseki.* and other ConnectionDialog-managed
        // keys flow through to RDFExportService. The Save Config button persists the
        // overrides; runExport() also uses this result directly so the live UI state always
        // wins over stale on-disk values for the duration of the export.
        Properties p = new Properties();
        p.putAll(UAFNeo4jPlugin.getInstance().getConfig());
        p.putAll(connectionProps());
        p.setProperty("export.tagged.values",  String.valueOf(exportTaggedValuesBox.isSelected()));
        p.setProperty("export.relationships",  String.valueOf(exportRelationshipsBox.isSelected()));
        p.setProperty("export.instance.links", String.valueOf(exportInstanceLinksBox.isSelected()));
        p.setProperty("export.language.uaf",   String.valueOf(exportUAFBox.isSelected()));
        p.setProperty("export.language.sysml", String.valueOf(exportSysMLBox.isSelected()));
        p.setProperty("export.language.bpmn",  String.valueOf(exportBPMNBox.isSelected()));
        p.setProperty("export.target.lpg",     String.valueOf(targetLpgBox.isSelected()));
        p.setProperty("export.target.rdf",     String.valueOf(targetRdfBox.isSelected()));
        p.setProperty("fuseki.push.enabled",   String.valueOf(fusekiPushBox.isSelected()));
        p.setProperty("rdf.output.path",       rdfOutputPathField.getText().trim());
        return p;
    }

    // ── Export ────────────────────────────────────────────────────────────────

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

        Set<String> selectedLanguages = new LinkedHashSet<>();
        if (exportUAFBox.isSelected())   selectedLanguages.add("UAF");
        if (exportSysMLBox.isSelected()) selectedLanguages.add("SysML");
        if (exportBPMNBox.isSelected())  selectedLanguages.add("BPMN");

        if (selectedLanguages.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one modelling language to export.",
                "UAF Neo4j Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Build the ordered list of export targets the user has ticked. Order matters because the
        // first target's result is what the summary dialog renders; LPG is the system of record so
        // it goes first when both are selected.
        final boolean tgtLpg = targetLpgBox.isSelected();
        final boolean tgtRdf = targetRdfBox.isSelected();
        if (!tgtLpg && !tgtRdf) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one export target (LPG and/or RDF).",
                "UAF Neo4j Export", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final boolean inclTaggedValues  = exportTaggedValuesBox.isSelected();
        final boolean inclRelationships = exportRelationshipsBox.isSelected();
        final boolean inclInstanceLinks = exportInstanceLinksBox.isSelected();
        final Properties connProps      = allProps();
        final ExportLog  log            = new ExportLog(project.getName());
        final Set<String> langFilter    = Collections.unmodifiableSet(selectedLanguages);

        setButtonsEnabled(false);
        logArea.setText("");
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        new SwingWorker<Map<String, ExportResult>, String>() {
            @Override
            protected Map<String, ExportResult> doInBackground() throws Exception {
                publish("Traversing model (languages: " + String.join(", ", langFilter) + ")…");
                UAFModelTraverser traverser = new UAFModelTraverser(project);

                List<UAFElementDTO> elements = traverser.getElements().stream()
                    .filter(el -> {
                        boolean inPkg = selectedPackages.stream().anyMatch(pkg ->
                            el.packageName.equals(pkg) || el.packageName.startsWith(pkg + "::"));
                        if (!inPkg) return false;
                        String lang = (el.language == null || el.language.isEmpty()) ? "UAF" : el.language;
                        return langFilter.contains(lang);
                    })
                    .collect(Collectors.toList());

                Map<String, Integer> langCounts = new LinkedHashMap<>();
                for (UAFElementDTO el : elements) {
                    String lang = (el.language == null || el.language.isEmpty()) ? "UAF" : el.language;
                    langCounts.merge(lang, 1, Integer::sum);
                }

                Set<String> selectedIds = elements.stream()
                    .map(el -> el.id).collect(Collectors.toSet());

                List<UAFRelationshipDTO> relationships = inclRelationships
                    ? traverser.getRelationships().stream()
                        .filter(r -> selectedIds.contains(r.sourceId))
                        .collect(Collectors.toList())
                    : Collections.emptyList();

                publish(String.format("Found %d elements, %d relationships across %d package(s).",
                    elements.size(), relationships.size(), selectedPackages.size()));

                Map<String, ExportResult> results = new LinkedHashMap<>();
                if (tgtLpg) results.put("LPG (Neo4j)", runOneTarget(
                    "LPG (Neo4j)", new Neo4jExportService(connProps),
                    elements, relationships, langCounts, traverser));
                if (tgtRdf) results.put("RDF (Turtle/Fuseki)", runOneTarget(
                    "RDF (Turtle/Fuseki)", new RDFExportService(connProps),
                    elements, relationships, langCounts, traverser));
                return results;
            }

            /** Runs the full export pipeline against a single ExportService. Always closes svc. */
            private ExportResult runOneTarget(String name, ExportService svc,
                                              List<UAFElementDTO> elements,
                                              List<UAFRelationshipDTO> relationships,
                                              Map<String, Integer> langCounts,
                                              UAFModelTraverser traverser) {
                try (ExportService autoClosed = svc) {
                    publish("[" + name + "] Connecting…");
                    autoClosed.init();
                    publish("[" + name + "] Writing nodes…");
                    autoClosed.exportNodes(elements, inclTaggedValues);
                    if (!relationships.isEmpty()) {
                        publish("[" + name + "] Writing relationships…");
                        autoClosed.exportRelationships(relationships);
                    }
                    if (inclInstanceLinks) {
                        publish("[" + name + "] Linking to metamodel stereotypes…");
                        autoClosed.exportInstanceOfLinks(elements);
                    }
                    publish("[" + name + "] Writing system model provenance…");
                    autoClosed.exportSystemModel(traverser.getSystemModelId(), traverser.getSystemModelName());
                    autoClosed.exportDefinesLinks(traverser.getSystemModelId(), elements);
                    autoClosed.getResult().languageCounts.putAll(langCounts);
                    autoClosed.getResult().unmatchedStereotypes.putAll(traverser.getUnmatchedStereotypes());
                    return autoClosed.getResult();
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
                    Map<String, ExportResult> results = get();
                    ExportResult combined = mergeResults(results);
                    log.finish(combined);
                    for (Map.Entry<String, ExportResult> e : results.entrySet()) {
                        appendLog("[" + e.getKey() + "] complete — "
                            + e.getValue().nodesWritten + " nodes, "
                            + e.getValue().relationshipsWritten + " relationships.");
                    }
                    new ExportSummaryDialog(null, combined, log).setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log.finishWithException(cause.getMessage());
                    appendLog("ERROR: " + cause.getMessage());
                }
            }
        }.execute();
    }

    /**
     * Combine per-target {@link ExportResult}s into one for {@link ExportSummaryDialog}.
     * Counts take the max across targets (both targets process the same elements, so max
     * represents the work actually attempted). Errors are prefixed with the target name so
     * the user can tell which path failed.
     */
    private static ExportResult mergeResults(Map<String, ExportResult> results) {
        ExportResult out = new ExportResult();
        for (Map.Entry<String, ExportResult> e : results.entrySet()) {
            ExportResult r = e.getValue();
            out.nodesWritten         = Math.max(out.nodesWritten,         r.nodesWritten);
            out.relationshipsWritten = Math.max(out.relationshipsWritten, r.relationshipsWritten);
            out.instanceLinksWritten = Math.max(out.instanceLinksWritten, r.instanceLinksWritten);
            out.definesLinksWritten  = Math.max(out.definesLinksWritten,  r.definesLinksWritten);
            for (String err : r.errors) out.errors.add("[" + e.getKey() + "] " + err);
            if (out.languageCounts.isEmpty()) out.languageCounts.putAll(r.languageCounts);
            // Unmatched stereotypes come from the traverser, identical across targets — take from first.
            if (out.unmatchedStereotypes.isEmpty()) out.unmatchedStereotypes.putAll(r.unmatchedStereotypes);
        }
        return out;
    }

    private void appendLog(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void setButtonsEnabled(boolean enabled) {
        saveConfigBtn.setEnabled(enabled);
        exportBtn.setEnabled(enabled);
        cancelBtn.setEnabled(enabled);
        browseGraphBtn.setEnabled(enabled);
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
