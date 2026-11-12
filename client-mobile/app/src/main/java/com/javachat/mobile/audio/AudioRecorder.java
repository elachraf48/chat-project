package com.javachat.mobile.audio;

import android.content.Context;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.IOException;

/**
 * Records audio to a temporary file using MediaRecorder.
 * Output format: AAC in M4A container (compatible with both Android and desktop playback).
 */
public class AudioRecorder {

    private MediaRecorder recorder;
    private File outputFile;

    /** Start recording to a temp file. Returns the output file path. */
    public String startRecording(Context context) throws IOException {
        outputFile = new File(context.getCacheDir(),
                "audio_" + System.currentTimeMillis() + ".m4a");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            recorder = new MediaRecorder(context);
        } else {
            //noinspection deprecation — no-arg constructor is correct on API 26–30 (pre-S)
            recorder = new MediaRecorder();
        }

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        try {
            recorder.prepare();
        } catch (IOException e) {
            recorder.release();
            recorder = null;
            throw e; // propagate so caller can handle the failure
        }
        recorder.start();

        return outputFile.getAbsolutePath();
    }

    /** Stop recording. Returns a URI to the recorded file. */
    public Uri stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                // Handle edge case where stop is called before any audio was recorded
            } finally {
                recorder.release();
                recorder = null;
            }
        }
        return outputFile != null ? Uri.fromFile(outputFile) : null;
    }

    public boolean isRecording() {
        return recorder != null;
    }

    public void release() {
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }
}
