package com.javachat.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Records audio from the default microphone using the Java Sound API.
 * Records until {@link #stop()} is called, then returns the raw PCM bytes.
 */
public class AudioRecorder {

    /** 16-bit mono, 16 kHz — compatible with most platforms and with Android MediaPlayer. */
    private static final AudioFormat FORMAT =
            new AudioFormat(16000f, 16, 1, true, false);

    private TargetDataLine line;
    private Thread recordThread;
    private ByteArrayOutputStream buffer;
    private volatile boolean recording = false;

    /**
     * Start recording. Returns immediately; recording runs on a background thread.
     * @throws Exception if no microphone is available.
     */
    public void start() throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new Exception("Microphone not supported on this system.");
        }
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();

        buffer = new ByteArrayOutputStream();
        recording = true;

        recordThread = new Thread(() -> {
            byte[] chunk = new byte[4096];
            while (recording) {
                int read = line.read(chunk, 0, chunk.length);
                if (read > 0) {
                    buffer.write(chunk, 0, read);
                }
            }
        }, "audio-recorder");
        recordThread.setDaemon(true);
        recordThread.start();
    }

    /**
     * Stop recording.
     * @return the raw PCM audio bytes captured since {@link #start()}.
     */
    public byte[] stop() throws IOException {
        recording = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        if (recordThread != null) {
            try { recordThread.join(2000); } catch (InterruptedException ignored) {}
        }
        return buffer != null ? buffer.toByteArray() : new byte[0];
    }

    public boolean isRecording() { return recording; }
}
