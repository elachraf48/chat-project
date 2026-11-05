package com.javachat.desktop.util;

import javax.swing.SwingUtilities;

/**
 * Swing utility helpers.
 */
public class SwingUtils {

    private SwingUtils() {}

    /**
     * Run a task on the Swing Event Dispatch Thread.
     * If already on the EDT the runnable is invoked directly; otherwise
     * it is queued via {@link SwingUtilities#invokeLater}.
     */
    public static void runOnEDT(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
