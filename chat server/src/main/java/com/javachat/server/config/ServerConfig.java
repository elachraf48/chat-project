package com.javachat.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {

    private static final int DEFAULT_PORT = 12345;

    private final int port;

    public ServerConfig() {
        int resolvedPort = DEFAULT_PORT;
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String value = props.getProperty("server.port");
                if (value != null) {
                    resolvedPort = Integer.parseInt(value.trim());
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // fall back to default
        }
        // SERVER_PORT env var overrides application.properties — useful for containerized deployments
        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                resolvedPort = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
                // malformed env var — keep properties/default value
            }
        }
        this.port = resolvedPort;
    }

    public int getPort() {
        return port;
    }
}
