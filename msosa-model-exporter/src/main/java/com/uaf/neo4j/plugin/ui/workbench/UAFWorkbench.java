package com.uaf.neo4j.plugin.ui.workbench;

import com.nomagic.magicdraw.core.Project;
import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.ui.ExportConfigDialog;
import com.uaf.neo4j.plugin.ui.GraphInspectorDialog;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/**
 * Single-window workbench for the UAF Neo4j Plugin. Replaces the per-feature
 * dialogs (Export, Configure Connection, Graph Inspector) with a left-rail
 * navigation over six modes — three operational today (Export / Inspect /
 * Settings), one near-term (Validate), two on the roadmap (Federate / Insights).
 *
 * <p>The workbench owns a {@link StatusStrip} along the bottom showing live
 * Neo4j + Fuseki health, so users no longer need to open a separate config
 * dialog to check whether the backends are reachable.
 */
public class UAFWorkbench extends JFrame {

    private static final Color RAIL_BG       = new Color(248, 249, 251);
    private static final Color RAIL_BORDER   = new Color(218, 219, 224);
    private static final Color HEADER_BG     = new Color(43, 43, 43);
    private static final Color HEADER_TITLE  = Color.WHITE;
    private static final Color HEADER_SUB    = new Color(170, 170, 170);

    private final Project project;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final Map<WorkbenchMode, WorkbenchPanel> panels = new EnumMap<>(WorkbenchMode.class);
    private final StatusStrip statusStrip;

    public UAFWorkbench(Project project) {
        super("UAF Knowledge Graph");
        this.project = project;

        Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();
        statusStrip = new StatusStrip(cfg);

        buildPanels();

        setLayout(new BorderLayout());
        add(buildHeader(),     BorderLayout.NORTH);
        add(buildRail(),       BorderLayout.WEST);
        add(cardHost,          BorderLayout.CENTER);
        add(statusStrip,       BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // Sized to fit the embedded Graph Inspector comfortably — its left panel
        // (table + filters) wants ~660px, the right tabs need ≥350px to not
        // squish the Properties table.
        setSize(new Dimension(1280, 800));
        setMinimumSize(new Dimension(960, 580));
        setLocationByPlatform(true);
    }

    public StatusStrip getStatusStrip() {
        return statusStrip;
    }

    Project getProject() {
        return project;
    }

    // ── Factory hooks (overridable) ─────────────────────────────────────────
    // Mode panels go through these instead of constructing the dialogs directly,
    // so tests and the preview harness can swap in sample-data subclasses.

    /**
     * Creates the embedded Export form. Returns {@code null} when no project is
     * open — the panel renders a "no project" notice in that case. Override in
     * subclasses to inject a sample-data variant (see {@code PreviewUAFWorkbench}).
     */
    protected ExportConfigDialog createExportDialog(Project project) {
        if (project == null) return null;
        return new ExportConfigDialog(project);
    }

    /**
     * Creates the embedded Graph Inspector form. Override in subclasses to
     * inject a sample-data variant.
     */
    protected GraphInspectorDialog createInspectorDialog(java.util.Properties config, Project project) {
        return new GraphInspectorDialog(config, project);
    }

    private void buildPanels() {
        register(new ExportModePanel(this));
        register(new InspectModePanel(this));
        register(new ValidateModePanel(this));
        register(new FederateModePanel());
        register(new InsightsModePanel());
        register(new SettingsModePanel(this));
    }

    private void register(WorkbenchPanel panel) {
        panels.put(panel.getMode(), panel);
        cardHost.add(panel.getComponent(), panel.getMode().name());
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(10, 16, 10, 16));

        JLabel title = new JLabel("UAF Knowledge Graph");
        title.setForeground(HEADER_TITLE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JLabel sub = new JLabel("MSOSA → Neo4j (LPG) + Fuseki (SPARQL)");
        sub.setForeground(HEADER_SUB);
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 11f));

        header.add(title);
        header.add(sub);
        return header;
    }

    private JComponent buildRail() {
        WorkbenchMode[] modes = WorkbenchMode.values();
        JList<WorkbenchMode> rail = new JList<>(modes);
        rail.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rail.setBackground(RAIL_BG);
        rail.setBorder(new EmptyBorder(8, 0, 8, 0));
        rail.setFixedCellHeight(48);
        rail.setCellRenderer(new RailRenderer());
        rail.setSelectedIndex(0);

        rail.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            WorkbenchMode mode = rail.getSelectedValue();
            if (mode != null) showMode(mode);
        });

        JScrollPane scroll = new JScrollPane(rail,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, RAIL_BORDER));
        scroll.setPreferredSize(new Dimension(190, 0));
        scroll.getViewport().setBackground(RAIL_BG);

        showMode(modes[0]);
        return scroll;
    }

    private void showMode(WorkbenchMode mode) {
        cards.show(cardHost, mode.name());
        WorkbenchPanel p = panels.get(mode);
        if (p != null) p.onActivated();
    }

    @Override
    public void dispose() {
        for (WorkbenchPanel p : panels.values()) p.onClosed();
        super.dispose();
    }

    // ── Rail cell renderer ──────────────────────────────────────────────────

    private static final class RailRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            WorkbenchMode mode = (WorkbenchMode) value;
            JPanel cell = new JPanel(new GridLayout(2, 1));
            cell.setOpaque(true);
            cell.setBorder(new EmptyBorder(4, 14, 4, 10));
            cell.setBackground(isSelected ? new Color(225, 232, 244) : RAIL_BG);

            JLabel label = new JLabel(mode.label + (mode.roadmap ? "  ◔" : ""));
            label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
            label.setForeground(mode.roadmap ? new Color(140, 140, 140) : new Color(40, 40, 40));

            JLabel tag = new JLabel(mode.roadmap
                ? mode.roadmapStage + " — " + mode.tagline
                : mode.tagline);
            tag.setFont(tag.getFont().deriveFont(Font.PLAIN, 10f));
            tag.setForeground(new Color(120, 120, 120));

            cell.add(label);
            cell.add(tag);
            return cell;
        }
    }
}
