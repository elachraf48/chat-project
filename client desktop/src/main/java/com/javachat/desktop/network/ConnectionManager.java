package com.javachat.desktop.network;

import com.javachat.desktop.config.ClientConfig;
import com.javachat.desktop.protocol.Message;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the lifecycle of the TCP connection to the JavaChat Server.
 *
 * Responsibilities:
 * - Establish and close the connection.
 * - Send messages to the server.
 * - Run a background reader thread that delivers incoming messages to a {@link MessageListener}.
 */
public class ConnectionManager {

    private final SocketClient socketClient = new SocketClient();
    private final ExecutorService readerThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "server-reader");
        t.setDaemon(true);
        return t;
    });

    private MessageListener listener;

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    /**
     * Connect to the server and start the background reader loop.
     * @throws IOException if the TCP connection cannot be established.
     */
    public void connect() throws IOException {
        socketClient.connect(ClientConfig.SERVER_HOST, ClientConfig.SERVER_PORT);
        startReaderLoop();
    }

    /**
     * Send a message to the server.
     * @throws IOException if not connected or write fails.
     */
    public void send(Message message) throws IOException {
        socketClient.send(message);
    }

    /** Disconnect from the server and stop the reader thread. */
    public void disconnect() {
        socketClient.close();
        readerThread.shutdownNow();
    }

    /** @return true if currently connected. */
    public boolean isConnected() {
        return socketClient.isConnected();
    }

    // -------------------------------------------------------------------------

    private void startReaderLoop() {
        readerThread.submit(() -> {
            try {
                Message msg;
                while ((msg = socketClient.receive()) != null) {
                    if (listener != null) {
                        final Message m = msg;
                        listener.onMessage(m);
                    }
                }
                if (listener != null) listener.onDisconnected("Server closed the connection.");
            } catch (IOException e) {
                if (listener != null) listener.onDisconnected(e.getMessage());
            }
        });
    }
}
