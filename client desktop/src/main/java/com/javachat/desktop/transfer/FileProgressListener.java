package com.javachat.desktop.transfer;

/**
 * Callback interface for file transfer progress updates.
 * Implementations must dispatch UI updates to the EDT.
 */
public interface FileProgressListener {

    /** Called periodically with the number of bytes transferred so far. */
    void onProgress(String filename, long bytesTransferred, long totalBytes);

    /** Called when the transfer completes successfully. */
    void onComplete(String filename);

    /** Called if the transfer fails or is interrupted. */
    void onError(String filename, String reason);
}
