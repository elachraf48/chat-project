package com.javachat.desktop.transfer;

import com.javachat.desktop.network.ConnectionManager;
import com.javachat.desktop.protocol.Message;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * Handles outgoing file transfers.
 *
 * Protocol (Phase 7 — matches server FileTransferManager):
 *   1. FILE_META  — fileId (UUID), fileName, fileSize, totalChunks
 *   2. FILE_CHUNK — fileId, chunkIndex (0-based), data (Base64)  × N
 *   3. FILE_COMPLETE — fileId
 */
public class FileTransferManager {

    private static final long MAX_FILE_SIZE  = 50L * 1024 * 1024; // 50 MB
    private static final int  CHUNK_SIZE     = 64 * 1024;          // 64 KB

    private final ConnectionManager connectionManager;
    private final JFrame parent;
    private FileProgressListener progressListener;

    public FileTransferManager(ConnectionManager connectionManager, JFrame parent) {
        this.connectionManager = connectionManager;
        this.parent = parent;
    }

    public void setProgressListener(FileProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Functional interface for the file-selected callback.
     * fileId  — UUID assigned to this transfer
     * fileName — display name of the file
     * fileData — raw bytes of the file (may be null if reading fails)
     */
    @FunctionalInterface
    public interface SendSelectedCallback {
        void onSelected(String fileId, String fileName, byte[] fileData);
    }

    /**
     * Open a file chooser, validate, then send on a background thread.
     * The callback is called on the EDT once the file is read, before network send,
     * so the caller can cache the bytes for sender-side preview/save.
     */
    public void selectAndSendFile(SendSelectedCallback onSelected) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select file to send");
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.exists()) {
            showError("File not found: " + file.getName());
            return;
        }
        if (file.length() > MAX_FILE_SIZE) {
            showError("File is too large (max 50 MB): " + file.getName());
            return;
        }

        String fileId = java.util.UUID.randomUUID().toString();

        // Read bytes on background thread to avoid blocking EDT on large files
        Thread sender = new Thread(() -> {
            byte[] fileData = null;
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] tmp = new byte[8192];
                int n;
                while ((n = fis.read(tmp)) != -1) buf.write(tmp, 0, n);
                fileData = buf.toByteArray();
            } catch (IOException ignored) {}

            // Notify caller with bytes before network send (EDT)
            if (onSelected != null) {
                final byte[] data = fileData;
                javax.swing.SwingUtilities.invokeLater(
                        () -> onSelected.onSelected(fileId, file.getName(), data));
            }
            doSend(file, fileId);
        }, "file-sender");
        sender.setDaemon(true);
        sender.start();
    }

    /** Backwards-compat overload — no callback. */
    public void selectAndSendFile() {
        selectAndSendFile(null);
    }

    // -------------------------------------------------------------------------

    private void doSend(File file, String fileId) {
        String fileName    = file.getName();
        long   fileSize    = file.length();
        int    totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);

        try {
            // 1 — FILE_META
            connectionManager.send(Message.fileMeta(fileId, fileName, fileSize, totalChunks));

            // 2 — FILE_CHUNK × totalChunks
            byte[] buf = new byte[CHUNK_SIZE];
            int chunkIndex = 0;
            long sent = 0;
            try (FileInputStream fis = new FileInputStream(file)) {
                int read;
                while ((read = fis.read(buf)) != -1) {
                    String encoded = Base64.getEncoder()
                            .encodeToString(java.util.Arrays.copyOf(buf, read));
                    connectionManager.send(Message.fileChunk(fileId, chunkIndex, encoded));
                    chunkIndex++;
                    sent += read;
                    if (progressListener != null) {
                        final long s = sent;
                        progressListener.onProgress(fileName, s, fileSize);
                    }
                }
            }

            // 3 — FILE_COMPLETE
            connectionManager.send(Message.fileComplete(fileId));

            if (progressListener != null) progressListener.onComplete(fileName);

        } catch (IOException e) {
            if (progressListener != null) progressListener.onError(fileName, e.getMessage());
        }
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(parent, msg, "File Transfer Error", JOptionPane.ERROR_MESSAGE);
    }
}
