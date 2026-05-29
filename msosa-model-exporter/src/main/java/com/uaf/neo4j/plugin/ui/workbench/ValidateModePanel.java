package com.uaf.neo4j.plugin.ui.workbench;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.rdf.FusekiClient;
import com.uaf.neo4j.plugin.rdf.ShaclReport;
import com.uaf.neo4j.plugin.rdf.ShaclValidationService;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Validate mode — runs SHACL shapes from {@code ontology/shapes/uaf-shapes.ttl}
 * against the live Fuseki dataset, in-process via the shaded {@code org.apache.jena.shacl}
 * engine. No MCP / Python toolchain in the critical path.
 *
 * <p>Flow on button click:
 * <ol>
 *   <li>Issue a SPARQL CONSTRUCT against Fuseki to snapshot the current dataset
 *       (asserted + reasoner-inferred under the dataset's assembler config).</li>
 *   <li>Parse the Turtle response into a Jena {@link Model}.</li>
 *   <li>Pass the model to {@link ShaclValidationService#validateAndAttach(Model, ExportResult)},
 *       which loads the bundled MVO + axioms + shapes, wraps with OWL FB, and runs
 *       {@code ShaclValidator}.</li>
 *   <li>Render the verdict + violation lines in the report area.</li>
 * </ol>
 */
final class ValidateModePanel extends JPanel implements WorkbenchPanel {

    private static final String IDLE_TEXT =
          "No validation run yet.\n\n"
        + "Click Run SHACL Validation to:\n"
        + "  1. Snapshot the live Fuseki dataset via SPARQL CONSTRUCT,\n"
        + "  2. Validate it against ontology/shapes/uaf-shapes.ttl under OWL FB reasoning,\n"
        + "  3. Render the conformance verdict and any violation lines here.\n\n"
        + "Connection settings live under the Settings rail.";

    @SuppressWarnings("unused") // held for future "validate the current model only" actions
    private final UAFWorkbench workbench;
    private final JTextArea report  = new JTextArea(IDLE_TEXT);
    private final JButton runBtn    = new JButton("Run SHACL Validation");
    private final JButton cancelBtn = new JButton("Cancel");
    private SwingWorker<String, String> currentWorker;

    ValidateModePanel(UAFWorkbench workbench) {
        this.workbench = workbench;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        runBtn.setToolTipText(
            "Snapshot Fuseki via SPARQL CONSTRUCT and validate against the bundled UAF SHACL shapes.");
        runBtn.addActionListener(e -> runValidation());

        cancelBtn.setEnabled(false);
        cancelBtn.setToolTipText(
            "Cancel the running validation. Jena may not honour the interrupt mid-reasoning, "
          + "so background work can continue until it finishes — but the UI is freed immediately.");
        cancelBtn.addActionListener(e -> cancelValidation());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(runBtn);
        buttons.add(cancelBtn);

        report.setEditable(false);
        report.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        report.setBackground(new Color(248, 249, 251));
        report.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(report);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(600, 320));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        add(heading("Validate"));
        add(Box.createVerticalStrut(8));
        add(body("Run the SHACL shapes from <code>ontology/shapes/uaf-shapes.ttl</code> against the "
               + "live Fuseki dataset. Report appears below. Runs inside the plugin via the shaded "
               + "Jena engine — no MCP server required."));
        add(Box.createVerticalStrut(18));
        add(buttons);
        add(Box.createVerticalStrut(14));
        add(scroll);
    }

    /**
     * Launch the validation pipeline on a SwingWorker so the EDT stays responsive
     * during the CONSTRUCT round-trip and the OWL FB closure. Probes Fuseki first
     * to surface the common first-run failures (unreachable, empty dataset) with
     * actionable hints rather than a stuck "Running…" message.
     *
     * <p>Each stage publishes a progress line through {@link SwingWorker#publish}
     * so the user sees forward motion (probe → snapshot triple count → reasoner →
     * SHACL) instead of a single frozen "Probing Fuseki…" string while the OWL FB
     * closure churns for minutes.
     */
    private void runValidation() {
        toggleRunning(true);
        report.setText("Running…\n");

        currentWorker = new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() {
                Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();
                String fusekiUrl = cfg.getProperty("fuseki.url",      "http://localhost:3030/uaf");
                String fusekiUsr = cfg.getProperty("fuseki.user",     "");
                String fusekiPwd = cfg.getProperty("fuseki.password", "");
                FusekiClient client = new FusekiClient(fusekiUrl, fusekiUsr, fusekiPwd);

                long t0 = System.currentTimeMillis();
                publish("  → Probing Fuseki at " + fusekiUrl + " …");
                if (!client.testConnection()) {
                    return "Fuseki unreachable at " + fusekiUrl + ".\n\n"
                         + "Check the Settings rail (Fuseki URL + credentials) and the "
                         + "status strip at the bottom of the workbench. If Fuseki is "
                         + "not running, start it via the docker-compose overlay.\n";
                }
                publish("  ✓ Fuseki reachable (" + (System.currentTimeMillis() - t0) + " ms).");
                if (isCancelled()) return cancelledMessage();

                try {
                    publish("  → Snapshotting dataset via SPARQL CONSTRUCT …");
                    long t1 = System.currentTimeMillis();
                    String turtle = client.constructAll();
                    publish("  ✓ Snapshot received: "
                          + (turtle.length() / 1024) + " KB Turtle in "
                          + (System.currentTimeMillis() - t1) + " ms.");
                    if (isCancelled()) return cancelledMessage();

                    publish("  → Parsing Turtle into Jena model …");
                    long t2 = System.currentTimeMillis();
                    Model data = ModelFactory.createDefaultModel();
                    RDFParser.create()
                        .source(new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)))
                        .lang(Lang.TURTLE)
                        .parse(data);
                    long tripleCount = data.size();
                    publish("  ✓ Parsed " + tripleCount + " triples in "
                          + (System.currentTimeMillis() - t2) + " ms.");

                    if (data.isEmpty()) {
                        return "Fuseki dataset is empty.\n\n"
                             + "Source: " + fusekiUrl + "/sparql\n\n"
                             + "Run an export with the Export rail and tick \"Also PUT to Fuseki\" "
                             + "so the dataset has something to validate against.\n";
                    }
                    if (isCancelled()) return cancelledMessage();

                    publish("  → Running OWL FB reasoner + SHACL validator …");
                    publish("    (this is the slow step — Jena may not honour cancel mid-reasoning)");
                    long t3 = System.currentTimeMillis();
                    ShaclReport result = ShaclValidationService.validate(data);
                    publish("  ✓ Validation finished in "
                          + (System.currentTimeMillis() - t3) + " ms.");
                    return formatReport(fusekiUrl, tripleCount, result);
                } catch (Exception ex) {
                    return formatError(ex);
                }
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    report.append(line + "\n");
                }
                report.setCaretPosition(report.getDocument().getLength());
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        report.setText(cancelledMessage());
                    } else {
                        report.setText(get());
                    }
                    report.setCaretPosition(0);
                } catch (java.util.concurrent.CancellationException ce) {
                    report.setText(cancelledMessage());
                } catch (Exception ex) {
                    report.setText(formatError(ex));
                } finally {
                    toggleRunning(false);
                    currentWorker = null;
                }
            }
        };
        currentWorker.execute();
    }

    private void cancelValidation() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
    }

    private void toggleRunning(boolean running) {
        runBtn.setEnabled(!running);
        cancelBtn.setEnabled(running);
    }

    private static String cancelledMessage() {
        return "Validation cancelled.\n\n"
             + "Jena does not always honour mid-reasoning interruption, so background work may "
             + "continue until it finishes naturally — but the UI is free to use.\n";
    }

    private static String formatReport(String fusekiUrl, long tripleCount, ShaclReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(fusekiUrl).append("/sparql\n");
        sb.append("Snapshot: ").append(tripleCount).append(" triples\n");
        if (r.conforms == null) {
            sb.append("Result: SHACL validator did not run.\n");
            if (!r.errors.isEmpty()) {
                sb.append("\nErrors:\n");
                for (String e : r.errors) sb.append("  ").append(e).append('\n');
            }
            return sb.toString();
        }
        sb.append("Result: ").append(r.conforms ? "CONFORMS" : "DOES NOT CONFORM").append('\n');
        sb.append("Violations: ").append(r.violations).append('\n');
        sb.append("Warnings:   ").append(r.warnings).append('\n');
        if (r.lines.isEmpty()) {
            sb.append("\n(no actionable findings against URI-named uafinst: instances)\n");
        } else {
            sb.append("\nFindings:\n");
            for (String line : r.lines) sb.append("  ").append(line).append('\n');
        }
        return sb.toString();
    }

    private static String formatError(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return "Validation failed.\n\n"
             + cause.getClass().getSimpleName() + ": " + cause.getMessage() + "\n\n"
             + "Check that Fuseki is reachable (see the Settings rail and the status strip).";
    }

    @Override public JComponent getComponent() { return this; }
    @Override public WorkbenchMode getMode()   { return WorkbenchMode.VALIDATE; }

    private static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 18f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel body(String text) {
        JLabel l = new JLabel("<html><body style='width:560px'>" + text + "</body></html>");
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        l.setForeground(new Color(80, 80, 80));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
