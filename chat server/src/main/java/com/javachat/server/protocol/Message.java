package com.javachat.server.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    private MessageType type;
    private String messageId;
    private String sender;
    private String text;
    private String timestamp;

    // --- File / Audio transfer fields ---
    /** Unique ID shared across FILE_META, FILE_CHUNK, FILE_COMPLETE for the same transfer. */
    private String fileId;
    /** Original filename (FILE_META, AUDIO_META). */
    private String fileName;
    /** Total file size in bytes (FILE_META, AUDIO_META). */
    private Long fileSize;
    /** Total number of chunks (FILE_META, AUDIO_META). */
    private Integer totalChunks;
    /** Zero-based chunk index (FILE_CHUNK, AUDIO_CHUNK). */
    private Integer chunkIndex;
    /** Base64-encoded chunk payload (FILE_CHUNK, AUDIO_CHUNK). */
    private String data;

    // --- AI fields ---
    /** AI prompt text (AI_REQUEST). */
    private String prompt;

    // Required by Jackson
    public Message() {}

    // =========================================================================
    // Static factory helpers — Chat
    // =========================================================================

    public static Message joinSuccess(String username) {
        Message m = new Message();
        m.type = MessageType.JOIN_SUCCESS;
        m.sender = username;
        m.timestamp = now();
        return m;
    }

    public static Message joinError(String reason) {
        Message m = new Message();
        m.type = MessageType.JOIN_ERROR;
        m.text = reason;
        m.timestamp = now();
        return m;
    }

    public static Message userJoined(String username) {
        Message m = new Message();
        m.type = MessageType.USER_JOINED;
        m.sender = username;
        m.timestamp = now();
        return m;
    }

    public static Message userLeft(String username) {
        Message m = new Message();
        m.type = MessageType.USER_LEFT;
        m.sender = username;
        m.timestamp = now();
        return m;
    }

    public static Message error(String text) {
        Message m = new Message();
        m.type = MessageType.ERROR;
        m.text = text;
        m.timestamp = now();
        return m;
    }

    public static Message chat(String sender, String text) {
        Message m = new Message();
        m.type = MessageType.CHAT;
        m.messageId = UUID.randomUUID().toString();
        m.sender = sender;
        m.text = text;
        m.timestamp = now();
        return m;
    }

    // =========================================================================
    // Static factory helpers — File transfer
    // =========================================================================

    public static Message fileMeta(String sender, String fileId, String fileName, long fileSize, int totalChunks) {
        Message m = new Message();
        m.type = MessageType.FILE_META;
        m.sender = sender;
        m.fileId = fileId;
        m.fileName = fileName;
        m.fileSize = fileSize;
        m.totalChunks = totalChunks;
        m.timestamp = now();
        return m;
    }

    public static Message fileChunk(String sender, String fileId, int chunkIndex, String base64Data) {
        Message m = new Message();
        m.type = MessageType.FILE_CHUNK;
        m.sender = sender;
        m.fileId = fileId;
        m.chunkIndex = chunkIndex;
        m.data = base64Data;
        m.timestamp = now();
        return m;
    }

    public static Message fileComplete(String sender, String fileId) {
        Message m = new Message();
        m.type = MessageType.FILE_COMPLETE;
        m.sender = sender;
        m.fileId = fileId;
        m.timestamp = now();
        return m;
    }

    // =========================================================================
    // Static factory helpers — Audio transfer
    // =========================================================================

    public static Message audioMeta(String sender, String fileId, String fileName, long fileSize, int totalChunks) {
        Message m = new Message();
        m.type = MessageType.AUDIO_META;
        m.sender = sender;
        m.fileId = fileId;
        m.fileName = fileName;
        m.fileSize = fileSize;
        m.totalChunks = totalChunks;
        m.timestamp = now();
        return m;
    }

    public static Message audioChunk(String sender, String fileId, int chunkIndex, String base64Data) {
        Message m = new Message();
        m.type = MessageType.AUDIO_CHUNK;
        m.sender = sender;
        m.fileId = fileId;
        m.chunkIndex = chunkIndex;
        m.data = base64Data;
        m.timestamp = now();
        return m;
    }

    public static Message audioComplete(String sender, String fileId) {
        Message m = new Message();
        m.type = MessageType.AUDIO_COMPLETE;
        m.sender = sender;
        m.fileId = fileId;
        m.timestamp = now();
        return m;
    }

    // =========================================================================
    // Static factory helpers — AI
    // =========================================================================

    public static Message aiResponse(String text) {
        Message m = new Message();
        m.type = MessageType.AI_RESPONSE;
        m.text = text;
        m.timestamp = now();
        return m;
    }

    // =========================================================================
    // Getters / Setters
    // =========================================================================

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Integer getTotalChunks() { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String now() {
        return Instant.now().toString();
    }
}
