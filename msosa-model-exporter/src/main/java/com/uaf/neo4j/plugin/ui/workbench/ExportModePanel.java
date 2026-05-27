package com.uaf.neo4j.plugin.ui.workbench;

import com.uaf.neo4j.plugin.ui.ExportConfigDialog;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * Export mode — embeds the full Export form directly. The form is built by
 * {@link ExportConfigDialog} in {@code embedded=true} mode; the dialog object
 * exists only as a controller and is never shown. Cancel and Browse Graph are
 * suppressed in this mode because the workbench owns navigation.
 *
 * <p>Construction goes through {@link UAFWorkbench#createExportDialog} so the
 * preview harness (and any future test) can swap in a sample-data subclass.
 * When the factory returns {@code null} (no MSOSA project) the panel shows a
 * notice instead.
 */
final class ExportModePanel extends JPanel implements WorkbenchPanel {

    @SuppressWarnings("unused") // held to keep the dialog instance alive for action listeners
    private final ExportConfigDialog controllerDialog;

    ExportModePanel(UAFWorkbench workbench) {
        super(new BorderLayout());
        setBackground(Color.WHITE);

        ExportConfigDialog dialog = workbench.createExportDialog(workbench.getProject());
        if (dialog == null) {
            controllerDialog = null;
            add(buildNoProjectNotice(), BorderLayout.CENTER);
            return;
        }

        controllerDialog = dialog;
        add(controllerDialog.getEmbeddedBody(), BorderLayout.CENTER);
    }

    private JPanel buildNoProjectNotice() {
        JPanel notice = new JPanel(new BorderLayout());
        notice.setBackground(Color.WHITE);
        notice.setBorder(new EmptyBorder(40, 32, 40, 32));

        JLabel title = new JLabel("Export");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JLabel msg = new JLabel("<html><body style='width:520px'>"
            + "No MSOSA project is open. Open a UAF model and reopen the "
            + "workbench to use the exporter.</body></html>");
        msg.setForeground(new Color(80, 80, 80));
        msg.setFont(msg.getFont().deriveFont(Font.PLAIN, 12f));
        msg.setBorder(new EmptyBorder(12, 0, 0, 0));

        notice.add(title, BorderLayout.NORTH);
        notice.add(msg,   BorderLayout.CENTER);
        return notice;
    }

    @Override public JComponent getComponent() { return this; }
    @Override public WorkbenchMode getMode()   { return WorkbenchMode.EXPORT; }
}
