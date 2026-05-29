package com.uaf.neo4j.plugin.ui.workbench;

import com.uaf.neo4j.plugin.UAFNeo4jPlugin;
import com.uaf.neo4j.plugin.export.ExportResult;
import com.uaf.neo4j.plugin.rdf.FusekiClient;
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
    private final JTextArea report = new JTextArea(IDLE_TEXT);
    private final JButton runBtn   = new JButton("Run SHACL Validation");

    ValidateModePanel(UAFWorkbench workbench) {
        this.workbench = workbench;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        runBtn.setToolTipText(
            "Snapshot Fuseki via SPARQL CONSTRUCT and validate against the bundled UAF SHACL shapes.");
        runBtn.addActionListener(e -> runValidation());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(runBtn);

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
     * during the CONSTRUCT round-trip and the OWL FB closure (which can take a
     * couple of seconds on a large dataset).
     */
    private void runValidation() {
        runBtn.setEnabled(false);
        report.setText("Running…\n\n"
                     + "  → Fetching dataset from Fuseki…\n");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    Properties cfg = UAFNeo4jPlugin.getInstance().getConfig();
                    String fusekiUrl = cfg.getProperty("fuseki.url",      "http://localhost:3030/uaf");
                    String fusekiUsr = cfg.getProperty("fuseki.user",     "");
                    String fusekiPwd = cfg.getProperty("fuseki.password", "");

                    FusekiClient client = new FusekiClient(fusekiUrl, fusekiUsr, fusekiPwd);
                    String turtle = client.constructAll();

                    Model data = ModelFactory.createDefaultModel();
                    RDFParser.create()
                        .source(new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)))
                        .lang(Lang.TURTLE)
                        .parse(data);

                    ExportResult result = new ExportResult();
                    ShaclValidationService.validateAndAttach(data, result);
                    return formatReport(fusekiUrl, data.size(), result);
                } catch (Exception ex) {
                    return formatError(ex);
                }
            }

            @Override
            protected void done() {
                try {
                    report.setText(get());
                    report.setCaretPosition(0);
                } catch (Exception ex) {
                    report.setText(formatError(ex));
                } finally {
                    runBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private static String formatReport(String fusekiUrl, long tripleCount, ExportResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Source: ").append(fusekiUrl).append("/sparql\n");
        sb.append("Snapshot: ").append(tripleCount).append(" triples\n");
        if (r.shaclConformance == null) {
            sb.append("Result: SHACL validator did not run.\n");
            if (!r.errors.isEmpty()) {
                sb.append("\nErrors:\n");
                for (String e : r.errors) sb.append("  ").append(e).append('\n');
            }
            return sb.toString();
        }
        sb.append("Result: ").append(r.shaclConformance ? "CONFORMS" : "DOES NOT CONFORM").append('\n');
        sb.append("Violations: ").append(r.shaclViolations).append('\n');
        sb.append("Warnings:   ").append(r.shaclWarnings).append('\n');
        if (r.shaclViolationLines.isEmpty()) {
            sb.append("\n(no actionable findings against URI-named uafinst: instances)\n");
        } else {
            sb.append("\nFindings:\n");
            for (String line : r.shaclViolationLines) sb.append("  ").append(line).append('\n');
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
