package com.uaf.neo4j.plugin.ui.workbench;

import com.uaf.neo4j.plugin.neo4j.Neo4jExportService;
import com.uaf.neo4j.plugin.rdf.FusekiClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Properties;

/**
 * Bottom strip showing live Neo4j and Fuseki connection health. Probes are
 * fired on demand (constructor, refresh button, after a Settings save) — no
 * background polling, no MSOSA-thread interference.
 */
public final class StatusStrip extends JPanel {

    private static final Color OK_GREEN    = new Color(0, 130, 0);
    private static final Color FAIL_RED    = new Color(180, 30, 30);
    private static final Color UNKNOWN_GRAY = new Color(120, 120, 120);
    private static final Color STRIP_BG    = new Color(245, 246, 248);
    private static final Color BORDER      = new Color(218, 219, 224);

    private final JLabel neo4jLabel = pill("Neo4j: …");
    private final JLabel fusekiLabel = pill("Fuseki: …");
    private final JButton refreshBtn = new JButton("Recheck");

    private Properties config;

    public StatusStrip(Properties config) {
        super(new BorderLayout());
        this.config = config;
        setBackground(STRIP_BG);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(4, 10, 4, 10)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(neo4jLabel);
        left.add(fusekiLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        refreshBtn.setFocusable(false);
        refreshBtn.setToolTipText("Re-probe Neo4j and Fuseki to refresh the status pills");
        refreshBtn.addActionListener(e -> refresh());
        right.add(refreshBtn);

        add(left,  BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        refresh();
    }

    /** Swap the live config (Settings panel calls this after saving). */
    public void setConfig(Properties config) {
        this.config = config;
        refresh();
    }

    public void refresh() {
        probeNeo4j();
        probeFuseki();
    }

    private void probeNeo4j() {
        String uri = config.getProperty("neo4j.uri", "");
        neo4jLabel.setForeground(UNKNOWN_GRAY);
        neo4jLabel.setText("Neo4j: …  " + uri);

        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try (Neo4jExportService svc = new Neo4jExportService(config)) {
                    svc.init();
                    return svc.testConnection();
                } catch (Exception ex) {
                    return false;
                }
            }
            @Override protected void done() {
                try {
                    boolean ok = get();
                    neo4jLabel.setForeground(ok ? OK_GREEN : FAIL_RED);
                    neo4jLabel.setText((ok ? "Neo4j ✓  " : "Neo4j ✗  ") + uri);
                } catch (Exception e) {
                    neo4jLabel.setForeground(FAIL_RED);
                    neo4jLabel.setText("Neo4j ✗  " + uri);
                }
            }
        }.execute();
    }

    private void probeFuseki() {
        String url = config.getProperty("fuseki.url", "");
        fusekiLabel.setForeground(UNKNOWN_GRAY);
        fusekiLabel.setText("Fuseki: …  " + url);

        String user = config.getProperty("fuseki.user", "");
        String pwd  = config.getProperty("fuseki.password", "");
        new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() {
                try {
                    return new FusekiClient(url, user, pwd).testConnection();
                } catch (Exception ex) {
                    return false;
                }
            }
            @Override protected void done() {
                try {
                    boolean ok = get();
                    fusekiLabel.setForeground(ok ? OK_GREEN : FAIL_RED);
                    fusekiLabel.setText((ok ? "Fuseki ✓  " : "Fuseki ✗  ") + url);
                } catch (Exception e) {
                    fusekiLabel.setForeground(FAIL_RED);
                    fusekiLabel.setText("Fuseki ✗  " + url);
                }
            }
        }.execute();
    }

    private static JLabel pill(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
        return l;
    }
}
