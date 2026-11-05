package com.javachat.mobile.ai;

import android.os.Handler;
import android.os.Looper;

import com.javachat.mobile.network.ConnectionManager;
import com.javachat.mobile.protocol.Message;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages Gemini AI requests and responses.
 *
 * The Gemini API key lives on the server — this class only sends
 * AI_REQUEST messages and listens for AI_RESPONSE messages.
 */
public class GeminiChatManager {

    private static GeminiChatManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Queue of pending callbacks, in FIFO order
    private final Queue<ResponseCallback> pendingCallbacks = new ConcurrentLinkedQueue<>();

    private GeminiChatManager() {}

    public static synchronized GeminiChatManager getInstance() {
        if (instance == null) instance = new GeminiChatManager();
        return instance;
    }

    public interface ResponseCallback {
        void onResponse(String responseText);
    }

    /**
     * Send an AI request through the server. The callback is invoked on the
     * main thread when the AI_RESPONSE arrives.
     */
    public void sendRequest(String prompt, ResponseCallback callback) {
        pendingCallbacks.add(callback);
        ConnectionManager.getInstance().send(Message.aiRequest(prompt));
    }

    /**
     * Called by the UI layer when an AI_RESPONSE message is received.
     */
    public void handleResponse(Message message) {
        ResponseCallback cb = pendingCallbacks.poll();
        if (cb != null) {
            String text = message.getText() != null ? message.getText() : "(no response)";
            mainHandler.post(() -> cb.onResponse(text));
        }
    }
}
