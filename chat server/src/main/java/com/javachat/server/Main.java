package com.javachat.server;

import com.javachat.server.config.ServerConfig;
import com.javachat.server.network.ChatServer;
import com.javachat.server.network.ClientManager;
import com.javachat.server.util.Logger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

public class Main {

    public static void main(String[] args) {
        ServerConfig config = new ServerConfig();

        Logger.info("==========================================");
        Logger.info("         JavaChat Server Starting         ");
        Logger.info("==========================================");
        Logger.info("Port: " + config.getPort());
        printNetworkAddresses(config.getPort());

        ClientManager clientManager = new ClientManager();
        ChatServer chatServer = new ChatServer(config, clientManager);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("[JavaChat Server] Shutting down...");
            chatServer.stop();
            Logger.info("[JavaChat Server] Shutdown complete.");
        }));

        Logger.info("Server ready — waiting for clients...");

        try {
            chatServer.start();
        } catch (IOException e) {
            Logger.error("Server error: " + e.getMessage());
        }
    }

    /**
     * Prints all non-loopback IPv4 addresses so clients know what IP to enter.
     */
    private static void printNetworkAddresses(int port) {
        Logger.info("------------------------------------------");
        Logger.info("Connect your clients to one of these addresses:");

        boolean found = false;
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address)) continue;  // IPv4 only
                    if (addr.isLoopbackAddress()) continue;

                    Logger.info("  [" + iface.getDisplayName() + "]  "
                            + addr.getHostAddress() + ":" + port);
                    found = true;
                }
            }
        } catch (SocketException e) {
            Logger.error("Could not enumerate network interfaces: " + e.getMessage());
        }

        if (!found) {
            Logger.info("  (no external interface found)");
        }

        Logger.info("  Localhost (same machine): 127.0.0.1:" + port);
        Logger.info("------------------------------------------");
    }
}
