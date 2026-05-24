package com.uaf.neo4j.plugin.preview;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * Standalone launcher for previewing the plugin's Swing dialogs off-MSOSA.
 *
 * <p>Which dialog(s): {@code -Dpreview.dialog=export|graph|all} (default {@code all}).
 *
 * <p>Interactive (needs a display) — the window carries a {@link UiInspector}
 * overlay: hover a widget to see the field/class/source line behind it.
 * <pre>mvn -Pui-preview test-compile exec:java -Dpreview.dialog=graph</pre>
 *
 * <p>Headless screenshot render (e.g. CI / cloud, run under xvfb-run):
 * <pre>mvn -Pui-preview test-compile exec:java -Dpreview.screenshot=target/ui-preview</pre>
 *
 * <p>Headless demo of the inspector overlay highlighting one named field:
 * <pre>mvn -Pui-preview test-compile exec:java -Dpreview.dialog=export \
 *     -Dpreview.inspect=exportBtn -Dpreview.screenshot=target/ui-preview</pre>
 *
 * <p>The "Export", "Test Connection", "Refresh" and "Locate in MSOSA" buttons
 * need a live MSOSA project / Neo4j and are not exercised here — this harness is
 * for visual layout iteration only.
 */
public final class UiPreview {

    public static void main(String[] args) throws Exception {
        seedPluginSingleton();

        String which = System.getProperty("preview.dialog", "all").toLowerCase();
        boolean doExport = which.equals("all") || which.equals("export");
        boolean doGraph  = which.equals("all") || which.equals("graph");

        String inspectField = System.getProperty("preview.inspect");
        String shotDir = System.getProperty("preview.screenshot");
        if (shotDir != null && !shotDir.isEmpty()) {
            File outDir = new File(shotDir);
            outDir.mkdirs();
            if (inspectField != null && !inspectField.isEmpty()) {
                if (doExport) renderInspectDemo("export", inspectField, outDir);
                if (doGraph)  renderInspectDemo("graph",  inspectField, outDir);
            } else {
                if (doExport) renderExportWizard(outDir);
                if (doGraph)  renderGraphInspector(outDir);
            }
            System.out.println("UI preview screenshots written to " + outDir.getAbsolutePath());
            System.exit(0);
        } else {
            final boolean fExport = doExport, fGraph = doGraph;
            SwingUtilities.invokeLater(() -> {
                if (fExport) {
                    PreviewExportConfigDialog d = new PreviewExportConfigDialog(null);
                    d.setModal(false);
                    d.setDefaultCloseOperation(JDialog.EXIT_ON_CLOSE);
                    d.setVisible(true);
                    UiInspector.install(d);
                }
                if (fGraph) {
                    PreviewGraphInspectorDialog g = new PreviewGraphInspectorDialog(null, defaultConfig());
                    g.setDefaultCloseOperation(JDialog.EXIT_ON_CLOSE);
                    g.setVisible(true);
                    UiInspector.install(g);
                }
            });
        }
    }

    // ── Headless demo of the UI inspector overlay on a named field ──────────────

