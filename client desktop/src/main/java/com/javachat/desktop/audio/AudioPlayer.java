package com.javachat.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays raw PCM audio bytes through the default audio output.
 */
public class AudioPlayer {

    /** Must match AudioRecorder.FORMAT */
    private static final AudioFormat FORMAT =
            new AudioFormat(16000f, 16, 1, true, false);

    /**
     * Play raw PCM bytes synchronously on the calling thread.
     * Call from a background thread — never from the EDT.
     */
    public void play(byte[] pcmData) throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new Exception("Audio output not supported on this system.");
        }
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(FORMAT);
            line.start();
            line.write(pcmData, 0, pcmData.length);
            line.drain();
        }
    }
}
