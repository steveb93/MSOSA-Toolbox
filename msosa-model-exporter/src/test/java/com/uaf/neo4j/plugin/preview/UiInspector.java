package com.uaf.neo4j.plugin.preview;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Debug overlay for the interactive UI preview. Hovering any widget highlights
 * it and shows which field / class / source line builds it; clicking prints the
 * same to the console.
 *
 * <p>It works purely by reflecting over the dialog instance's fields at runtime,
 * so production dialogs need no annotations or changes. Only widgets stored in
 * fields (text fields, checkboxes, buttons, tables, etc.) are resolvable; pure
 * layout scaffolding created inline in builder methods is not, so the overlay
 * falls back to the nearest field-backed ancestor.
 */
public final class UiInspector {

    private final JDialog dialog;
    private final String sourceRoot;
    private final Map<Component, FieldRef> index = new IdentityHashMap<>();
    private final Map<String, List<String>> sourceCache = new HashMap<>();
    private final Glass glass = new Glass();

    private UiInspector(JDialog dialog) {
        this.dialog = dialog;
        this.sourceRoot = System.getProperty("preview.srcRoot", "src/main/java");
        buildIndex();
    }

    /** Installs the overlay on a (realised) dialog and returns the inspector. */
    public static UiInspector install(JDialog dialog) {
        UiInspector insp = new UiInspector(dialog);
        dialog.setGlassPane(insp.glass);
        insp.glass.setOpaque(false);
        insp.glass.setVisible(true);
        Toolkit.getDefaultToolkit().addAWTEventListener(insp::onAwtEvent,
            AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        System.out.println("[ui-inspector] hover a widget to see the field/class/source behind it; "
            + "click it to print the reference.");
        return insp;
    }

    // ── Live hover / click ──────────────────────────────────────────────────────

    private void onAwtEvent(AWTEvent ev) {
        if (!(ev instanceof MouseEvent)) return;
        MouseEvent me = (MouseEvent) ev;
        if (SwingUtilities.getWindowAncestor(me.getComponent()) != dialog) return;

        if (me.getID() == MouseEvent.MOUSE_MOVED) {
            Component c = fieldBackedAt(me);
            if (c == null) { glass.set(null, null); return; }
            Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), glass);
            glass.set(r, index.get(c).label());
        } else if (me.getID() == MouseEvent.MOUSE_CLICKED) {
            Component c = fieldBackedAt(me);
            if (c != null) System.out.println("[ui-inspector] " + index.get(c).label());
        }
    }

    private Component fieldBackedAt(MouseEvent me) {
        Container content = dialog.getContentPane();
        Point p = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), content);
        Component c = SwingUtilities.getDeepestComponentAt(content, p.x, p.y);
        while (c != null && !index.containsKey(c)) c = c.getParent();
        return c;
    }

    /** Forces the highlight onto a named field (for headless screenshot demos). */
    public boolean highlightField(String fieldName) {
        for (Map.Entry<Component, FieldRef> e : index.entrySet()) {
            if (e.getValue().fieldName.equals(fieldName)) {
                Component c = e.getKey();
                Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), glass);
                glass.set(r, e.getValue().label());
                return true;
            }
        }
        return false;
    }

    // ── Build component → field index by reflection ─────────────────────────────

    private void buildIndex() {
        Class<?> c = dialog.getClass();
        while (c != null && c != JDialog.class && c != Dialog.class) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object val;
                try { val = f.get(dialog); } catch (Exception e) { continue; }
                indexValue(c, f.getName(), "", val);
            }
            c = c.getSuperclass();
        }
    }

    private void indexValue(Class<?> declaring, String fieldName, String key, Object val) {
        if (val == null) return;
        if (val instanceof Component) {
            Component comp = (Component) val;
            index.putIfAbsent(comp, new FieldRef(
                declaring.getSimpleName(), fieldName, key,
                comp.getClass().getSimpleName(), findLine(declaring, fieldName)));
        } else if (val instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) val).entrySet()) {
                indexValue(declaring, fieldName, "[\"" + e.getKey() + "\"]", e.getValue());
            }
        } else if (val instanceof Iterable) {
            int i = 0;
            for (Object o : (Iterable<?>) val) indexValue(declaring, fieldName, "[" + (i++) + "]", o);
        } else if (val instanceof Object[]) {
            Object[] arr = (Object[]) val;
            for (int i = 0; i < arr.length; i++) indexValue(declaring, fieldName, "[" + i + "]", arr[i]);
        }
    }

    // ── Source-line lookup (best effort) ────────────────────────────────────────

    private int findLine(Class<?> declaring, String fieldName) {
        List<String> lines = sourceLines(declaring);
        if (lines == null) return -1;
        Pattern decl = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\b\\s*[;=]");
        Pattern modifier = Pattern.compile("\\b(private|protected|public|final|static)\\b");
        int firstAny = -1;
        for (int i = 0; i < lines.size(); i++) {
            String ln = lines.get(i);
            if (decl.matcher(ln).find()) {
                if (firstAny < 0) firstAny = i + 1;
                if (modifier.matcher(ln).find()) return i + 1; // prefer the declaration line
            }
        }
        return firstAny;
    }

    private List<String> sourceLines(Class<?> declaring) {
        String name = declaring.getName();
        if (sourceCache.containsKey(name)) return sourceCache.get(name);
        String rel = name.replace('.', '/');
        int dollar = rel.indexOf('$');
        if (dollar >= 0) rel = rel.substring(0, dollar);
        rel += ".java";
        List<String> lines = null;
        File f = new File(sourceRoot, rel);
        if (f.isFile()) {
            try { lines = Files.readAllLines(f.toPath()); } catch (Exception ignored) { }
        }
        sourceCache.put(name, lines);
        return lines;
    }

    // ── Model ───────────────────────────────────────────────────────────────────

    private static final class FieldRef {
        final String declaringClass;
        final String fieldName;
        final String key;
        final String type;
        final int line;

        FieldRef(String declaringClass, String fieldName, String key, String type, int line) {
            this.declaringClass = declaringClass;
            this.fieldName = fieldName;
            this.key = key;
            this.type = type;
            this.line = line;
        }

        String label() {
            String loc = line > 0 ? "  ·  " + declaringClass + ".java:" + line : "";
            return declaringClass + "." + fieldName + key + "  ·  " + type + loc;
        }
    }

    // ── Transparent overlay (pass-through to widgets below) ─────────────────────

    private static final class Glass extends JComponent {
        private Rectangle hi;
        private String text;

        void set(Rectangle r, String t) { this.hi = r; this.text = t; repaint(); }

        @Override public boolean contains(int x, int y) { return false; } // never intercept input

        @Override
        protected void paintComponent(Graphics g) {
            if (hi == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(255, 90, 40, 55));
            g2.fillRect(hi.x, hi.y, hi.width, hi.height);
            g2.setColor(new Color(230, 70, 30));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(hi.x, hi.y, hi.width, hi.height);

            if (text != null && !text.isEmpty()) {
                g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(text) + 12;
                int th = fm.getHeight() + 6;
                int tx = hi.x;
                int ty = hi.y - th - 2;
                if (ty < 2) ty = hi.y + hi.height + 2;
                if (tx + tw > getWidth()) tx = Math.max(2, getWidth() - tw - 2);
                if (tx < 2) tx = 2;
                g2.setColor(new Color(35, 35, 35, 238));
                g2.fillRoundRect(tx, ty, tw, th, 7, 7);
                g2.setColor(Color.WHITE);
                g2.drawString(text, tx + 6, ty + fm.getAscent() + 3);
            }
            g2.dispose();
        }
    }
}
