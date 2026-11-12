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
     * Open a file chooser, validate, then send on a background thread.
     *
     * @param onSelected  called on the EDT right after the user picks a valid file,
     *                    with (fileName, fileSize) so the caller can show a sent bubble
     *                    immediately. May be null.
     */
    public void selectAndSendFile(java.util.function.BiConsumer<String, Long> onSelected) {
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

        if (onSelected != null) {
            javax.swing.SwingUtilities.invokeLater(
                    () -> onSelected.accept(file.getName(), file.length()));
        }

        Thread sender = new Thread(() -> doSend(file), "file-sender");
        sender.setDaemon(true);
        sender.start();
    }

    /** Backwards-compat overload — no bubble callback. */
    public void selectAndSendFile() {
        selectAndSendFile(null);
    }

    // -------------------------------------------------------------------------

    private void doSend(File file) {
        String fileId      = UUID.randomUUID().toString();
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
