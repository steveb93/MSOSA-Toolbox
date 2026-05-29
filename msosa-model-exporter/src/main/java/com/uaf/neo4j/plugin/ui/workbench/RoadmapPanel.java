package com.uaf.neo4j.plugin.ui.workbench;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * Shared visual treatment for modes that are reserved-but-unimplemented
 * (Federate / Insights). Renders the stage callout, a short rationale, and a
 * muted card per planned feature so the roadmap is legible in-app rather than
 * hidden inside {@code ontology/NEXT-STEPS.md}.
 */
final class RoadmapPanel extends JPanel {

    private static final Color CARD_BG     = new Color(248, 249, 251);
    private static final Color CARD_BORDER = new Color(218, 219, 224);
    private static final Color MUTED_TEXT  = new Color(120, 120, 120);
    private static final Color BODY_TEXT   = new Color(80, 80, 80);

    RoadmapPanel(String title,
                 String stage,
                 String rationale,
                 List<RoadmapItem> items) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setBackground(Color.WHITE);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 18f));
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel stageLbl = new JLabel("Roadmap — " + stage);
        stageLbl.setFont(stageLbl.getFont().deriveFont(Font.PLAIN, 11f));
        stageLbl.setForeground(MUTED_TEXT);
        stageLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel rationaleLbl = new JLabel("<html><body style='width:560px'>" + rationale + "</body></html>");
        rationaleLbl.setFont(rationaleLbl.getFont().deriveFont(Font.PLAIN, 12f));
        rationaleLbl.setForeground(BODY_TEXT);
        rationaleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        add(titleLbl);
        add(Box.createVerticalStrut(2));
        add(stageLbl);
        add(Box.createVerticalStrut(14));
        add(rationaleLbl);
        add(Box.createVerticalStrut(18));

        for (RoadmapItem item : items) {
            add(buildCard(item));
            add(Box.createVerticalStrut(10));
        }

        add(Box.createVerticalGlue());
    }

    private JPanel buildCard(RoadmapItem item) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER),
            new EmptyBorder(10, 14, 10, 14)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));

        JLabel name = new JLabel(item.name);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        name.setForeground(MUTED_TEXT);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel("<html><body style='width:520px'>" + item.description + "</body></html>");
        desc.setFont(desc.getFont().deriveFont(Font.PLAIN, 11f));
        desc.setForeground(MUTED_TEXT);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(name);
        card.add(Box.createVerticalStrut(4));
        card.add(desc);
        return card;
    }

    static final class RoadmapItem {
        final String name;
        final String description;
        RoadmapItem(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
