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
        // Open new socket first; only replace fields after streams init successfully
        // so the old socket is not leaked if stream initialization throws.
        Socket newSocket = new Socket(host, port);
        try {
            PrintWriter newWriter = new PrintWriter(
                    new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader newReader = new BufferedReader(
                    new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
            // All good — close old socket and swap in the new one atomically
            close();
            socket = newSocket;
            writer = newWriter;
            reader = newReader;
        } catch (IOException e) {
            try { newSocket.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    /**
     * Send a Message to the server (serializes to one JSON line).
     * @throws IOException if the write fails.
     */
    public void send(Message message) throws IOException {
        if (writer == null) throw new IOException("Not connected.");
        writer.println(ProtocolManager.serialize(message));
        if (writer.checkError()) throw new IOException("Write failed — connection lost.");
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
