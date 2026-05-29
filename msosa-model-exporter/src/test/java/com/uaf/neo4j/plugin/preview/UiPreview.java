package com.uaf.neo4j.plugin.preview;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.ui.workbench.WorkbenchMode;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
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
 * Standalone launcher for previewing the workbench off-MSOSA.
 *
 * <p>Modes:
 * <ul>
 *   <li><b>Interactive</b> (needs a display) — opens the workbench window, samples
 *       are wired through {@link PreviewUAFWorkbench}, and a {@link UiInspector}
 *       overlay is installed so hovering shows the field/class/source behind
 *       each widget.
 *       <pre>mvn -Pui-preview test-compile exec:java</pre></li>
 *   <li><b>Headless screenshot</b> — writes one PNG per rail item.
 *       <pre>mvn -Pui-preview test-compile exec:java "-Dpreview.screenshot=target/ui-preview"</pre></li>
 *   <li><b>Inspector field demo</b> — highlights one named widget and writes
 *       a single PNG of the workbench.
 *       <pre>mvn -Pui-preview test-compile exec:java "-Dpreview.inspect=exportBtn" "-Dpreview.screenshot=target/ui-preview"</pre></li>
 * </ul>
 *
 * <p>Debug flags forwarded to {@link UiInspector}:
 * {@code -Dpreview.inspect.verbose=true} (print every hover),
 * {@code -Dpreview.inspect.dump=true} (print the full component tree at start).
 *
 * <p>The "Export", "Test Connection", "Refresh" and "Locate in MSOSA" buttons
 * need a live MSOSA project / Neo4j and are not exercised here — this harness
 * is for visual layout iteration only.
 */
public final class UiPreview {

    public static void main(String[] args) throws Exception {
        seedPluginSingleton();

        String inspectField = System.getProperty("preview.inspect");
        String shotDir      = System.getProperty("preview.screenshot");

        if (shotDir != null && !shotDir.isEmpty()) {
            File outDir = new File(shotDir);
            outDir.mkdirs();
            if (inspectField != null && !inspectField.isEmpty()) {
                renderInspectDemo(inspectField, outDir);
            } else {
                renderWorkbench(outDir);
            }
            System.out.println("UI preview screenshots written to " + outDir.getAbsolutePath());
            System.exit(0);
        } else {
            SwingUtilities.invokeLater(() -> {
                PreviewUAFWorkbench wb = new PreviewUAFWorkbench();
                wb.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                wb.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override public void windowClosed(java.awt.event.WindowEvent e) { System.exit(0); }
                });
                wb.setVisible(true);
                UiInspector.install(wb);
            });
        }
    }

    // ── Workbench — one PNG per rail item ───────────────────────────────────────

    private static void renderWorkbench(File outDir) throws Exception {
        PreviewUAFWorkbench wb = newRealisedWorkbench();
        // Let StatusStrip's probes settle (they're SwingWorkers — they paint regardless of result).
        Thread.sleep(800);

        WorkbenchMode[] modes = WorkbenchMode.values();
        JList<?> rail = findRailList(wb.getContentPane());
        for (int i = 0; i < modes.length; i++) {
            final int idx = i;
            SwingUtilities.invokeAndWait(() -> {
                if (rail != null) rail.setSelectedIndex(idx);
                wb.validate();
                wb.getContentPane().doLayout();
            });
            Thread.sleep(150);
            final WorkbenchMode mode = modes[i];
            SwingUtilities.invokeAndWait(() -> {
                try {
                    writePng((JComponent) wb.getContentPane(),
                        new File(outDir, "workbench-" + mode.name().toLowerCase() + ".png"));
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
    }

    // ── Inspector field demo — highlight one named widget on the workbench ──────

    private static void renderInspectDemo(String field, File outDir) throws Exception {
        PreviewUAFWorkbench wb = newRealisedWorkbench();
        Thread.sleep(1500); // background workers (status probes, element counts, table fill)

        SwingUtilities.invokeAndWait(() -> {
            try {
                UiInspector insp = UiInspector.install(wb);
                JComponent root = wb.getRootPane();
                root.validate();
                root.doLayout();
                if (!insp.highlightField(field)) {
                    System.out.println("[ui-inspector] no field or text matching '" + field + "' on the workbench");
                }
                wb.getGlassPane().setSize(root.getSize());
                writePng(root, new File(outDir, "inspect-demo-workbench.png"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static PreviewUAFWorkbench newRealisedWorkbench() throws Exception {
        final PreviewUAFWorkbench[] holder = new PreviewUAFWorkbench[1];
        SwingUtilities.invokeAndWait(() -> {
            PreviewUAFWorkbench wb = new PreviewUAFWorkbench();
            wb.pack();
            wb.setSize(1280, 800);
            holder[0] = wb;
        });
        return holder[0];
    }

    private static JList<?> findRailList(Component c) {
        if (c instanceof JList) return (JList<?>) c;
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                JList<?> found = findRailList(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void writePng(JComponent comp, File file) throws Exception {
        Dimension size = comp.getSize();
        if (size.width <= 0 || size.height <= 0) size = comp.getPreferredSize();
        BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        comp.printAll(g);
        g.dispose();
        ImageIO.write(img, "png", file);
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
