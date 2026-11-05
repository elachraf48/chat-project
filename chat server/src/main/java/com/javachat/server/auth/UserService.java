package com.javachat.server.auth;

import com.javachat.server.network.ClientHandler;
import com.javachat.server.network.ClientManager;

public class UserService {

    private final ClientManager clientManager;

    public UserService(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    /**
     * Attempt to claim a username for a connecting client.
     * @return true if the username was successfully claimed; false if already taken.
     */
    public boolean tryJoin(String username, ClientHandler handler) {
        return clientManager.register(username, handler);
    }

    /** Release a username when a client disconnects. */
    public void leave(String username) {
        clientManager.deregister(username);
    }
}
