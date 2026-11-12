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
                        // MediaPlayer completion fires on an internal thread — post to main thread
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(listener::onComplete);
                    }
                });
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
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
