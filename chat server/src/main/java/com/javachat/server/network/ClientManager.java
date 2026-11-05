package com.javachat.server.network;

import com.javachat.server.protocol.Message;
import com.javachat.server.protocol.ProtocolManager;
import com.javachat.server.util.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {

    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    /**
     * Register a username → handler mapping.
     * @return true if registered successfully; false if the username is already taken.
     */
    public boolean register(String username, ClientHandler handler) {
        boolean added = clients.putIfAbsent(username, handler) == null;
        if (added) {
            Logger.info("User joined: " + username + ". Online: " + count());
        }
        return added;
    }

    public void deregister(String username) {
        clients.remove(username);
        Logger.info("User left: " + username + ". Online: " + count());
    }

    public boolean isUsernameTaken(String username) {
        return clients.containsKey(username);
    }

    public Collection<ClientHandler> getAll() {
        return Collections.unmodifiableCollection(clients.values());
    }

    /** Returns a snapshot of all currently registered usernames. */
    public java.util.Set<String> getUsernames() {
        return java.util.Collections.unmodifiableSet(clients.keySet());
    }

    public int count() {
        return clients.size();
    }

    /** Broadcast a message to every joined client. */
    public void broadcast(Message message) {
        String json = serializeQuietly(message);
        if (json == null) return;
        for (ClientHandler handler : clients.values()) {
            handler.sendRaw(json);
        }
    }

    /** Broadcast a message to every joined client except the one with the given username. */
    public void broadcastExcept(String excludeUsername, Message message) {
        String json = serializeQuietly(message);
        if (json == null) return;
        for (var entry : clients.entrySet()) {
            if (!entry.getKey().equals(excludeUsername)) {
                entry.getValue().sendRaw(json);
            }
        }
    }

    private String serializeQuietly(Message message) {
        try {
            return ProtocolManager.serialize(message);
        } catch (IOException e) {
            Logger.error("Failed to serialize message: " + e.getMessage());
            return null;
        }
    }
}
