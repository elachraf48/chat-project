package com.javachat.server.transfer;

import com.javachat.server.network.ClientManager;
import com.javachat.server.protocol.Message;
import com.javachat.server.util.Logger;

/**
 * Phase 8 – Audio Messaging
 *
 * Mirrors the same relay pattern as FileTransferManager.
 * The server does NOT store audio data — it relays AUDIO_META and
 * AUDIO_CHUNK messages to all other joined clients immediately.
 *
 * Supported audio formats are enforced by file extension:
 *   .ogg, .mp3, .wav, .m4a, .aac
 *
 * Size limit: 10 MB (audio messages are expected to be short clips).
 */
public class AudioTransferManager {

    /** 10 MB hard limit per audio message. */
    public static final long MAX_AUDIO_SIZE_BYTES = 10L * 1024 * 1024;

    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of(".ogg", ".mp3", ".wav", ".m4a", ".aac", ".opus");

    private final ClientManager clientManager;

    public AudioTransferManager(ClientManager clientManager) {
        this.clientManager = clientManager;
    }

    // -------------------------------------------------------------------------
    // Public handlers (called from ClientHandler)
    // -------------------------------------------------------------------------

    /**
     * Handle an AUDIO_META message.
     *
     * @return null on success, or an error string to send back to the sender.
     */
    public String handleAudioMeta(String senderUsername, Message message) {
        String fileId   = message.getFileId();
        String fileName = message.getFileName();
        Long   fileSize = message.getFileSize();
        Integer totalChunks = message.getTotalChunks();

        if (fileId == null || fileId.isBlank()) {
            return "AUDIO_META: fileId must not be empty.";
        }
        if (fileName == null || fileName.isBlank()) {
            return "AUDIO_META: fileName must not be empty.";
        }
        if (!hasAllowedExtension(fileName)) {
            return "AUDIO_META: unsupported audio format. Allowed: .ogg, .mp3, .wav, .m4a, .aac, .opus";
        }
        if (fileSize == null || fileSize <= 0) {
            return "AUDIO_META: fileSize must be > 0.";
        }
        if (fileSize > MAX_AUDIO_SIZE_BYTES) {
            return "AUDIO_META: audio exceeds maximum allowed size of " + (MAX_AUDIO_SIZE_BYTES / 1024 / 1024) + " MB.";
        }
        if (totalChunks == null || totalChunks <= 0) {
            return "AUDIO_META: totalChunks must be > 0.";
        }

        Logger.info("[AUDIO_META] " + senderUsername + " → \"" + fileName
                + "\" (" + fileSize + " bytes, " + totalChunks + " chunks) [" + fileId + "]");

        clientManager.broadcastExcept(senderUsername,
                Message.audioMeta(senderUsername, fileId, fileName, fileSize, totalChunks));
        return null;
    }

    /**
     * Handle an AUDIO_CHUNK message.
     *
     * @return null on success, or an error string.
     */
    public String handleAudioChunk(String senderUsername, Message message) {
        String fileId    = message.getFileId();
        Integer chunkIdx = message.getChunkIndex();
        String data      = message.getData();

        if (fileId == null || fileId.isBlank()) {
            return "AUDIO_CHUNK: fileId must not be empty.";
        }
        if (chunkIdx == null || chunkIdx < 0) {
            return "AUDIO_CHUNK: chunkIndex must be >= 0.";
        }
        if (data == null || data.isBlank()) {
            return "AUDIO_CHUNK: data must not be empty.";
        }

        clientManager.broadcastExcept(senderUsername,
                Message.audioChunk(senderUsername, fileId, chunkIdx, data));
        return null;
    }

    /**
     * Handle an AUDIO_COMPLETE message — relay to all other clients.
     *
     * @return null on success, or an error string.
     */
    public String handleAudioComplete(String senderUsername, Message message) {
        String fileId = message.getFileId();
        if (fileId == null || fileId.isBlank()) {
            return "AUDIO_COMPLETE: fileId must not be empty.";
        }
        Logger.info("[AUDIO_COMPLETE] " + senderUsername + " finished audio transfer [" + fileId + "]");
        clientManager.broadcastExcept(senderUsername,
                Message.audioComplete(senderUsername, fileId));
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean hasAllowedExtension(String fileName) {
        String lower = fileName.toLowerCase();
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
