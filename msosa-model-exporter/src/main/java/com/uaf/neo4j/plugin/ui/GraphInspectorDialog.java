package com.uaf.neo4j.plugin.ui;

import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Properties;

/**
 * Graph Inspector — non-modal dialog that queries nodes from Neo4j and presents
 * them in a searchable/filterable table.
 *
 * Right panel tabs:
 *   Properties — core node properties including language; Locate in MSOSA button
 *   Graph       — JGraphX 1-hop neighbourhood (domain/language colour-coded)
 *
 * Clicking a graph node syncs the table selection (bidirectional).
 * Left-drag on the Graph tab pans the viewport.
 */
public class GraphInspectorDialog extends JDialog {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color HDR_BG        = new Color( 43,  43,  43);
    private static final Color HDR_TITLE     = Color.WHITE;
    private static final Color HDR_SUBTITLE  = new Color(160, 160, 160);
    private static final Color LEFT_BG       = new Color(248, 249, 251);
    private static final Color BORDER_SUBTLE = new Color(218, 219, 224);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Properties                connectionConfig;
    private final Project                   project;
    private final List<String>              nodeIds  = new ArrayList<>();
    private final List<Map<String, Object>> nodeData = new ArrayList<>();
    private volatile String                 pendingNeighbourhoodId;

    // ── Main table (0=Name, 1=Stereotype, 2=Domain, 3=Language, 4=Package) ───
    private final DefaultTableModel                 tableModel;
    private final JTable                            mainTable;
    private final TableRowSorter<DefaultTableModel> sorter;

    // ── Search / filter ───────────────────────────────────────────────────────
    private final JTextField        searchField  = new JTextField(20);
    private final JComboBox<String> domainBox;
    private final JComboBox<String> languageBox;

    // ── Right panel ───────────────────────────────────────────────────────────
    private JTabbedPane             rightTabs;
    private final DefaultTableModel propsModel;
    private final JTable            propsTable;
    private final JButton           locateBtn  = new JButton("Locate in MSOSA Model");
    private final GraphPanel        graphPanel  = new GraphPanel();

    // ── Cypher / status ───────────────────────────────────────────────────────
    private final JTextField   cypherField = new JTextField();
    private final JLabel       statusLabel = new JLabel("Connecting to Neo4j…");
    private final JProgressBar loadingBar  = new JProgressBar();

    private static final List<String> PROP_ORDER = Arrays.asList(
        "id", "name", "qualifiedName", "stereotype", "domain", "language", "packageName", "documentation"
    );

    private final JPanel body = new JPanel(new BorderLayout());

    /**
     * Build the embedded inspector form. The dialog object exists only as a
     * controller; {@link #getEmbeddedBody()} returns the panel hosted by the
     * workbench's Inspect rail.
     */
    public GraphInspectorDialog(Properties connectionConfig, Project project) {
        super((Frame) null, "UAF Knowledge Graph — Inspect", false);
        this.connectionConfig = connectionConfig;
        this.project          = project;

        // Main table — 5 columns
        tableModel = new DefaultTableModel(
                new String[]{"Name", "Stereotype", "Domain", "Language", "Package"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        mainTable = new JTable(tableModel);
        mainTable.setRowHeight(22);
        mainTable.setShowGrid(false);
        mainTable.setIntercellSpacing(new Dimension(0, 0));
        mainTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mainTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        mainTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        mainTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        mainTable.getColumnModel().getColumn(3).setPreferredWidth(65);
        mainTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        sorter = new TableRowSorter<>(tableModel);
        mainTable.setRowSorter(sorter);
        mainTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRowSelected();
        });

        // Properties table
        propsModel = new DefaultTableModel(new String[]{"Property", "Value"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        propsTable = new JTable(propsModel);
        propsTable.setRowHeight(22);
        propsTable.setShowGrid(false);
        propsTable.setIntercellSpacing(new Dimension(0, 0));
        propsTable.getColumnModel().getColumn(0).setPreferredWidth(110);
        propsTable.getColumnModel().getColumn(1).setPreferredWidth(240);
        propsTable.setBackground(LEFT_BG);

        // Filter combos
        domainBox = new JComboBox<>(new String[]{
            "All Domains", "STRATEGIC", "OPERATIONAL", "RESOURCE",
            "SERVICE", "PERSONNEL", "ACQUISITION", "SECURITY", "SHARED"
        });
        languageBox = new JComboBox<>(new String[]{"All Languages", "UAF", "SysML", "BPMN"});

        // Live search
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        };
        searchField.getDocument().addDocumentListener(dl);
        domainBox.addActionListener(e -> applyFilter());
        languageBox.addActionListener(e -> applyFilter());

        // Locate button
        locateBtn.setEnabled(false);
        locateBtn.setToolTipText(
            "<html>Navigate to this element in the MSOSA containment browser.<br>" +
            "The element is looked up by its MagicDraw ID.</html>");
        locateBtn.addActionListener(e -> locateInMSOSA());

        // Cypher field — read-only, monospaced
        cypherField.setEditable(false);
        cypherField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);

        // Graph tab — clicking a node syncs table selection and switches to Properties
        graphPanel.setOnNodeClick(clickedId -> {
            int modelIdx = nodeIds.indexOf(clickedId);
            if (modelIdx >= 0) {
                int viewIdx = mainTable.convertRowIndexToView(modelIdx);
                if (viewIdx >= 0) {
                    mainTable.setRowSelectionInterval(viewIdx, viewIdx);
                    mainTable.scrollRectToVisible(mainTable.getCellRect(viewIdx, 0, true));
                    if (rightTabs != null) rightTabs.setSelectedIndex(0);
                }
            }
        });

        body.add(buildHeader(), BorderLayout.NORTH);
        body.add(buildMain(),   BorderLayout.CENTER);
        body.add(buildSouth(),  BorderLayout.SOUTH);

        refreshData();
    }

