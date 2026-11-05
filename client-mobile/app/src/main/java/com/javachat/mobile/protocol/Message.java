package com.javachat.mobile.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Protocol message — field names must match the server's Message class exactly.
 *
 * Key alignment with server:
 *   fileId      — unique transfer ID (server uses fileId, not transferId)
 *   data        — base64 chunk payload (server uses data, not text)
 *   prompt      — AI request text (server uses prompt, not text)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    private MessageType type;
    private String messageId;
    private String sender;
    private String text;
    private String timestamp;

    // File / audio transfer — names match server exactly
    private String  fileId;       // was "transferId" — server requires "fileId"
    private String  fileName;
    private Long    fileSize;
    private Integer totalChunks;
    private Integer chunkIndex;
    private String  data;         // base64 chunk payload — server requires "data" not "text"

    // AI — name matches server exactly
    private String prompt;        // server requires "prompt" not "text"

    // Required by Jackson
    public Message() {}

    // -------------------------------------------------------------------------
    // Static factory helpers — produce the JSON the server expects
    // -------------------------------------------------------------------------

    public static Message join(String username) {
        Message m = new Message();
        m.type = MessageType.JOIN;
        m.sender = username;
        return m;
    }

    public static Message chat(String sender, String text) {
        Message m = new Message();
        m.type = MessageType.CHAT;
        m.sender = sender;
        m.text = text;
        m.timestamp = java.time.Instant.now().toString();
        return m;
    }

    /** FILE_META — server validates: fileId, fileName, fileSize, totalChunks */
    public static Message fileMeta(String fileId, String fileName, long fileSize, int totalChunks) {
        Message m = new Message();
        m.type = MessageType.FILE_META;
        m.fileId = fileId;
        m.fileName = fileName;
        m.fileSize = fileSize;
        m.totalChunks = totalChunks;
        return m;
    }

    /** FILE_CHUNK — server validates: fileId, chunkIndex, data */
    public static Message fileChunk(String fileId, int chunkIndex, String base64Data) {
        Message m = new Message();
        m.type = MessageType.FILE_CHUNK;
        m.fileId = fileId;
        m.chunkIndex = chunkIndex;
        m.data = base64Data;
        return m;
    }

    /** FILE_COMPLETE — server validates: fileId */
    public static Message fileComplete(String fileId) {
        Message m = new Message();
        m.type = MessageType.FILE_COMPLETE;
        m.fileId = fileId;
        return m;
    }

    /** AUDIO_META — server validates: fileId, fileName, fileSize, totalChunks */
    public static Message audioMeta(String fileId, String fileName, long fileSize, int totalChunks) {
        Message m = new Message();
        m.type = MessageType.AUDIO_META;
        m.fileId = fileId;
        m.fileName = fileName;
        m.fileSize = fileSize;
        m.totalChunks = totalChunks;
        return m;
    }

    /** AUDIO_CHUNK — server validates: fileId, chunkIndex, data */
    public static Message audioChunk(String fileId, int chunkIndex, String base64Data) {
        Message m = new Message();
        m.type = MessageType.AUDIO_CHUNK;
        m.fileId = fileId;
        m.chunkIndex = chunkIndex;
        m.data = base64Data;
        return m;
    }

    /** AUDIO_COMPLETE — server validates: fileId */
    public static Message audioComplete(String fileId) {
        Message m = new Message();
        m.type = MessageType.AUDIO_COMPLETE;
        m.fileId = fileId;
        return m;
    }

    /** AI_REQUEST — server reads "prompt" field */
    public static Message aiRequest(String prompt) {
        Message m = new Message();
        m.type = MessageType.AI_REQUEST;
        m.prompt = prompt;
        return m;
    }

    // -------------------------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------------------------

    public MessageType getType()                    { return type; }
    public void setType(MessageType type)           { this.type = type; }

    public String getMessageId()                    { return messageId; }
    public void setMessageId(String messageId)      { this.messageId = messageId; }

    public String getSender()                       { return sender; }
    public void setSender(String sender)            { this.sender = sender; }

    public String getText()                         { return text; }
    public void setText(String text)                { this.text = text; }

    public String getTimestamp()                    { return timestamp; }
    public void setTimestamp(String timestamp)      { this.timestamp = timestamp; }

    public String getFileId()                       { return fileId; }
    public void setFileId(String fileId)            { this.fileId = fileId; }

    public String getFileName()                     { return fileName; }
    public void setFileName(String fileName)        { this.fileName = fileName; }

    public Long getFileSize()                       { return fileSize; }
    public void setFileSize(Long fileSize)          { this.fileSize = fileSize; }

    public Integer getTotalChunks()                 { return totalChunks; }
    public void setTotalChunks(Integer totalChunks) { this.totalChunks = totalChunks; }

    public Integer getChunkIndex()                  { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex)   { this.chunkIndex = chunkIndex; }

    public String getData()                         { return data; }
    public void setData(String data)                { this.data = data; }

    public String getPrompt()                       { return prompt; }
    public void setPrompt(String prompt)            { this.prompt = prompt; }
}
