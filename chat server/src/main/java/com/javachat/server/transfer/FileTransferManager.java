package com.javachat.server.transfer;

import com.javachat.server.network.ClientManager;
import com.javachat.server.protocol.Message;
import com.javachat.server.util.Logger;

/**
 * Phase 7 – File Sharing
 *
 * The server's role is pure relay: it does NOT store file data on disk.
 * When a client sends FILE_META / FILE_CHUNK / FILE_COMPLETE the server
 * immediately re-broadcasts each message to all other joined clients.
 *
 * Validation performed here:
 *   - fileId and fileName must be non-null / non-blank.
 *   - fileSize must be > 0 and <= MAX_FILE_SIZE_BYTES.
 *   - totalChunks must be > 0.
 *   - chunkIndex on FILE_CHUNK must be >= 0.
 *   - data (Base64 payload) must be non-null / non-blank.
 */
public class FileTransferManager {

    /** 50 MB hard limit per file. */
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    private final ClientManager clientManager;

    public FileTransferManager(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    // -------------------------------------------------------------------------
    // Public handlers (called from ClientHandler)
    // -------------------------------------------------------------------------

    /**
     * Handle a FILE_META message from the sending client.
     * Validates the metadata then relays to all other clients.
     *
     * @return null on success, or an error string to send back to the sender.
     */
    public String handleFileMeta(String senderUsername, Message message) {
        String fileId   = message.getFileId();
        String fileName = message.getFileName();
        Long   fileSize = message.getFileSize();
        Integer totalChunks = message.getTotalChunks();

        if (fileId == null || fileId.isBlank()) {
            return "FILE_META: fileId must not be empty.";
        }
        // Validate fileId is a well-formed UUID to prevent log injection / misuse
        try { java.util.UUID.fromString(fileId); }
        catch (IllegalArgumentException e) { return "FILE_META: fileId must be a valid UUID."; }

        if (fileName == null || fileName.isBlank()) {
            return "FILE_META: fileName must not be empty.";
        }
        if (fileSize == null || fileSize <= 0) {
            return "FILE_META: fileSize must be > 0.";
        }
        if (fileSize > MAX_FILE_SIZE_BYTES) {
            return "FILE_META: file exceeds maximum allowed size of " + (MAX_FILE_SIZE_BYTES / 1024 / 1024) + " MB.";
        }
        if (totalChunks == null || totalChunks <= 0) {
            return "FILE_META: totalChunks must be > 0.";
        }

        // Sanitize fileName before logging to prevent log injection
        String safeFileName = fileName.replace("\n", "\\n").replace("\r", "\\r");
        Logger.info("[FILE_META] " + senderUsername + " → \"" + safeFileName
                + "\" (" + fileSize + " bytes, " + totalChunks + " chunks) [" + fileId + "]");

        // Relay to all other clients
        clientManager.broadcastExcept(senderUsername,
                Message.fileMeta(senderUsername, fileId, fileName, fileSize, totalChunks));
        return null;
    }

    /**
     * Handle a FILE_CHUNK message from the sending client.
     * Validates then relays to all other clients.
     *
     * @return null on success, or an error string.
     */
    public String handleFileChunk(String senderUsername, Message message) {
        String fileId    = message.getFileId();
        Integer chunkIdx = message.getChunkIndex();
        String data      = message.getData();

        if (fileId == null || fileId.isBlank()) {
            return "FILE_CHUNK: fileId must not be empty.";
        }
        try { java.util.UUID.fromString(fileId); }
        catch (IllegalArgumentException e) { return "FILE_CHUNK: fileId must be a valid UUID."; }
        if (chunkIdx == null || chunkIdx < 0) {
            return "FILE_CHUNK: chunkIndex must be >= 0.";
        }
        if (data == null || data.isBlank()) {
            return "FILE_CHUNK: data must not be empty.";
        }

        // Relay immediately — no server-side reassembly
        clientManager.broadcastExcept(senderUsername,
                Message.fileChunk(senderUsername, fileId, chunkIdx, data));
        return null;
    }

    /**
     * Handle a FILE_COMPLETE message.
     *
     * @return null on success, or an error string.
     */
    public String handleFileComplete(String senderUsername, Message message) {
        String fileId = message.getFileId();

        if (fileId == null || fileId.isBlank()) {
            return "FILE_COMPLETE: fileId must not be empty.";
        }
        try { java.util.UUID.fromString(fileId); }
        catch (IllegalArgumentException e) { return "FILE_COMPLETE: fileId must be a valid UUID."; }

        Logger.info("[FILE_COMPLETE] " + senderUsername + " finished transfer [" + fileId + "]");
        clientManager.broadcastExcept(senderUsername,
                Message.fileComplete(senderUsername, fileId));
        return null;
    }
}
