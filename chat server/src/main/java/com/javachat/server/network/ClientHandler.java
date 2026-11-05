package com.javachat.server.network;

import com.javachat.server.ai.GeminiService;
import com.javachat.server.auth.UserService;
import com.javachat.server.chat.GlobalChatRoom;
import com.javachat.server.protocol.Message;
import com.javachat.server.protocol.ProtocolManager;
import com.javachat.server.transfer.AudioTransferManager;
import com.javachat.server.transfer.FileTransferManager;
import com.javachat.server.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Handles a single connected client for its entire session.
 *
 * Responsibilities:
 *  - Read newline-delimited JSON messages from the client.
 *  - Dispatch each message to the appropriate handler based on MessageType.
 *  - Register / deregister from ClientManager on connect / disconnect.
 *  - Phase 10 hardening: enforce line length and per-message validation.
 */
public class ClientHandler implements Runnable {

    // Phase 10 – security limits
    /** Maximum raw line length in bytes before the connection is dropped. */
    private static final int MAX_LINE_LENGTH = 1024 * 1024; // 1 MB (covers full Base64 chunks)
    /** Maximum username length. */
    static final int MAX_USERNAME_LENGTH = 32;
    /** Allowed username pattern: letters, digits, underscore, hyphen only. */
    private static final java.util.regex.Pattern USERNAME_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9_\\-]{1,32}");
    /** Maximum chat message text length. */
    static final int MAX_CHAT_TEXT_LENGTH = 2000;

    private final Socket socket;
    private final ClientManager clientManager;
    private final UserService userService;
    private final GlobalChatRoom globalChatRoom;
    private final FileTransferManager fileTransferManager;
    private final AudioTransferManager audioTransferManager;
    private final GeminiService geminiService;

    private PrintWriter writer;
    private boolean joined = false;
    private String username = null;

    public ClientHandler(Socket socket,
                         ClientManager clientManager,
                         UserService userService,
                         GlobalChatRoom globalChatRoom,
                         FileTransferManager fileTransferManager,
                         AudioTransferManager audioTransferManager,
                         GeminiService geminiService) {
        this.socket = socket;
        this.clientManager = clientManager;
        this.userService = userService;
        this.globalChatRoom = globalChatRoom;
        this.fileTransferManager = fileTransferManager;
        this.audioTransferManager = audioTransferManager;
        this.geminiService = geminiService;
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = reader.readLine()) != null) {
                // Phase 10: drop oversized lines
                if (line.length() > MAX_LINE_LENGTH) {
                    Logger.error("[OVERSIZED] Line from " + displayName() + " exceeded limit, dropping connection.");
                    sendMessage(Message.error("Message too large."));
                    break;
                }
                handleLine(line);
            }

            Logger.info("[DISCONNECT] " + displayName());
        } catch (IOException e) {
            Logger.info("[DISCONNECT] " + displayName() + " (IOException: " + e.getMessage() + ")");
        } finally {
            if (joined) {
                userService.leave(username);
                clientManager.broadcast(Message.userLeft(username));
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /** Send a pre-serialized JSON line to this client. Thread-safe via PrintWriter's synchronized println. */
    public void sendRaw(String json) {
        if (writer != null) {
            writer.println(json);
        }
    }

    /** Serialize and send a Message to this client. */
    public void sendMessage(Message message) {
        try {
            sendRaw(ProtocolManager.serialize(message));
        } catch (IOException e) {
            Logger.error("Failed to serialize message for " + displayName() + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // Line dispatch
    // =========================================================================

    private void handleLine(String line) {
        Message message;
        try {
            message = ProtocolManager.deserialize(line);
        } catch (IOException e) {
            Logger.error("Malformed message from " + displayName() + ": " + e.getMessage());
            sendMessage(Message.error("Malformed JSON message."));
            return;
        }

        if (message.getType() == null) {
            sendMessage(Message.error("Missing message type."));
            return;
        }

        if (!joined) {
            // Only JOIN is accepted before a username is claimed
            switch (message.getType()) {
                case JOIN -> handleJoin(message);
                default -> sendMessage(Message.error("Send JOIN first."));
            }
        } else {
            switch (message.getType()) {
                case CHAT          -> handleChat(message);
                case FILE_META     -> handleFileMeta(message);
                case FILE_CHUNK    -> handleFileChunk(message);
                case FILE_COMPLETE -> handleFileComplete(message);
                case AUDIO_META    -> handleAudioMeta(message);
                case AUDIO_CHUNK   -> handleAudioChunk(message);
                case AUDIO_COMPLETE -> handleAudioComplete(message);
                case AI_REQUEST    -> handleAiRequest(message);
                case JOIN          -> sendMessage(Message.error("Already joined as " + username + "."));
                default            -> Logger.info("[IGNORED] " + message.getType() + " from " + username);
            }
        }
    }

    // =========================================================================
    // Phase 5 – Auth / Join
    // =========================================================================

    private void handleJoin(Message message) {
        String requestedName = message.getSender();

        if (requestedName == null || requestedName.isBlank()) {
            sendMessage(Message.joinError("Username must not be empty."));
            return;
        }
        // Phase 10: validate username format and length
        if (requestedName.length() > MAX_USERNAME_LENGTH) {
            sendMessage(Message.joinError("Username must not exceed " + MAX_USERNAME_LENGTH + " characters."));
            return;
        }
        if (!USERNAME_PATTERN.matcher(requestedName).matches()) {
            sendMessage(Message.joinError("Username may only contain letters, digits, underscores, and hyphens."));
            return;
        }

        if (userService.tryJoin(requestedName, this)) {
            joined = true;
            username = requestedName;
            Logger.info("[JOIN] " + username + " from " + socket.getRemoteSocketAddress());
            sendMessage(Message.joinSuccess(username));
            // Send USER_JOINED for every already-connected user to the newcomer,
            // so their online users sidebar populates with existing members.
            for (String existingUser : clientManager.getUsernames()) {
                if (!existingUser.equals(username)) {
                    sendMessage(Message.userJoined(existingUser));
                }
            }
            clientManager.broadcastExcept(username, Message.userJoined(username));
        } else {
            sendMessage(Message.joinError("Username '" + requestedName + "' is already taken."));
        }
    }

    // =========================================================================
    // Phase 6 – Chat
    // =========================================================================

    private void handleChat(Message message) {
        String text = message.getText();
        if (text == null || text.isBlank()) {
            sendMessage(Message.error("Message text must not be empty."));
            return;
        }
        // Phase 10: text length cap
        if (text.length() > MAX_CHAT_TEXT_LENGTH) {
            sendMessage(Message.error("Message text exceeds maximum length of " + MAX_CHAT_TEXT_LENGTH + " characters."));
            return;
        }
        globalChatRoom.broadcast(username, text);
    }

    // =========================================================================
    // Phase 7 – File transfer
    // =========================================================================

    private void handleFileMeta(Message message) {
        String error = fileTransferManager.handleFileMeta(username, message);
        if (error != null) sendMessage(Message.error(error));
    }

    private void handleFileChunk(Message message) {
        String error = fileTransferManager.handleFileChunk(username, message);
        if (error != null) sendMessage(Message.error(error));
    }

    private void handleFileComplete(Message message) {
        String error = fileTransferManager.handleFileComplete(username, message);
        if (error != null) sendMessage(Message.error(error));
    }

    // =========================================================================
    // Phase 8 – Audio transfer
    // =========================================================================

    private void handleAudioMeta(Message message) {
        String error = audioTransferManager.handleAudioMeta(username, message);
        if (error != null) sendMessage(Message.error(error));
    }

    private void handleAudioChunk(Message message) {
        String error = audioTransferManager.handleAudioChunk(username, message);
        if (error != null) sendMessage(Message.error(error));
    }

    private void handleAudioComplete(Message message) {
        String error = audioTransferManager.handleAudioComplete(username, message);
        if (error != null) sendMessage(Message.error(error));
    }

    // =========================================================================
    // Phase 9 – AI assistant
    // =========================================================================

    private void handleAiRequest(Message message) {
        String prompt = message.getPrompt();
        if (prompt == null || prompt.isBlank()) {
            sendMessage(Message.error("AI_REQUEST: prompt must not be empty."));
            return;
        }
        // Phase 10: cap prompt length
        if (prompt.length() > GeminiService.MAX_PROMPT_LENGTH) {
            sendMessage(Message.error("AI_REQUEST: prompt exceeds maximum length of "
                    + GeminiService.MAX_PROMPT_LENGTH + " characters."));
            return;
        }

        Logger.info("[AI_REQUEST] " + username + ": " + prompt.substring(0, Math.min(prompt.length(), 80))
                + (prompt.length() > 80 ? "…" : ""));

        // Call Gemini synchronously on the client's thread (each client has its own thread)
        String response = geminiService.generate(prompt);

        Logger.info("[AI_RESPONSE] → " + username);
        sendMessage(Message.aiResponse(response));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String displayName() {
        return username != null ? username : socket.getRemoteSocketAddress().toString();
    }
}
