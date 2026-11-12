package com.javachat.server.chat;

import com.javachat.server.network.ClientManager;
import com.javachat.server.protocol.Message;
import com.javachat.server.util.Logger;

public class GlobalChatRoom {

    private final ClientManager clientManager;

    public GlobalChatRoom(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Stamp a new UUID + timestamp, then broadcast to all clients EXCEPT the sender.
     * The sender adds the message to their UI optimistically on send, so echoing
     * it back would show it twice.
     */
    public void broadcast(String senderUsername, String text) {
        Message message = Message.chat(senderUsername, text);
        // Sanitize text before logging to prevent log injection via ANSI/newline sequences
        String safeText = text.replace("\r", "\\r").replace("\n", "\\n");
        Logger.info("[CHAT] " + senderUsername + ": "
                + safeText.substring(0, Math.min(safeText.length(), 80))
                + (safeText.length() > 80 ? "…" : ""));
        clientManager.broadcastExcept(senderUsername, message);
    }
}
