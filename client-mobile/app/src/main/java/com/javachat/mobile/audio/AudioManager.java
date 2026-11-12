package com.javachat.mobile.audio;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.javachat.mobile.R;
import com.javachat.mobile.config.ClientConfig;
import com.javachat.mobile.network.ConnectionManager;
import com.javachat.mobile.protocol.Message;
import com.javachat.mobile.protocol.MessageType;
import com.javachat.mobile.util.PermissionManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-level audio manager: coordinates recording, playback, send and receive.
 *
 * Protocol (must match server AudioTransferManager exactly):
 *  1. AUDIO_META  — fileId, fileName (.m4a), fileSize (Long), totalChunks (Integer)
 *  2. AUDIO_CHUNK — fileId, chunkIndex (Integer), data (base64 String)
 *  3. AUDIO_COMPLETE — fileId
 */
public class AudioManager {

    private static AudioManager instance;

    private final AudioRecorder recorder = new AudioRecorder();
    private final AudioPlayer   player   = new AudioPlayer();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // fileId → accumulated bytes for in-progress incoming audio
    private final Map<String, ByteArrayOutputStream> incomingBuffers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Message>               incomingMeta    = new java.util.concurrent.ConcurrentHashMap<>();
    // fileId → saved cache URI for playback
    private final Map<String, Uri>                   audioCache      = new java.util.concurrent.ConcurrentHashMap<>();

    private AudioManager() {}

    public static synchronized AudioManager getInstance() {
        if (instance == null) instance = new AudioManager();
        return instance;
    }

    // -------------------------------------------------------------------------
    // Recording dialog
    // -------------------------------------------------------------------------

    public interface RecordingCallback {
        void onAudioReady(Uri audioUri);
    }

    public interface SendCallback {
        void onSendComplete(String fileId);
    }


    public void showRecordDialog(Activity activity, RecordingCallback callback) {
        if (!PermissionManager.hasAudioPermission(activity)) {
            PermissionManager.requestAudioPermission(activity);
            return;
        }

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_audio_record, null);
        android.widget.Button btnStart    = dialogView.findViewById(R.id.btnStartRecord);
        android.widget.Button btnStop     = dialogView.findViewById(R.id.btnStopRecord);
        android.widget.Chronometer chrono = dialogView.findViewById(R.id.chronometerRecording);
        android.widget.TextView tvStatus  = dialogView.findViewById(R.id.tvRecordingStatus);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.btn_record)
                .setView(dialogView)
                .setNegativeButton(R.string.btn_cancel, (d, w) -> recorder.release())
                .create();

        btnStart.setOnClickListener(v -> {
            try {
                recorder.startRecording(activity);
                btnStart.setVisibility(View.GONE);
                btnStop.setVisibility(View.VISIBLE);
                tvStatus.setVisibility(View.VISIBLE);
                chrono.setVisibility(View.VISIBLE);
                chrono.start();
            } catch (IOException e) {
                dialog.dismiss();
            }
        });

        btnStop.setOnClickListener(v -> {
            chrono.stop();
            Uri uri = recorder.stopRecording();
            dialog.dismiss();
            if (uri != null && callback != null) callback.onAudioReady(uri);
        });

        dialog.show();
    }

    // -------------------------------------------------------------------------
    // Outgoing
    // -------------------------------------------------------------------------

    public void sendAudio(Uri audioUri, Context context, SendCallback callback) {
        executor.submit(() -> {
            try {
                byte[] data = readAllBytes(context, audioUri);
                if (data == null || data.length == 0) return;
                if (data.length > ClientConfig.MAX_FILE_SIZE_BYTES) return;

                String fileId     = UUID.randomUUID().toString();
                int chunkSize     = ClientConfig.CHUNK_SIZE_BYTES;
                int totalChunks   = (int) Math.ceil((double) data.length / chunkSize);

                // Cache own audio so the sender can also play it back from the bubble
                audioCache.put(fileId, audioUri);

                // 1. META
                ConnectionManager.getInstance().send(
                        Message.audioMeta(fileId, "audio.m4a", (long) data.length, totalChunks));

                // 2. CHUNKs
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end   = Math.min(start + chunkSize, data.length);
                    byte[] chunk = new byte[end - start];
                    System.arraycopy(data, start, chunk, 0, chunk.length);
                    ConnectionManager.getInstance().send(
                            Message.audioChunk(fileId, i, Base64.encodeToString(chunk, Base64.NO_WRAP)));
                }

                // 3. COMPLETE
                ConnectionManager.getInstance().send(Message.audioComplete(fileId));
                if (callback != null) callback.onSendComplete(fileId);

            } catch (IOException e) {
                android.util.Log.e("AudioManager", "sendAudio failed: " + e.getMessage(), e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Incoming
    // -------------------------------------------------------------------------

    public void handleIncoming(Message message, Context context) {
        switch (message.getType()) {
            case AUDIO_META:
                if (message.getFileId() != null) {
                    incomingBuffers.put(message.getFileId(), new ByteArrayOutputStream());
                    incomingMeta.put(message.getFileId(), message);
                }
                break;

            case AUDIO_CHUNK: {
                String fid = message.getFileId();
                ByteArrayOutputStream buf = fid != null ? incomingBuffers.get(fid) : null;
                if (buf != null && message.getData() != null) {
                    byte[] chunk = Base64.decode(message.getData(), Base64.NO_WRAP);
                    buf.write(chunk, 0, chunk.length);
                }
                break;
            }

            case AUDIO_COMPLETE: {
                String fid = message.getFileId();
                ByteArrayOutputStream buf = fid != null ? incomingBuffers.remove(fid) : null;
                if (buf != null && fid != null) {
                    Uri saved = saveAudioToCache(context, buf.toByteArray());
                    if (saved != null) audioCache.put(fid, saved);
                }
                incomingMeta.remove(fid);
                break;
            }

            default:
                break;
        }
    }

    public void playAudio(Context context, Uri uri) {
        player.play(context, uri, null);
    }

    /**
     * Play a received audio message by its fileId.
     * Called by MessageAdapter when the user taps a received audio bubble.
     */
    public void playAudioById(Context context, String fileId) {
        Uri uri = fileId != null ? audioCache.get(fileId) : null;
        if (uri != null) {
            player.play(context, uri, null);
        }
    }

    public void stopPlayback() {
        player.stop();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Uri saveAudioToCache(Context context, byte[] data) {
        try {
            File file = new File(context.getCacheDir(),
                    "recv_audio_" + System.currentTimeMillis() + ".m4a");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
            return Uri.fromFile(file);
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] readAllBytes(Context context, Uri uri) throws IOException {
        if ("file".equals(uri.getScheme())) {
            File f = new File(uri.getPath());
            try (FileInputStream fis = new FileInputStream(f)) {
                return toByteArray(fis);
            }
        }
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            return toByteArray(is);
        }
    }

    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }
}
