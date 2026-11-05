package com.javachat.mobile.network;

import com.javachat.mobile.protocol.Message;

/**
 * Callback interface delivered on the main thread by {@link ConnectionManager}.
 */
public interface MessageListener {
    void onMessageReceived(Message message);
    void onConnectionLost(String reason);
}
