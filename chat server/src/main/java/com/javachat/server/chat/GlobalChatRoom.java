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
        Logger.info("[CHAT] " + senderUsername + ": " + text);
        clientManager.broadcastExcept(senderUsername, message);
    }
}