    private static void renderInspectDemo(String which, String field, File outDir) throws Exception {
        final JDialog[] holder = new JDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            JDialog d = which.equals("graph")
                ? new PreviewGraphInspectorDialog(null, defaultConfig())
                : new PreviewExportConfigDialog(null);
            d.pack();
            holder[0] = d;
        });
        Thread.sleep(1200); // background data load

        if (which.equals("graph")) {
            SwingUtilities.invokeAndWait(() -> {
                JTable main = findMainTable(holder[0].getContentPane());
                if (main != null && main.getRowCount() > 0) main.setRowSelectionInterval(0, 0);
            });
            Thread.sleep(1200); // neighbourhood load
        }

        SwingUtilities.invokeAndWait(() -> {
            try {
                UiInspector insp = UiInspector.install(holder[0]);
                JComponent root = holder[0].getRootPane();
                root.validate();
                root.doLayout();
                if (!insp.highlightField(field)) {
                    System.out.println("[ui-inspector] no field named '" + field + "' on the " + which + " dialog");
                }
                holder[0].getGlassPane().setSize(root.getSize());
                writePng(root, new File(outDir, "inspect-demo-" + which + ".png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ── Export wizard — one PNG per tab ─────────────────────────────────────────

    private static void renderExportWizard(File outDir) throws Exception {
        final String[] tabFile = {"connection", "options", "preview"};
        final PreviewExportConfigDialog[] holder = new PreviewExportConfigDialog[1];

        SwingUtilities.invokeAndWait(() -> {
            PreviewExportConfigDialog d = new PreviewExportConfigDialog(null);
            d.pack();
            holder[0] = d;
        });
        // Let the background element-count SwingWorker finish and post to the EDT.
        Thread.sleep(1200);

        SwingUtilities.invokeAndWait(() -> {
            try {
                JComponent content = (JComponent) holder[0].getContentPane();
                JTabbedPane tabs = findTabbedPane(content);
                if (tabs == null) {
                    writePng(content, new File(outDir, "export-wizard.png"));
                    return;
                }
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    tabs.setSelectedIndex(i);
                    content.validate();
                    content.doLayout();
                    String name = i < tabFile.length ? tabFile[i] : ("tab" + i);
                    writePng(content, new File(outDir, "export-wizard-" + name + ".png"));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ── Graph inspector — Properties tab (node selected) + Graph tab ────────────

    private static void renderGraphInspector(File outDir) throws Exception {
        final PreviewGraphInspectorDialog[] holder = new PreviewGraphInspectorDialog[1];

        SwingUtilities.invokeAndWait(() -> {
            PreviewGraphInspectorDialog g = new PreviewGraphInspectorDialog(null, defaultConfig());
            g.pack();
            holder[0] = g;
        });
        // Let the node-loading SwingWorker post rows to the table.
        Thread.sleep(1200);

        // Select the first row so the property inspector + neighbourhood populate.
        SwingUtilities.invokeAndWait(() -> {
            JTable main = findMainTable(holder[0].getContentPane());
            if (main != null && main.getRowCount() > 0) {
                main.setRowSelectionInterval(0, 0);
            }
        });
        // Let the neighbourhood SwingWorker render into the GraphPanel.
        Thread.sleep(1200);

        SwingUtilities.invokeAndWait(() -> {
            try {
                JComponent content = (JComponent) holder[0].getContentPane();
                JTabbedPane tabs = findTabbedPane(content);
                if (tabs == null) {
                    writePng(content, new File(outDir, "graph-inspector.png"));
                    return;
                }
                tabs.setSelectedIndex(0);
                content.validate(); content.doLayout();
                writePng(content, new File(outDir, "graph-inspector-properties.png"));

                tabs.setSelectedIndex(1);
                content.validate(); content.doLayout();
                writePng(content, new File(outDir, "graph-inspector-graph.png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ── Rendering helpers ───────────────────────────────────────────────────────

    private static void writePng(JComponent comp, File file) throws Exception {
        Dimension size = comp.getSize();
        if (size.width <= 0 || size.height <= 0) size = comp.getPreferredSize();
        BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        comp.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    private static JTabbedPane findTabbedPane(Component c) {
        if (c instanceof JTabbedPane) return (JTabbedPane) c;
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                JTabbedPane found = findTabbedPane(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** The inspector's node table is the first JTable with >= 5 columns
     *  (the 2-column property table is skipped). */
    private static JTable findMainTable(Component c) {
        if (c instanceof JTable && ((JTable) c).getColumnCount() >= 5) return (JTable) c;
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                JTable found = findMainTable(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ── Make UAFNeo4jPlugin.getInstance().getConfig() work off-MSOSA ────────────
    // The dialogs read config from the plugin singleton in their constructors. We
    // can't call Plugin.init() (it touches MSOSA), so seed instance + config
    // directly. Mirrors the defaults in UAFNeo4jPlugin.loadConfig().

    private static void seedPluginSingleton() throws Exception {
        UAFNeo4jPlugin plugin = new UAFNeo4jPlugin();

        Field configField = UAFNeo4jPlugin.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(plugin, defaultConfig());

        Field instanceField = UAFNeo4jPlugin.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, plugin);
    }

    private static Properties defaultConfig() {
        Properties p = new Properties();
        p.setProperty("neo4j.uri", "bolt://localhost:7687");
        p.setProperty("neo4j.user", "neo4j");
        p.setProperty("neo4j.password", "Password123");
        p.setProperty("neo4j.database", "neo4j");
        p.setProperty("neo4j.batch.size", "500");
        p.setProperty("neo4j.max.connections", "10");
        p.setProperty("export.tagged.values", "true");
        p.setProperty("export.relationships", "true");
        p.setProperty("export.instance.links", "true");
        p.setProperty("export.language.uaf", "true");
        p.setProperty("export.language.sysml", "true");
        p.setProperty("export.language.bpmn", "true");
        p.setProperty("export.target.lpg", "true");
        p.setProperty("export.target.rdf", "false");
        p.setProperty("fuseki.push.enabled", "false");
        p.setProperty("rdf.output.path", System.getProperty("user.home") + "/uaf-instance.ttl");
        p.setProperty("fuseki.url", "http://localhost:3030/uaf");
        p.setProperty("fuseki.sparql", "http://localhost:3030/uaf/sparql");
        p.setProperty("fuseki.user", "admin");
        p.setProperty("fuseki.password", "Password123");
        return p;
    }

    private UiPreview() {}
}