    /** The inspector form panel, hosted by the workbench's Inspect rail. */
    public JComponent getEmbeddedBody() {
        return body;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(HDR_BG);
        header.setOpaque(true);
        header.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, new Color(65, 65, 65)),
            new EmptyBorder(16, 20, 15, 20)));

        JLabel title = new JLabel("Graph Inspector");
        title.setForeground(HDR_TITLE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setOpaque(false);

        JLabel subtitle = new JLabel(
            "Browse exported nodes from Neo4j. Select a node to inspect properties " +
            "and explore its neighbourhood graph.");
        subtitle.setForeground(HDR_SUBTITLE);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
        subtitle.setOpaque(false);

        JPanel textBlock = new JPanel(new GridLayout(2, 1, 0, 4));
        textBlock.setOpaque(false);
        textBlock.add(title);
        textBlock.add(subtitle);

        header.add(textBlock, BorderLayout.CENTER);
        return header;
    }

    // ── Main split ────────────────────────────────────────────────────────────

    private JSplitPane buildMain() {
        rightTabs = new JTabbedPane();
        rightTabs.addTab("Properties", buildInspectorPanel());
        rightTabs.addTab("Graph",      graphPanel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildNodesPanel(), rightTabs);
        // Proportional divider — the previous hard-coded 660px assumed the
        // 1240px dialog and left the workbench's narrower content area with
        // ~140px of right-tab space.
        split.setResizeWeight(0.60);
        split.setDividerLocation(520);                   // sensible absolute fallback
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.60));  // proportional once realised
        split.setBorder(null);
        return split;
    }

    private JPanel buildNodesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 0, 1, BORDER_SUBTLE),
            new EmptyBorder(10, 12, 10, 8)));

        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        searchRow.setOpaque(false);
        searchRow.add(new JLabel("Search:"));
        searchRow.add(searchField);
        searchRow.add(new JLabel("  Domain:"));
        searchRow.add(domainBox);
        searchRow.add(new JLabel("  Language:"));
        searchRow.add(languageBox);

        JScrollPane scroll = new JScrollPane(mainTable);
        scroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER_SUBTLE));

        panel.add(searchRow, BorderLayout.NORTH);
        panel.add(scroll,    BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(LEFT_BG);
        panel.setOpaque(true);
        panel.setBorder(new EmptyBorder(10, 10, 10, 12));

        JLabel heading = new JLabel("Node Properties");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));

        JPanel headBlock = new JPanel();
        headBlock.setLayout(new BoxLayout(headBlock, BoxLayout.Y_AXIS));
        headBlock.setOpaque(false);
        headBlock.add(heading);
        headBlock.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        headBlock.add(sep);

        JScrollPane propsScroll = new JScrollPane(propsTable);
        propsScroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER_SUBTLE));
        propsScroll.getViewport().setBackground(LEFT_BG);

        JPanel southPanel = new JPanel(new BorderLayout(0, 4));
        southPanel.setOpaque(false);
        southPanel.add(locateBtn, BorderLayout.CENTER);

        panel.add(headBlock,   BorderLayout.NORTH);
        panel.add(propsScroll, BorderLayout.CENTER);
        panel.add(southPanel,  BorderLayout.SOUTH);
        return panel;
    }

    // ── South bar ─────────────────────────────────────────────────────────────

    private JPanel buildSouth() {
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_SUBTLE),
            new EmptyBorder(6, 12, 10, 12)));

        JLabel cypherLbl = new JLabel("Cypher:");
        cypherLbl.setFont(cypherLbl.getFont().deriveFont(Font.BOLD, 11f));
        JPanel cypherRow = new JPanel(new BorderLayout(6, 0));
        cypherRow.setOpaque(false);
        cypherRow.add(cypherLbl,   BorderLayout.WEST);
        cypherRow.add(cypherField, BorderLayout.CENTER);

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshData());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(refreshBtn);

        loadingBar.setPreferredSize(new Dimension(100, 14));
        JPanel statusRow = new JPanel(new BorderLayout(8, 0));
        statusRow.setOpaque(false);
        statusRow.add(loadingBar,  BorderLayout.WEST);
        statusRow.add(statusLabel, BorderLayout.CENTER);
        statusRow.add(btnPanel,    BorderLayout.EAST);

        south.add(cypherRow,  BorderLayout.NORTH);
        south.add(statusRow,  BorderLayout.SOUTH);
        return south;
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void refreshData() {
        tableModel.setRowCount(0);
        nodeIds.clear();
        nodeData.clear();
        propsModel.setRowCount(0);
        cypherField.setText("");
        locateBtn.setEnabled(false);
        graphPanel.clear();
        statusLabel.setText("Connecting to Neo4j…");
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(true);

        new SwingWorker<List<Map<String, Object>>, Void>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return fetchAllNodes();
            }
            @Override
            protected void done() {
                loadingBar.setIndeterminate(false);
                loadingBar.setVisible(false);
                try {
                    List<Map<String, Object>> rows = get();
                    for (Map<String, Object> row : rows) {
                        nodeIds.add(String.valueOf(row.getOrDefault("id", "")));
                        nodeData.add(row);
                        tableModel.addRow(new Object[]{
                            row.getOrDefault("name",        ""),
                            row.getOrDefault("stereotype",  ""),
                            row.getOrDefault("domain",      ""),
                            row.getOrDefault("language",    ""),
                            row.getOrDefault("packageName", "")
                        });
                    }
                    statusLabel.setText(rows.size() + " nodes loaded from Neo4j");
                    applyFilter();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String uri = connectionConfig.getProperty("neo4j.uri", "bolt://localhost:7687");
                    statusLabel.setText("Failed to load (" + uri + "): "
                        + cause.getClass().getSimpleName() + " — " + cause.getMessage());
                }
            }
        }.execute();
    }

    /** Source of all node rows for the table. Overridable so the inspector can be
     *  driven from sample data (UI preview harness) without a live Neo4j. */
    protected List<Map<String, Object>> fetchAllNodes() throws Exception {
        try (Neo4jExportService svc = new Neo4jExportService(connectionConfig)) {
            svc.init();
            return svc.fetchAllUAFElements();
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private void applyFilter() {
        String text     = searchField.getText().trim();
        String domain   = (String) domainBox.getSelectedItem();
        String language = (String) languageBox.getSelectedItem();

        List<RowFilter<DefaultTableModel, Integer>> filters = new ArrayList<>();
        if (!text.isEmpty()) {
            try { filters.add(RowFilter.regexFilter("(?i)" + text, 0, 1, 4)); } // Name, Stereotype, Package
            catch (Exception ignored) {}
        }
        if (!"All Domains".equals(domain)) {
            filters.add(RowFilter.regexFilter("^" + domain + "$", 2));
        }
        if (!"All Languages".equals(language)) {
            filters.add(RowFilter.regexFilter("^" + language + "$", 3));
        }

        if (filters.isEmpty())        sorter.setRowFilter(null);
        else if (filters.size() == 1) sorter.setRowFilter(filters.get(0));
        else                          sorter.setRowFilter(RowFilter.andFilter(filters));

        int visible = mainTable.getRowCount();
        int total   = tableModel.getRowCount();
        statusLabel.setText(visible == total
            ? total + " nodes loaded from Neo4j"
            : visible + " / " + total + " nodes shown");
    }

    // ── Row selection ─────────────────────────────────────────────────────────

    private void onRowSelected() {
        int viewRow = mainTable.getSelectedRow();
        if (viewRow < 0) {
            propsModel.setRowCount(0);
            cypherField.setText("");
            locateBtn.setEnabled(false);
            graphPanel.clear();
            return;
        }
        int modelRow = mainTable.convertRowIndexToModel(viewRow);
        String nodeId       = nodeIds.get(modelRow);
        Map<String, Object> data = nodeData.get(modelRow);

        cypherField.setText(
            "MATCH (n {id: '" + nodeId + "'})-[r]-(m) WHERE n.stereotype IS NOT NULL AND m.stereotype IS NOT NULL RETURN n,r,m");
        locateBtn.setEnabled(project != null);

        propsModel.setRowCount(0);
        for (String key : PROP_ORDER) {
            Object val = data.get(key);
            if (val != null && !val.toString().isEmpty()) {
                propsModel.addRow(new Object[]{key, val});
            }
        }

        loadNeighbourhood(nodeId);
    }

    // ── Neighbourhood graph ───────────────────────────────────────────────────

    private void loadNeighbourhood(String nodeId) {
        pendingNeighbourhoodId = nodeId;
        graphPanel.clear();
        new SwingWorker<Neo4jExportService.NeighbourhoodResult, Void>() {
            @Override
            protected Neo4jExportService.NeighbourhoodResult doInBackground() throws Exception {
                return fetchNeighbourhood(nodeId);
            }
            @Override
            protected void done() {
                if (!nodeId.equals(pendingNeighbourhoodId)) return;
                try {
                    Neo4jExportService.NeighbourhoodResult result = get();
                    graphPanel.showNeighbourhood(result.nodes, result.relationships, nodeId);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    /** Source of a node's 1-hop neighbourhood. Overridable for the UI preview harness. */
    protected Neo4jExportService.NeighbourhoodResult fetchNeighbourhood(String nodeId) throws Exception {
        try (Neo4jExportService svc = new Neo4jExportService(connectionConfig)) {
            svc.init();
            return svc.fetchNeighbourhood(nodeId);
        }
    }

    // ── Locate in MSOSA ───────────────────────────────────────────────────────

    private void locateInMSOSA() {
        if (project == null) return;
        int viewRow = mainTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = mainTable.convertRowIndexToModel(viewRow);
        String nodeId = nodeIds.get(modelRow);
        try {
            Element el = (Element) project.getElementByID(nodeId);
            if (el != null) {
                openInContainmentBrowser(el);
            } else {
                JOptionPane.showMessageDialog(this,
                    "<html>Element not found in the current project.<br>" +
                    "The graph may contain elements from a different model version.</html>",
                    "Locate in MSOSA Model", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Could not navigate to element: " + ex.getMessage(),
                "Locate in MSOSA Model", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Navigates the MSOSA containment browser to the given element.
     *
     * Uses reflection for the MainFrame → Browser → ContainmentTree chain to avoid
     * compile-time dependencies on the full JIDE docking library hierarchy.
     * All JIDE jars are present at runtime inside MSOSA.
     */
    private static void openInContainmentBrowser(Element el) throws Exception {
        Object app       = Application.getInstance();
        Object mainFrame = app.getClass().getMethod("getMainFrame").invoke(app);
        Object browser   = mainFrame.getClass().getMethod("getBrowser").invoke(mainFrame);
        Object tree      = browser.getClass().getMethod("getContainmentTree").invoke(browser);
        for (java.lang.reflect.Method m : tree.getClass().getMethods()) {
            if ("openNode".equals(m.getName()) && m.getParameterCount() == 1) {
                m.invoke(tree, el);
                return;
            }
        }
        throw new NoSuchMethodException("openNode(BaseElement) not found on " + tree.getClass());
    }
}
