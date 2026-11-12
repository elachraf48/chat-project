package com.javachat.mobile.config;

/**
 * Central configuration constants for the mobile client.
 * The user can override IP and port at login time; these are only defaults.
 */
public class ClientConfig {

    public static final String DEFAULT_SERVER_IP   = "172.31.48.1"; // Android emulator → host loopback
    public static final int    DEFAULT_SERVER_PORT  = 12345;
    public static final int    CONNECT_TIMEOUT_MS   = 5_000;     // 5 s — sufficient for LAN; emulators rarely need more
    public static final int    MAX_FILE_SIZE_BYTES  = 50 * 1024 * 1024; // 50 MB
    public static final int    CHUNK_SIZE_BYTES     = 64 * 1024;        // 64 KB

    private ClientConfig() {}
}
