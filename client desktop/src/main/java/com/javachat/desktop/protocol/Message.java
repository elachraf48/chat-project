package com.javachat.desktop.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Shared message POJO used for all client↔server communication.
 * Must stay in sync with the server's Message class.
 * Fields not present in a given message type are left null and omitted from JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    private MessageType type;
    private String messageId;
    private String sender;
    private String text;
    private String timestamp;

    // File / Audio transfer fields
    private String  fileId;
    private String  fileName;
    private Long    fileSize;
    private Integer totalChunks;
    private Integer chunkIndex;
    private String  data;

    // AI fields
    private String prompt;

    public Message() {}

    // -------------------------------------------------------------------------
    // Outgoing message factories (client → server)
    // -------------------------------------------------------------------------

    /** Join the chat room with a username. */
    public static Message join(String username) {
        Message m = new Message();
        m.type   = MessageType.JOIN;
        m.sender = username;
        return m;
    }

    /** Send a chat message (outgoing — no sender needed by server). */
    public static Message chat(String text) {
        Message m = new Message();
        m.type      = MessageType.CHAT;
        m.text      = text;
        m.timestamp = Instant.now().toString();
        return m;
    }

    /**
     * Build a local-display CHAT message with sender set.
     * Used to show own messages in the bubble panel immediately after sending,
     * since the server uses broadcastExcept and never echoes to the sender.
     */
    public static Message chat(String sender, String text) {
        Message m = new Message();
        m.type      = MessageType.CHAT;
        m.sender    = sender;
        m.text      = text;
        m.timestamp = Instant.now().toString();
        return m;
    }

    /** Send an AI prompt. The API key stays on the server. */
    public static Message aiRequest(String promptText) {
        Message m = new Message();
        m.type   = MessageType.AI_REQUEST;
        m.prompt = promptText;
        m.timestamp = Instant.now().toString();
        return m;
    }

    /** FILE_META — announce a file transfer. */
    public static Message fileMeta(String fileId, String fileName, long fileSize, int totalChunks) {
        Message m = new Message();
        m.type        = MessageType.FILE_META;
        m.fileId      = fileId;
        m.fileName    = fileName;
        m.fileSize    = fileSize;
        m.totalChunks = totalChunks;
        return m;
    }

    /** FILE_CHUNK — one chunk of a file transfer. */
    public static Message fileChunk(String fileId, int chunkIndex, String base64Data) {
        Message m = new Message();
        m.type       = MessageType.FILE_CHUNK;
        m.fileId     = fileId;
        m.chunkIndex = chunkIndex;
        m.data       = base64Data;
        return m;
    }

    /** FILE_COMPLETE — signal that all chunks have been sent. */
    public static Message fileComplete(String fileId) {
        Message m = new Message();
        m.type   = MessageType.FILE_COMPLETE;
        m.fileId = fileId;
        return m;
    }

    /** AUDIO_META — announce an audio message transfer. */
    public static Message audioMeta(String fileId, String fileName, long fileSize, int totalChunks) {
        Message m = new Message();
        m.type        = MessageType.AUDIO_META;
        m.fileId      = fileId;
        m.fileName    = fileName;
        m.fileSize    = fileSize;
        m.totalChunks = totalChunks;
        return m;
    }

    /** AUDIO_CHUNK — one chunk of an audio transfer. */
    public static Message audioChunk(String fileId, int chunkIndex, String base64Data) {
        Message m = new Message();
        m.type       = MessageType.AUDIO_CHUNK;
        m.fileId     = fileId;
        m.chunkIndex = chunkIndex;
        m.data       = base64Data;
        return m;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public MessageType getType()                      { return type; }
    public void setType(MessageType type)             { this.type = type; }

    public String getMessageId()                      { return messageId; }
    public void setMessageId(String messageId)        { this.messageId = messageId; }

    public String getSender()                         { return sender; }
    public void setSender(String sender)              { this.sender = sender; }

    public String getText()                           { return text; }
    public void setText(String text)                  { this.text = text; }

    public String getTimestamp()                      { return timestamp; }
    public void setTimestamp(String timestamp)        { this.timestamp = timestamp; }

    public String getFileId()                         { return fileId; }
    public void setFileId(String fileId)              { this.fileId = fileId; }

    public String getFileName()                       { return fileName; }
    public void setFileName(String fileName)          { this.fileName = fileName; }

    public Long getFileSize()                         { return fileSize; }
    public void setFileSize(Long fileSize)            { this.fileSize = fileSize; }

    public Integer getTotalChunks()                   { return totalChunks; }
    public void setTotalChunks(Integer totalChunks)   { this.totalChunks = totalChunks; }

    public Integer getChunkIndex()                    { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex)     { this.chunkIndex = chunkIndex; }

    public String getData()                           { return data; }
    public void setData(String data)                  { this.data = data; }

    public String getPrompt()                         { return prompt; }
    public void setPrompt(String prompt)              { this.prompt = prompt; }
}
