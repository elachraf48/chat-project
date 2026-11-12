package com.javachat.mobile.transfer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.widget.Toast;

import com.javachat.mobile.config.ClientConfig;
import com.javachat.mobile.network.ConnectionManager;
import com.javachat.mobile.protocol.Message;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles outgoing and incoming file transfers.
 *
 * Protocol:
 *  1. FILE_META  — fileId, fileName, fileSize (Long), totalChunks (Integer)
 *  2. FILE_CHUNK — fileId, chunkIndex (Integer), data (base64 String)
 *  3. FILE_COMPLETE — fileId
 *
 * Fix BUG 5: saveToDownloads() now has a fallback for API 26–28.
 */
public class FileTransferManager {

    public interface TransferCallback {
        void onSendComplete(String fileName);
        void onSendFailed(String fileName);
    }


    private static FileTransferManager instance;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<String, ByteArrayOutputStream> incomingBuffers = new HashMap<>();
    private final Map<String, Message>               incomingMeta    = new HashMap<>();
    /** fileId → fully assembled bytes, retained so the user can tap Download later */
    private final Map<String, byte[]>                receivedFiles   = new HashMap<>();

    private FileTransferManager() {}

    public static synchronized FileTransferManager getInstance() {
        if (instance == null) instance = new FileTransferManager();
        return instance;
    }

    // -------------------------------------------------------------------------
    // Outgoing
    // -------------------------------------------------------------------------

    public void sendFile(Uri fileUri, Context context, TransferCallback callback) {
        executor.submit(() -> {
            String fileName = getFileName(context, fileUri);
            try {
                byte[] data = readAllBytes(context, fileUri);
                if (data == null) {
                    if (callback != null) callback.onSendFailed(fileName);
                    return;
                }
                if (data.length > ClientConfig.MAX_FILE_SIZE_BYTES) return;

                String fileId   = UUID.randomUUID().toString();
                int chunkSize   = ClientConfig.CHUNK_SIZE_BYTES;
                int totalChunks = (int) Math.ceil((double) data.length / chunkSize);

                ConnectionManager.getInstance().send(
                        Message.fileMeta(fileId, fileName, (long) data.length, totalChunks));

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * chunkSize;
                    int end   = Math.min(start + chunkSize, data.length);
                    byte[] chunk = new byte[end - start];
                    System.arraycopy(data, start, chunk, 0, chunk.length);
                    ConnectionManager.getInstance().send(
                            Message.fileChunk(fileId, i, Base64.encodeToString(chunk, Base64.NO_WRAP)));
                }

                ConnectionManager.getInstance().send(Message.fileComplete(fileId));
                if (callback != null) callback.onSendComplete(fileName);

            } catch (IOException e) {
                if (callback != null) callback.onSendFailed(fileName);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Incoming
    // -------------------------------------------------------------------------

    public void handleIncoming(Message message, Context context) {
        switch (message.getType()) {
            case FILE_META:
                if (message.getFileId() != null) {
                    incomingBuffers.put(message.getFileId(), new ByteArrayOutputStream());
                    incomingMeta.put(message.getFileId(), message);
                }
                break;

            case FILE_CHUNK: {
                String fid = message.getFileId();
                ByteArrayOutputStream buf = fid != null ? incomingBuffers.get(fid) : null;
                if (buf != null && message.getData() != null) {
                    byte[] chunk = Base64.decode(message.getData(), Base64.NO_WRAP);
                    buf.write(chunk, 0, chunk.length);
                }
                break;
            }

            case FILE_COMPLETE: {
                String fid = message.getFileId();
                ByteArrayOutputStream buf = fid != null ? incomingBuffers.remove(fid) : null;
                // Keep meta so MessageAdapter can read fileName; remove only after download
                Message meta = fid != null ? incomingMeta.get(fid) : null;
                if (buf != null && meta != null) {
                    // Store bytes for on-demand download — do NOT auto-save
                    receivedFiles.put(fid, buf.toByteArray());
                }
                break;
            }

            default:
                break;
        }
    }

    /**
     * Returns the fileName from the FILE_META that arrived for this transfer.
     */
    public String getFileNameForId(String fileId) {
        Message meta = fileId != null ? incomingMeta.get(fileId) : null;
        return meta != null ? meta.getFileName() : null;
    }

    /**
     * Returns the fileSize from the FILE_META that arrived for this transfer.
     */
    public Long getFileSizeForId(String fileId) {
        Message meta = fileId != null ? incomingMeta.get(fileId) : null;
        return meta != null ? meta.getFileSize() : null;
    }

    /**
     * Called when the user taps the Download button on a received file bubble.
     * Saves the pre-assembled bytes to the Downloads folder.
     */
    public void saveReceivedFile(String fileId, Context context) {
        byte[] data = fileId != null ? receivedFiles.get(fileId) : null;
        Message meta = fileId != null ? incomingMeta.get(fileId) : null;
        if (data == null || meta == null) return;

        String fileName = meta.getFileName();
        executor.submit(() -> saveToDownloads(context, fileName, data));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void saveToDownloads(Context context, String fileName, byte[] data) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: MediaStore Downloads
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = context.getContentResolver()
                        .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                        if (os != null) os.write(data);
                    }
                }
            } else {
                // BUG 5: API 26–28 fallback — write directly to public Downloads directory
                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null) {
                    downloadsDir.mkdirs();
                    File outFile = new File(downloadsDir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(data);
                    }
                }
            }
            // Show success toast only on the non-exception path
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            main.post(() -> Toast.makeText(context,
                    "📎 " + fileName + " saved to Downloads", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            // Notify user on main thread that the save failed
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            main.post(() -> Toast.makeText(context,
                    "Failed to save file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private byte[] readAllBytes(Context context, Uri uri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
            return buf.toByteArray();
        }
    }

    private String getFileName(Context context, Uri uri) {
        // Use ContentResolver cursor to get the real display name from the content URI
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = cursor.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {}
        // Fallback: last path segment (raw, but better than nothing)
        String segment = uri.getLastPathSegment();
        return segment != null ? segment : "file_" + System.currentTimeMillis();
    }
}
