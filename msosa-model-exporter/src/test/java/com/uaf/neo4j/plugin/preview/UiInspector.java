package com.uaf.neo4j.plugin.preview;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Debug overlay for the interactive UI preview. Hovering any widget highlights
 * it and shows the code behind it; clicking prints the same to the console.
 *
 * <p>Works with any {@link Window} that is also a {@link RootPaneContainer} —
 * {@code JDialog}, {@code JFrame}, {@code JInternalFrame}, {@code JApplet}.
 * The legacy export / inspect dialogs and the new {@code UAFWorkbench} frame
 * both qualify.
 *
 * <p>Resolution is two-tiered, both purely reflective / source-scanning so
 * production windows need no annotations or changes:
 * <ol>
 *   <li><b>Field-backed widgets</b> (text fields, checkboxes, buttons, tables,
 *       the package-checkbox map, …) resolve to {@code Class.field · Type ·
 *       File:line} by reflecting over the window's declared fields.</li>
 *   <li><b>Inline widgets</b> created in builder methods (section labels, the
 *       "Test Connection" button, headings, …) carry no field, so they resolve
 *       by their text: the inspector greps the window's source for the string
 *       literal and reports {@code "text" · Type · File:line}.</li>
 * </ol>
 * The closest match on the component's ancestor chain wins; pure layout
 * scaffolding with neither a field nor text falls through to its nearest
 * resolvable ancestor.
 */
public final class UiInspector {

    private final Window window;     // also a RootPaneContainer; cast where needed
    private final List<String> sourceRoots;
    private final List<Class<?>> sourceClasses = new ArrayList<>();
    private final Map<Component, FieldRef> index = new IdentityHashMap<>();
    private final Map<String, List<String>> sourceCache = new HashMap<>();
    private final Glass glass = new Glass();
    private final boolean verbose;
    private final boolean dump;

    private UiInspector(Window window) {
        this.window = window;
        // Allow multiple source roots — main + test — so the inspector resolves
        // both production widgets and preview-only subclasses (e.g. PreviewUAFWorkbench).
        String roots = System.getProperty("preview.srcRoot", "src/main/java,src/test/java");
        this.sourceRoots = new ArrayList<>();
        for (String r : roots.split(",")) {
            String t = r.trim();
            if (!t.isEmpty()) sourceRoots.add(t);
        }
        this.verbose = Boolean.getBoolean("preview.inspect.verbose");
        this.dump    = Boolean.getBoolean("preview.inspect.dump");
        // Walk up the host's class hierarchy stopping at the framework windows
        // — anything declared in user code is fair game for reflective indexing.
        Class<?> c = window.getClass();
        while (c != null
            && c != JDialog.class && c != JFrame.class
            && c != Dialog.class  && c != Frame.class) {
            sourceClasses.add(c);
            c = c.getSuperclass();
        }
        buildIndex();
        if (dump) dumpTree();
    }

