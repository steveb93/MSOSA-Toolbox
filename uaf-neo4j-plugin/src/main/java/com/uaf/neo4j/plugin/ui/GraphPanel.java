package com.uaf.neo4j.plugin.ui;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 2b — JGraphX neighbourhood panel.
 *
 * Renders the 1-hop subgraph around a selected :UAFElement node.
 * Nodes are colour-coded by UAF domain; the centre node has a gold border.
 * Clicking any vertex fires the onNodeClick callback with that element's id,
 * allowing the parent dialog to sync the table selection.
 */
public class GraphPanel extends JPanel {

    // ── Domain colour palette ─────────────────────────────────────────────────
    private static final Map<String, String> DOMAIN_FILL = new LinkedHashMap<>();
    static {
        DOMAIN_FILL.put("STRATEGIC",   "#2E4057");
        DOMAIN_FILL.put("OPERATIONAL", "#048A81");
        DOMAIN_FILL.put("RESOURCE",    "#6B4C93");
        DOMAIN_FILL.put("SERVICE",     "#E07B39");
        DOMAIN_FILL.put("PERSONNEL",   "#8B5E3C");
        DOMAIN_FILL.put("ACQUISITION", "#2D6A4F");
        DOMAIN_FILL.put("SECURITY",    "#9B2226");
        DOMAIN_FILL.put("SHARED",      "#5C5C5C");
    }
    private static final Color BG = new Color(248, 249, 251);

    // ── State ─────────────────────────────────────────────────────────────────
    private mxGraphComponent graphComp;
    private Consumer<String>  onNodeClick;

    private final JLabel placeholder = new JLabel(
        "<html><center>Select a node in the table<br>to view its neighbourhood graph</center></html>",
        SwingConstants.CENTER);

    public GraphPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        placeholder.setFont(placeholder.getFont().deriveFont(Font.ITALIC, 11f));
        placeholder.setForeground(new Color(150, 150, 150));
        add(placeholder, BorderLayout.CENTER);
    }

    public void setOnNodeClick(Consumer<String> handler) {
        this.onNodeClick = handler;
    }

    /** Removes the graph and shows the placeholder message. */
    public void clear() {
        if (graphComp != null) {
            remove(graphComp);
            graphComp = null;
        }
        add(placeholder, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Renders the given nodes and relationships as a hierarchical graph.
     *
     * @param nodes     list of node property maps (keys: id, name, stereotype, domain)
     * @param rels      list of relationship maps (keys: fromId, toId, relType)
     * @param centreId  the selected node's id — rendered with a gold border
     */
    public void showNeighbourhood(List<Map<String, Object>> nodes,
                                   List<Map<String, Object>> rels,
                                   String centreId) {
        remove(placeholder);
        if (graphComp != null) remove(graphComp);

        mxGraph graph = new mxGraph();
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);
        graph.setEdgeLabelsMovable(false);
        graph.setAllowDanglingEdges(false);

        Object parent = graph.getDefaultParent();
        final Map<String, Object> cellById = new HashMap<>();   // nodeId → mxCell vertex
        final Map<Object, String> idByCell = new IdentityHashMap<>(); // mxCell → nodeId

        graph.getModel().beginUpdate();
        try {
            for (Map<String, Object> node : nodes) {
                String id     = str(node, "id");
                String name   = str(node, "name");
                String stereo = str(node, "stereotype");
                String domain = str(node, "domain");
                if (id.isEmpty()) continue;

                String fill  = DOMAIN_FILL.getOrDefault(domain, "#888888");
                String style = vertexStyle(fill, id.equals(centreId));
                String label = truncate(name, 22) + (stereo.isEmpty() ? "" : "\n«" + stereo + "»");

                Object v = graph.insertVertex(parent, null, label, 0, 0, 150, 55, style);
                cellById.put(id, v);
                idByCell.put(v, id);
            }

            Set<String> seen = new HashSet<>();
            for (Map<String, Object> rel : rels) {
                String from = str(rel, "fromId");
                String to   = str(rel, "toId");
                String type = str(rel, "relType").replace('_', ' ');
                Object src  = cellById.get(from);
                Object tgt  = cellById.get(to);
                if (src == null || tgt == null || src == tgt) continue;
                if (!seen.add(from + "|" + to + "|" + type)) continue;

                graph.insertEdge(parent, null, type, src, tgt,
                    "rounded=1;edgeStyle=orthogonalEdgeStyle;" +
                    "fontSize=9;fontColor=#666666;strokeColor=#BBBBBB;exitX=1;exitY=0.5;" +
                    "entryX=0;entryY=0.5;");
            }
        } finally {
            graph.getModel().endUpdate();
        }

        // Left-to-right hierarchical layout
        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph, SwingConstants.WEST);
        layout.setInterRankCellSpacing(55);
        layout.setIntraCellSpacing(35);
        layout.execute(parent);

        graphComp = new mxGraphComponent(graph);
        graphComp.setConnectable(false);
        graphComp.setBorder(null);
        graphComp.setBackground(BG);
        graphComp.getViewport().setBackground(BG);

        // Single click → sync table selection in parent dialog
        graphComp.getGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object cell = graphComp.getCellAt(e.getX(), e.getY());
                if (cell != null && graph.getModel().isVertex(cell)) {
                    String clickedId = idByCell.get(cell);
                    if (clickedId != null && onNodeClick != null) {
                        onNodeClick.accept(clickedId);
                    }
                }
            }
        });

        add(graphComp, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String vertexStyle(String fillHex, boolean isCentre) {
        String s = "rounded=1;arcSize=20;whiteSpace=wrap;fontSize=10;" +
                   "fontColor=#FFFFFF;fillColor=" + fillHex +
                   ";strokeColor=" + darken(fillHex) + ";";
        if (isCentre) s += "strokeWidth=3;strokeColor=#FFD700;";
        return s;
    }

    private static String darken(String hex) {
        try {
            Color c = Color.decode(hex);
            return String.format("#%02X%02X%02X",
                (int)(c.getRed()   * 0.72),
                (int)(c.getGreen() * 0.72),
                (int)(c.getBlue()  * 0.72));
        } catch (Exception e) {
            return hex;
        }
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
