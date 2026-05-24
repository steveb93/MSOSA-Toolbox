package com.uaf.neo4j.plugin.preview;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
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
 * Standalone launcher for previewing the export wizard UI off-MSOSA.
 *
 * <p>Interactive (needs a display):
 * <pre>mvn -Pui-preview test-compile exec:java</pre>
 *
 * <p>Headless screenshot render (e.g. CI / cloud, run under xvfb-run):
 * <pre>mvn -Pui-preview test-compile exec:java -Dpreview.screenshot=target/ui-preview</pre>
 *
 * <p>The dialog's "Export" and "Test Connection" buttons need a live MSOSA
 * project / Neo4j and are not exercised here — this harness is for visual
 * layout iteration only.
 */
public final class UiPreview {

    public static void main(String[] args) throws Exception {
        seedPluginSingleton();

        String shotDir = System.getProperty("preview.screenshot");
        if (shotDir != null && !shotDir.isEmpty()) {
            renderScreenshots(new File(shotDir));
            System.out.println("UI preview screenshots written to " + new File(shotDir).getAbsolutePath());
            System.exit(0);
        } else {
            SwingUtilities.invokeLater(() -> {
                PreviewExportConfigDialog d = new PreviewExportConfigDialog(null);
                d.setModal(false);
                d.setDefaultCloseOperation(JDialog.EXIT_ON_CLOSE);
                d.setVisible(true);
            });
        }
    }

    // ── Render each tab of the wizard to a PNG ──────────────────────────────────

    private static void renderScreenshots(File outDir) throws Exception {
        outDir.mkdirs();
        final String[] tabFile = {"connection", "options", "preview"};

        final PreviewExportConfigDialog[] holder = new PreviewExportConfigDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            PreviewExportConfigDialog d = new PreviewExportConfigDialog(null);
            d.pack(); // realises the peer and lays out at preferred size
            holder[0] = d;
        });

        // Let the background element-count SwingWorker finish and post to the EDT
        // so package rows render as "Strategic (12)" rather than "Counting elements…".
        Thread.sleep(1200);

        SwingUtilities.invokeAndWait(() -> {
            try {
                PreviewExportConfigDialog d = holder[0];
                JComponent content = (JComponent) d.getContentPane();
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

    // ── Make UAFNeo4jPlugin.getInstance().getConfig() work off-MSOSA ────────────
    // The dialog reads config from the plugin singleton in its constructor. We
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
