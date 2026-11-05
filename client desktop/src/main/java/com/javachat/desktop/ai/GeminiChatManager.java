package com.javachat.desktop.ai;

import com.javachat.desktop.network.ConnectionManager;
import com.javachat.desktop.protocol.Message;

import java.io.IOException;

/**
 * Sends AI requests to the JavaChat Server, which relays them to Gemini.
 *
 * The Gemini API key is NEVER held in the desktop client.
 * The server handles the Gemini call and returns an AI_RESPONSE message.
 */
public class GeminiChatManager {

    private final ConnectionManager connectionManager;

    public GeminiChatManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Send a natural-language prompt to the server for Gemini processing.
     * The response will arrive as an AI_RESPONSE message via the normal
     * MessageListener callback in MainFrame.
     *
     * @param prompt The user's question or instruction.
     * @throws IOException if the message cannot be sent.
     */
    public void sendPrompt(String prompt) throws IOException {
        connectionManager.send(Message.aiRequest(prompt));
    }
}
