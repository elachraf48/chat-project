package com.javachat.desktop;

import com.javachat.desktop.ui.LoginFrame;
import com.javachat.desktop.util.SwingUtils;

import javax.swing.UIManager;

/**
 * Application entry point.
 * Launches the login window on the Swing Event Dispatch Thread.
 */
public class Main {

    public static void main(String[] args) {
        // Use the native OS look-and-feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtils.runOnEDT(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}
