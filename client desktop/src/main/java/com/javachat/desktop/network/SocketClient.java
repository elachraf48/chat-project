package com.javachat.desktop.network;

import com.javachat.desktop.protocol.Message;
import com.javachat.desktop.protocol.ProtocolManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Low-level TCP socket wrapper.
 * Sends and receives newline-delimited JSON messages.
 * All network I/O runs on a background thread — never on the EDT.
 */
public class SocketClient {

    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    /**
     * Open a TCP connection to the server.
     * @throws IOException if the connection fails.
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Send a Message to the server (serializes to one JSON line).
     * @throws IOException if the write fails.
     */
    public void send(Message message) throws IOException {
        if (writer == null) throw new IOException("Not connected.");
        writer.println(ProtocolManager.serialize(message));
    }

    /**
     * Block until the next message line arrives, then deserialize it.
     * Returns null if the server closed the connection.
     * @throws IOException on read error or parse failure.
     */
    public Message receive() throws IOException {
        if (reader == null) throw new IOException("Not connected.");
        String line = reader.readLine();
        if (line == null) return null;
        return ProtocolManager.deserialize(line);
    }

    /** @return true if the socket is connected and not closed. */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** Close the socket. Safe to call multiple times. */
    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}
