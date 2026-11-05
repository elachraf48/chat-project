package com.javachat.desktop.network;

import com.javachat.desktop.protocol.Message;

/**
 * Callback interface implemented by UI layers that need to react to
 * messages arriving from the server.
 * All callbacks are invoked from the background reader thread;
 * implementations must dispatch to the EDT before touching Swing components.
 */
public interface MessageListener {

    /** A complete Message was received from the server. */
    void onMessage(Message message);

    /** The connection was lost (server closed or network error). */
    void onDisconnected(String reason);
}
