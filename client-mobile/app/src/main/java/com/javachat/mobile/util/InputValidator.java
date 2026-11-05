package com.javachat.mobile.util;

import com.javachat.mobile.config.ClientConfig;
import com.javachat.mobile.protocol.Message;

/**
 * Validates incoming messages and user inputs.
 *
 * Fixes applied:
 *  - BUG 8: validates data field length to cap incoming chunk payloads.
 *  - BUG 9: isValidUsername() enforces the same regex as the server.
 */
public class InputValidator {

    private static final int  MAX_TEXT_LENGTH     = 4096;
    private static final int  MAX_USERNAME_LENGTH = 32;
    private static final int  MIN_PORT            = 1;
    private static final int  MAX_PORT            = 65535;
    /** 64 KB chunk → base64 is ~87 KB. Cap at 128 KB to give margin. */
    private static final int  MAX_DATA_LENGTH     = 128 * 1024;

    /** Server pattern: [A-Za-z0-9_\-]{1,32} */
    private static final java.util.regex.Pattern USERNAME_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    private InputValidator() {}

    // -------------------------------------------------------------------------
    // Login inputs
    // -------------------------------------------------------------------------

    /** BUG 9: enforces server-side username rules client-side. */
    public static boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        return USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isValidHost(String host) {
        return host != null && !host.trim().isEmpty();
    }

    public static boolean isValidPort(String portText) {
        if (portText == null) return false;
        try {
            int port = Integer.parseInt(portText.trim());
            return port >= MIN_PORT && port <= MAX_PORT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Incoming messages
    // -------------------------------------------------------------------------

    public static boolean isValidIncoming(Message message) {
        if (message == null) return false;
        if (message.getType() == null) return false;

        if (message.getText() != null && message.getText().length() > MAX_TEXT_LENGTH) {
            return false;
        }

        // BUG 8: cap incoming base64 chunk payloads
        if (message.getData() != null && message.getData().length() > MAX_DATA_LENGTH) {
            return false;
        }

        if (message.getFileSize() != null && message.getFileSize() > ClientConfig.MAX_FILE_SIZE_BYTES) {
            return false;
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Text sanitisation
    // -------------------------------------------------------------------------

    public static String sanitiseText(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            return trimmed.substring(0, MAX_TEXT_LENGTH) + "…";
        }
        return trimmed;
    }
}
