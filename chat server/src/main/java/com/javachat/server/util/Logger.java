package com.javachat.server.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Centralised logging wrapper backed by java.util.logging.
 *
 * On first use, attempts to load logging.properties from the classpath
 * so that the log format and level are configurable without JVM flags.
 */
public class Logger {

    static {
        try (InputStream in = Logger.class.getResourceAsStream("/logging.properties")) {
            if (in != null) {
                LogManager.getLogManager().readConfiguration(in);
            }
        } catch (IOException ignored) {
            // Fall back to JVM defaults
        }
    }

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger("com.javachat.server");

    private Logger() {}

    public static void info(String message) {
        LOGGER.log(Level.INFO, message);
    }

    public static void error(String message) {
        LOGGER.log(Level.SEVERE, message);
    }

    public static void warn(String message) {
        LOGGER.log(Level.WARNING, message);
    }

    public static void debug(String message) {
        LOGGER.log(Level.FINE, message);
    }
}
