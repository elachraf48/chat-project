package com.javachat.mobile.network;

import com.javachat.mobile.protocol.Message;
import com.javachat.mobile.protocol.ProtocolManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Manages the single TCP socket connection to the JavaChat server.
 * All operations that create or destroy the socket happen on this class.
 * Actual sending / receiving is delegated to {@link ProtocolManager}.
 */
public class SocketClient {

    // volatile so reads/writes are visible across threads without synchronized
    private volatile Socket socket;
    private volatile ProtocolManager protocol;

    /**
     * Opens a TCP connection to the given host:port.
     * Must be called off the main thread.
     */
    public void connect(String host, int port, int timeoutMs) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            protocol = new ProtocolManager(s);
            socket = s;  // assigned last — isConnected() is safe
        } catch (IOException e) {
            try { s.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    /** Send a message. Must be called off the main thread. */
    public void send(Message message) throws IOException {
        checkConnected();
        protocol.send(message);
    }

    /**
     * Block until the next message arrives from the server.
     * Must be called off the main thread (from the reader loop).
     */
    public Message receive() throws IOException {
        checkConnected();
        return protocol.receive();
    }

    /** @return true if the socket is open and connected. */
    public boolean isConnected() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    /**
     * Close the socket. Calling socket.close() interrupts any blocked readLine()
     * on the reader thread — no lock needed, and safe to call from the main thread.
     */
    public void disconnect() {
        Socket s = socket;
        socket = null;
        protocol = null;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private void checkConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected to server");
        }
    }
}
