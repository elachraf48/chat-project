package com.javachat.mobile.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.IOException;

/**
 * Plays audio from a URI (local file or content URI).
 */
public class AudioPlayer {

    private MediaPlayer mediaPlayer;

    /** Play audio from the given URI. Releases any previously playing audio. */
    public void play(Context context, Uri uri, OnCompletionListener listener) {
        release();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.setOnCompletionListener(mp -> {
                    release();
                    if (listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(listener::onComplete);
                    }
                });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    android.util.Log.e("AudioPlayer", "MediaPlayer error what=" + what + " extra=" + extra);
                    release();
                    return true; // handled — prevents IllegalStateException propagation
                });
            mediaPlayer.prepare(); // synchronous; safe for local files / cache URIs
            mediaPlayer.start();
        } catch (IOException | IllegalStateException e) {
            android.util.Log.e("AudioPlayer", "play() failed: " + e.getMessage(), e);
            release();
        }
    }

    public void stop() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        release();
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    private void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public interface OnCompletionListener {
        void onComplete();
    }
}
