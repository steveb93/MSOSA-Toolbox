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

/**
 * Screen 2 — Graph Inspector (Phase 2a).
 *
 * Non-modal dialog that queries :UAFElement nodes from Neo4j and presents them
 * in a searchable/filterable table.  Selecting a row shows core properties in
 * the right-hand inspector panel and enables "Locate in MSOSA Model", which
 * navigates the MSOSA containment browser to the corresponding element using
 * the MagicDraw element ID stored as the node's {id} property.
 */
public class GraphInspectorDialog extends JDialog {

    // ── Palette (shared with ExportConfigDialog) ──────────────────────────────
    private static final Color HDR_BG        = new Color( 43,  43,  43);
    private static final Color HDR_TITLE     = Color.WHITE;
    private static final Color HDR_SUBTITLE  = new Color(160, 160, 160);
    private static final Color LEFT_BG       = new Color(248, 249, 251);
    private static final Color BORDER_SUBTLE = new Color(218, 219, 224);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Properties               connectionConfig;
    private final Project                  project;           // may be null
    private final List<String>             nodeIds  = new ArrayList<>();
    private final List<Map<String, Object>> nodeData = new ArrayList<>();

    // ── Main node table ───────────────────────────────────────────────────────
    private final DefaultTableModel                tableModel;
    private final JTable                           mainTable;
    private final TableRowSorter<DefaultTableModel> sorter;

    // ── Search / filter ───────────────────────────────────────────────────────
    private final JTextField        searchField = new JTextField(22);
    private final JComboBox<String> domainBox;

    // ── Inspector panel ───────────────────────────────────────────────────────
    private final DefaultTableModel propsModel;
    private final JTable            propsTable;
    private final JButton           locateBtn = new JButton("Locate in MSOSA Model");

    // ── Cypher / status ───────────────────────────────────────────────────────
    private final JTextField   cypherField = new JTextField();
    private final JLabel       statusLabel = new JLabel("Connecting to Neo4j…");
    private final JProgressBar loadingBar  = new JProgressBar();

    // Properties shown in inspector (ordered)
    private static final List<String> PROP_ORDER = Arrays.asList(
        "id", "name", "qualifiedName", "stereotype", "domain", "packageName", "documentation"
    );

    public GraphInspectorDialog(Frame parent, Properties connectionConfig, Project project) {
        super(parent, "UAF Neo4j — Graph Inspector", false); // non-modal
        this.connectionConfig = connectionConfig;
        this.project          = project;

        // Main table
        tableModel = new DefaultTableModel(
                new String[]{"Name", "Stereotype", "Domain", "Package"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        mainTable = new JTable(tableModel);
        mainTable.setRowHeight(22);
        mainTable.setShowGrid(false);
        mainTable.setIntercellSpacing(new Dimension(0, 0));
        mainTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        mainTable.getColumnModel().getColumn(0).setPreferredWidth(210);
        mainTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        mainTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        mainTable.getColumnModel().getColumn(3).setPreferredWidth(200);
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

        // Domain filter combo
        domainBox = new JComboBox<>(new String[]{
            "All Domains", "STRATEGIC", "OPERATIONAL", "RESOURCE",
            "SERVICE", "PERSONNEL", "ACQUISITION", "SECURITY", "SHARED"
        });

        // Live search listener
        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        };
        searchField.getDocument().addDocumentListener(dl);
        domainBox.addActionListener(e -> applyFilter());

        // Locate button
        locateBtn.setEnabled(false);
        locateBtn.setToolTipText(
            "<html>Navigate to this element in the MSOSA containment browser.<br>" +
            "The element is looked up by its MagicDraw ID.</html>");
        locateBtn.addActionListener(e -> locateInMSOSA());

        // Cypher field — read-only, monospaced
        cypherField.setEditable(false);
        cypherField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        // Loading bar — shown only while fetching
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildMain(),   BorderLayout.CENTER);
        add(buildSouth(),  BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(960, 600));
        setPreferredSize(new Dimension(1060, 700));
        setResizable(true);
        setLocationRelativeTo(parent);

        refreshData();
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
            "Browse exported UAF elements from Neo4j. " +
            "Select a node to inspect its properties and locate it in the MSOSA model.");
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
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            buildNodesPanel(), buildInspectorPanel());
        split.setDividerLocation(620);
        split.setBorder(null);
        return split;
    }

    private JPanel buildNodesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 0, 1, BORDER_SUBTLE),
            new EmptyBorder(10, 12, 10, 8)));

        // Search bar
        JLabel searchLbl  = new JLabel("Search:");
        JLabel domainLbl  = new JLabel("  Domain:");
        JPanel searchRow  = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchLbl);
        searchRow.add(searchField);
        searchRow.add(domainLbl);
        searchRow.add(domainBox);

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

        // Section heading
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

        // Properties table
        JScrollPane propsScroll = new JScrollPane(propsTable);
        propsScroll.setBorder(new MatteBorder(1, 1, 1, 1, BORDER_SUBTLE));
        propsScroll.getViewport().setBackground(LEFT_BG);

        // Locate button
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

        // Cypher row
        JLabel cypherLbl = new JLabel("Cypher:");
        cypherLbl.setFont(cypherLbl.getFont().deriveFont(Font.BOLD, 11f));
        JPanel cypherRow = new JPanel(new BorderLayout(6, 0));
        cypherRow.setOpaque(false);
        cypherRow.add(cypherLbl,   BorderLayout.WEST);
        cypherRow.add(cypherField, BorderLayout.CENTER);

        // Status + buttons row
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshData());
        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(refreshBtn);
        btnPanel.add(closeBtn);

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
        statusLabel.setText("Connecting to Neo4j…");
        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(true);

        final Properties cfg = connectionConfig;
        new SwingWorker<List<Map<String, Object>>, Void>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                try (Neo4jExportService svc = new Neo4jExportService(cfg)) {
                    svc.init();
                    return svc.fetchAllUAFElements();
                }
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
                            row.getOrDefault("packageName", "")
                        });
                    }
                    statusLabel.setText(rows.size() + " nodes loaded from Neo4j");
                    applyFilter();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Failed to load: " + cause.getMessage());
                }
            }
        }.execute();
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private void applyFilter() {
        String text   = searchField.getText().trim();
        String domain = (String) domainBox.getSelectedItem();

        List<RowFilter<DefaultTableModel, Integer>> filters = new ArrayList<>();

        if (!text.isEmpty()) {
            try {
                // Search across Name (0), Stereotype (1), Package (3)
                filters.add(RowFilter.regexFilter("(?i)" + text, 0, 1, 3));
            } catch (Exception ignored) { /* invalid regex — skip */ }
        }
        if (!"All Domains".equals(domain)) {
            filters.add(RowFilter.regexFilter("^" + domain + "$", 2));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            sorter.setRowFilter(filters.get(0));
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }

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
            return;
        }
        int modelRow = mainTable.convertRowIndexToModel(viewRow);
        String nodeId        = nodeIds.get(modelRow);
        Map<String, Object>  data = nodeData.get(modelRow);

        cypherField.setText("MATCH (n:UAFElement {id: '" + nodeId + "'}) RETURN n");
        locateBtn.setEnabled(project != null);

        // Populate inspector from cached summary data (no extra Neo4j round-trip)
        propsModel.setRowCount(0);
        for (String key : PROP_ORDER) {
            Object val = data.get(key);
            if (val != null && !val.toString().isEmpty()) {
                propsModel.addRow(new Object[]{key, val});
            }
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
                Application.getInstance().getMainFrame().getBrowser()
                    .getContainmentTree().openNode(el);
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
}
