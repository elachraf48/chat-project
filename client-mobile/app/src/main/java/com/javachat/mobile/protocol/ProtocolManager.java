package com.javachat.mobile.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles JSON serialisation/deserialisation and the newline-framed TCP
 * message protocol used by the JavaChat server.
 *
 * Protocol: each message is a single JSON object followed by '\n'.
 */
public class ProtocolManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BufferedReader reader;
    private final PrintWriter writer;

    public ProtocolManager(Socket socket) throws IOException {
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /** Serialise and send a message. Thread-safe per PrintWriter auto-flush. */
    public synchronized void send(Message message) throws IOException {
        String json = MAPPER.writeValueAsString(message);
        writer.println(json);
        if (writer.checkError()) {
            throw new IOException("Write error on socket output stream");
        }
    }

    /** Block until the next newline-terminated JSON message arrives, then deserialise it. */
    public Message receive() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Connection closed by server");
        }
        return MAPPER.readValue(line, Message.class);
    }

    public void close() {
        writer.close();
        try { reader.close(); } catch (IOException ignored) {}
    }
}
