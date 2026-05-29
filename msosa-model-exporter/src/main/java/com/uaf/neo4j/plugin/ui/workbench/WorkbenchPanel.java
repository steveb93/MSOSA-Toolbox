package com.uaf.neo4j.plugin.ui.workbench;

import javax.swing.JComponent;

/**
 * Anything hosted in the workbench's card area.
 *
 * <p>Panels are constructed once and reused as the user toggles the rail; do
 * heavy work (Neo4j queries, SPARQL probes, …) inside {@link #onActivated()}
 * rather than in the constructor.
 */
public interface WorkbenchPanel {

    /** The card body — added to the workbench's {@code CardLayout} container. */
    JComponent getComponent();

    /** Which rail item this panel implements. */
    WorkbenchMode getMode();

    /** Called every time the panel becomes the active card. */
    default void onActivated() {}

    /** Called when the workbench is being disposed. Release backends here. */
    default void onClosed() {}
}
