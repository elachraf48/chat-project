package com.javachat.desktop.audio;

import com.javachat.desktop.network.ConnectionManager;
import com.javachat.desktop.protocol.Message;

import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Coordinates audio recording and sending.
 *
 * Protocol (Phase 8 — matches server AudioTransferManager):
 *   1. AUDIO_META  — fileId (UUID), fileName (.wav), fileSize, totalChunks
 *   2. AUDIO_CHUNK — fileId, chunkIndex (0-based), data (Base64)
 *
 * Playback of received audio is triggered by {@link #playReceived(byte[])}.
 */
public class AudioManager {

    /** Server hard limit for audio messages. */
    private static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final int CHUNK_SIZE      = 64 * 1024;         // 64 KB

    private final ConnectionManager connectionManager;
    private final AudioRecorder recorder = new AudioRecorder();
    private final AudioPlayer   player   = new AudioPlayer();

    public AudioManager(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Toggle recording on/off.
     *
     * @param recordingStateCallback called with {@code true} when recording starts,
     *                               {@code false} when it stops. Always called from a
     *                               background thread — use SwingUtils.runOnEDT() before
     *                               touching UI (the caller in GlobalChatPanel does this).
     * @param errorCallback          called with an error message if something goes wrong.
     */
    public void toggleRecording(Consumer<Boolean> recordingStateCallback,
                                Consumer<String>  errorCallback) {
        if (recorder.isRecording()) {
            stopAndSend(recordingStateCallback, errorCallback);
        } else {
            startRecording(recordingStateCallback, errorCallback);
        }
    }

    /**
     * Play received raw PCM bytes on a background thread.
     */
    public void playReceived(byte[] pcmData) {
        Thread t = new Thread(() -> {
            try {
                player.play(pcmData);
            } catch (Exception e) {
                System.err.println("[AudioManager] Playback error: " + e.getMessage());
            }
        }, "audio-player");
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------

    private void startRecording(Consumer<Boolean> stateCallback,
                                Consumer<String>  errorCallback) {
        Thread t = new Thread(() -> {
            try {
                recorder.start();
                if (stateCallback != null) stateCallback.accept(true);
            } catch (Exception e) {
                if (errorCallback != null) errorCallback.accept(e.getMessage());
            }
        }, "audio-recorder-starter");
        t.setDaemon(true);
        t.start();
    }

    private void stopAndSend(Consumer<Boolean> stateCallback,
                             Consumer<String>  errorCallback) {
        Thread t = new Thread(() -> {
            try {
                byte[] pcm = recorder.stop();
                if (stateCallback != null) stateCallback.accept(false);

                if (pcm.length == 0) return;

                // WARN 2: enforce 10 MB limit before sending
                if (pcm.length > MAX_AUDIO_BYTES) {
                    if (errorCallback != null)
                        errorCallback.accept("Recording is too large (max 10 MB). Please record a shorter clip.");
                    return;
                }

                String fileId      = UUID.randomUUID().toString();
                String fileName    = "audio_" + System.currentTimeMillis() + ".wav";
                long   fileSize    = pcm.length;
                int    totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);

                // 1 — AUDIO_META
                connectionManager.send(
                        Message.audioMeta(fileId, fileName, fileSize, totalChunks));

                // 2 — AUDIO_CHUNK(s)
                int chunkIndex = 0;
                int offset = 0;
                while (offset < pcm.length) {
                    int end = Math.min(offset + CHUNK_SIZE, pcm.length);
                    byte[] chunk = java.util.Arrays.copyOfRange(pcm, offset, end);
                    String encoded = Base64.getEncoder().encodeToString(chunk);
                    connectionManager.send(Message.audioChunk(fileId, chunkIndex, encoded));
                    chunkIndex++;
                    offset = end;
                }

            } catch (Exception e) {
                if (errorCallback != null) errorCallback.accept(e.getMessage());
            }
        }, "audio-sender");
        t.setDaemon(true);
        t.start();
    }
}