    /**
     * Installs the overlay on a (realised) Swing top-level window and returns
     * the inspector. The window must implement {@link RootPaneContainer} —
     * {@code JFrame}, {@code JDialog}, {@code JInternalFrame}, {@code JApplet}.
     *
     * <p>System properties recognised:
     * <ul>
     *   <li>{@code -Dpreview.inspect.verbose=true} — print every hover to console
     *       (safe: hovering doesn't fire the widget's action).</li>
     *   <li>{@code -Dpreview.inspect.dump=true} — print the full component tree
     *       at install time.</li>
     *   <li>{@code -Dpreview.srcRoot=path1,path2} — source roots searched for
     *       field declarations and inline text literals
     *       (default {@code src/main/java,src/test/java}).</li>
     * </ul>
     */
    public static UiInspector install(Window window) {
        if (!(window instanceof RootPaneContainer)) {
            throw new IllegalArgumentException(
                "UiInspector requires a RootPaneContainer (JFrame/JDialog/JInternalFrame/JApplet); got "
                    + window.getClass().getName());
        }
        UiInspector insp = new UiInspector(window);
        RootPaneContainer host = (RootPaneContainer) window;
        host.setGlassPane(insp.glass);
        insp.glass.setOpaque(false);
        insp.glass.setVisible(true);
        Toolkit.getDefaultToolkit().addAWTEventListener(insp::onAwtEvent,
            AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        System.out.println("[ui-inspector] hover a widget to see the field/class/source behind it; "
            + "click it to print a stack-trace-style reference your IDE can navigate to."
            + (insp.verbose ? " [verbose: hovers also printed]" : "")
            + (insp.dump    ? " [dump: component tree printed above]" : ""));
        return insp;
    }

    // ── Live hover / click ──────────────────────────────────────────────────────

    /** Last hovered component — used so verbose mode doesn't spam on every pixel. */
    private Component lastHovered;

    private void onAwtEvent(AWTEvent ev) {
        if (!(ev instanceof MouseEvent)) return;
        MouseEvent me = (MouseEvent) ev;
        if (SwingUtilities.getWindowAncestor(me.getComponent()) != window) return;

        if (me.getID() == MouseEvent.MOUSE_MOVED) {
            Hit h = resolveAt(me);
            if (h == null) {
                glass.set(null, null);
                lastHovered = null;
                return;
            }
            Rectangle r = SwingUtilities.convertRectangle(h.comp.getParent(), h.comp.getBounds(), glass);
            glass.set(r, h.label);
            if (verbose && h.comp != lastHovered) {
                System.out.println(formatClickable("hover", h));
                lastHovered = h.comp;
            }
        } else if (me.getID() == MouseEvent.MOUSE_CLICKED) {
            Hit h = resolveAt(me);
            if (h != null) System.out.println(formatClickable("click", h));
        }
    }

    /**
     * Stack-trace format that IntelliJ / VS Code / Cursor terminals recognise as
     * a click-to-open hyperlink. The middle line ("via text") only appears when
     * the inline-text resolver added context the field-resolver didn't have.
     */
    private String formatClickable(String kind, Hit h) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ui-inspector] ").append(kind).append(": ").append(h.headline()).append('\n');
        for (String frame : h.stackFrames()) sb.append("    at ").append(frame).append('\n');
        return sb.toString();
    }

    private Container contentPane() {
        return ((RootPaneContainer) window).getContentPane();
    }

    /**
     * Resolve the deepest component under the cursor. Gathers as much
     * traceable context as possible: a direct field match (if indexed), a
     * matching text literal (if the widget shows text), and the nearest
     * field-backed ancestor for fallback context. The hover overlay shows the
     * single most useful label; the click output reports every located frame.
     */
    private Hit resolveAt(MouseEvent me) {
        Container content = contentPane();
        Point p = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), content);
        Component deepest = SwingUtilities.getDeepestComponentAt(content, p.x, p.y);
        if (deepest == null) return null;

        FieldRef directField = index.get(deepest);
        String text = anyText(deepest);
        SourceRef textSrc = isUsableText(text) ? findTextSource(text) : null;

        FieldRef ancestorField = null;
        for (Component c = deepest.getParent(); c != null; c = c.getParent()) {
            FieldRef fr = index.get(c);
            if (fr != null) { ancestorField = fr; break; }
        }

        return new Hit(deepest, text, directField, ancestorField, textSrc);
    }

    /** Forces the highlight onto a named field or a text literal (screenshot demos). */
    public boolean highlightField(String spec) {
        for (Map.Entry<Component, FieldRef> e : index.entrySet()) {
            if (e.getValue().fieldName.equals(spec)) {
                Component c = e.getKey();
                Hit h = new Hit(c, anyText(c), e.getValue(), null, null);
                highlight(c, h.label);
                return true;
            }
        }
        Component c = findByText(contentPane(), spec);
        if (c != null) {
            Hit h = new Hit(c, spec, null, null, findTextSource(spec));
            highlight(c, h.label);
            return true;
        }
        return false;
    }

    private void highlight(Component c, String label) {
        Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), glass);
        glass.set(r, label);
    }

    private Component findByText(Component c, String spec) {
        if (spec.equals(anyText(c))) return c;
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                Component found = findByText(child, spec);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Carrier — a hit can match by direct field, by text, by ancestor, or any combination. */
    private static final class Hit {
        final Component comp;
        final String text;            // displayed text, if any (button/label)
        final FieldRef directField;   // index entry for this exact component
        final FieldRef ancestorField; // index entry for the nearest enclosing field
        final SourceRef textSource;   // source location of the text literal, if found
        final String label;           // single-line label for the glass overlay

        Hit(Component comp, String text, FieldRef directField, FieldRef ancestorField, SourceRef textSource) {
            this.comp = comp;
            this.text = text;
            this.directField = directField;
            this.ancestorField = ancestorField;
            this.textSource = textSource;
            this.label = buildLabel();
        }

        /** Headline shown to humans: "JButton 'Save Config' — ExportConfigDialog.saveConfigBtn". */
        String headline() {
            StringBuilder sb = new StringBuilder(comp.getClass().getSimpleName());
            if (text != null && !text.isEmpty()) {
                String shown = text.length() > 60 ? text.substring(0, 59) + "…" : text;
                sb.append(" \"").append(shown).append('"');
            }
            if (directField != null) {
                sb.append(" — ").append(directField.declaringClassSimple).append('.').append(directField.fieldName)
                  .append(directField.key);
            } else if (ancestorField != null) {
                sb.append(" (descendant of ").append(ancestorField.declaringClassSimple)
                  .append('.').append(ancestorField.fieldName).append(ancestorField.key).append(')');
            }
            return sb.toString();
        }

        /** Stack-trace frames, in priority order. IDEs hyperlink each line. */
        List<String> stackFrames() {
            List<String> frames = new ArrayList<>();
            if (directField != null && directField.line > 0) {
                frames.add(directField.declaringClassFqn + ".<init>("
                    + directField.declaringClassSimple + ".java:" + directField.line + ")");
            }
            if (textSource != null) {
                frames.add(textSource.classFqn + ".<init>(" + textSource.classSimple + ".java:" + textSource.line + ")");
            }
            if (directField == null && ancestorField != null && ancestorField.line > 0) {
                frames.add(ancestorField.declaringClassFqn + ".<init>("
                    + ancestorField.declaringClassSimple + ".java:" + ancestorField.line + ")");
            }
            return frames;
        }

        private String buildLabel() {
            StringBuilder sb = new StringBuilder();
            if (text != null && !text.isEmpty()) {
                String shown = text.length() > 30 ? text.substring(0, 29) + "…" : text;
                sb.append('"').append(shown).append("\"  ·  ");
            }
            sb.append(comp.getClass().getSimpleName());
            if (directField != null) {
                sb.append("  ·  ").append(directField.declaringClassSimple).append('.')
                  .append(directField.fieldName).append(directField.key);
                if (directField.line > 0) sb.append("  ·  ")
                    .append(directField.declaringClassSimple).append(".java:").append(directField.line);
            } else if (textSource != null) {
                sb.append("  ·  ").append(textSource.classSimple).append(".java:").append(textSource.line);
            } else if (ancestorField != null) {
                sb.append("  ·  ↳ ").append(ancestorField.declaringClassSimple).append('.')
                  .append(ancestorField.fieldName).append(ancestorField.key);
            }
            return sb.toString();
        }
    }

    /** A located source position — used for inline text literals. */
    private static final class SourceRef {
        final String classFqn;
        final String classSimple;
        final int line;
        SourceRef(String classFqn, String classSimple, int line) {
            this.classFqn = classFqn; this.classSimple = classSimple; this.line = line;
        }
    }

    // ── Text resolution (inline widgets) ────────────────────────────────────────

    /** Text the widget actually displays — checked in order of likely usefulness. */
    private static String anyText(Component c) {
        if (c instanceof JLabel) {
            String t = ((JLabel) c).getText();
            if (t != null && !t.isEmpty()) return t;
        }
        if (c instanceof AbstractButton) {
            String t = ((AbstractButton) c).getText();
            if (t != null && !t.isEmpty()) return t;
        }
        if (c instanceof JComponent) {
            String tip = ((JComponent) c).getToolTipText();
            if (tip != null && !tip.isEmpty()) return tip;
        }
        String name = c.getName();
        if (name != null && !name.isEmpty()) return name;
        return null;
    }

    private static boolean isUsableText(String t) {
        return t != null && !t.isEmpty() && !t.toLowerCase().startsWith("<html");
    }

    /** Locate the first source line containing the quoted string literal. */
    private SourceRef findTextSource(String text) {
        if (text == null) return null;
        String needle = "\"" + text + "\"";
        for (Class<?> cls : sourceClasses) {
            List<String> lines = sourceLines(cls);
            if (lines == null) continue;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(needle)) {
                    return new SourceRef(cls.getName(), cls.getSimpleName(), i + 1);
                }
            }
        }
        return null;
    }

    // ── Build component → field index by reflection ─────────────────────────────

    private void buildIndex() {
        for (Class<?> cls : sourceClasses) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object val;
                try { val = f.get(window); } catch (Exception e) { continue; }
                indexValue(cls, f.getName(), "", val);
            }
        }
    }

    private void indexValue(Class<?> declaring, String fieldName, String key, Object val) {
        if (val == null) return;
        if (val instanceof Component) {
            Component comp = (Component) val;
            FieldRef fr = new FieldRef(
                declaring.getName(), declaring.getSimpleName(),
                fieldName, key, comp.getClass().getSimpleName(),
                findLine(declaring, fieldName));
            index.putIfAbsent(comp, fr);
            // Walk into the subtree so deep widgets get traceable attribution
            // back to the field that owns them, not just the component-tree root.
            indexDescendants(declaring, fieldName, key, comp);
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

    /**
     * Index every descendant of a field-backed container so hovering deep
     * widgets resolves to {@code field.children[i].children[j]...} instead of
     * falling through to a far ancestor with mismatched semantics.
     *
     * <p>Children that have their OWN direct field entry are left alone —
     * {@code putIfAbsent} means a more-specific match wins.
     */
    private void indexDescendants(Class<?> declaring, String fieldName, String parentKey, Component parent) {
        if (!(parent instanceof Container)) return;
        Component[] children = ((Container) parent).getComponents();
        for (int i = 0; i < children.length; i++) {
            Component child = children[i];
            String childKey = parentKey + ".children[" + i + "]";
            index.putIfAbsent(child, new FieldRef(
                declaring.getName(), declaring.getSimpleName(),
                fieldName, childKey,
                child.getClass().getSimpleName(),
                findLine(declaring, fieldName)));
            indexDescendants(declaring, fieldName, childKey, child);
        }
        // JScrollPane's viewport view isn't returned by getComponents() in the
        // useful order; peek through it explicitly so embedded JTables etc.
        // appear in the index.
        if (parent instanceof javax.swing.JScrollPane) {
            javax.swing.JViewport vp = ((javax.swing.JScrollPane) parent).getViewport();
            Component view = vp == null ? null : vp.getView();
            if (view != null && !index.containsKey(view)) {
                String viewKey = parentKey + ".viewport.view";
                index.putIfAbsent(view, new FieldRef(
                    declaring.getName(), declaring.getSimpleName(),
                    fieldName, viewKey,
                    view.getClass().getSimpleName(),
                    findLine(declaring, fieldName)));
                indexDescendants(declaring, fieldName, viewKey, view);
            }
        }
    }

    // ── Tree dump (boot-time debug aid) ─────────────────────────────────────────

    private void dumpTree() {
        System.out.println("[ui-inspector] component tree of " + window.getClass().getName() + ":");
        dumpComponent((Component) ((RootPaneContainer) window).getContentPane(), 0);
    }

    private void dumpComponent(Component c, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");
        FieldRef fr = index.get(c);
        StringBuilder line = new StringBuilder();
        line.append(indent).append(c.getClass().getSimpleName());
        String t = anyText(c);
        if (isUsableText(t)) {
            String shown = t.length() > 40 ? t.substring(0, 39) + "…" : t;
            line.append(" \"").append(shown).append('"');
        }
        if (fr != null) line.append("  — ").append(fr.declaringClassSimple).append('.')
                            .append(fr.fieldName).append(fr.key);
        System.out.println(line);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) dumpComponent(child, depth + 1);
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
        for (String root : sourceRoots) {
            File f = new File(root, rel);
            if (f.isFile()) {
                try { lines = Files.readAllLines(f.toPath()); break; } catch (Exception ignored) { }
            }
        }
        sourceCache.put(name, lines);
        return lines;
    }

    // ── Model ───────────────────────────────────────────────────────────────────

    private static final class FieldRef {
        final String declaringClassFqn;    // for IDE-clickable stack-trace frames
        final String declaringClassSimple; // for the on-screen label
        final String fieldName;
        final String key;
        final String type;
        final int line;

        FieldRef(String declaringClassFqn, String declaringClassSimple,
                 String fieldName, String key, String type, int line) {
            this.declaringClassFqn    = declaringClassFqn;
            this.declaringClassSimple = declaringClassSimple;
            this.fieldName            = fieldName;
            this.key                  = key;
            this.type                 = type;
            this.line                 = line;
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
