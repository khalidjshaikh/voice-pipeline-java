package com.voicepipeline.audio;

import com.voicepipeline.core.AudioFormatSpec;

import javax.sound.sampled.*;
import java.util.function.Consumer;

public class MicrophoneSource {
    private final AudioFormatSpec format;
    private final int frameBytes;
    private TargetDataLine line;
    private volatile boolean running = false;
    private Thread thread;

    public MicrophoneSource(AudioFormatSpec format, int frameMs) {
        this.format = format;
        this.frameBytes = format.sampleRate() * format.bytesPerFrame() * frameMs / 1000;
    }

    public void start(Consumer<byte[]> onFrame) throws LineUnavailableException {
        AudioFormat af = new AudioFormat(format.sampleRate(), format.bitsPerSample(),
                format.channels(), format.signed(), format.bigEndian());
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, af);
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(af, frameBytes * 4);
        line.start();
        running = true;
        thread = new Thread(() -> {
            byte[] buf = new byte[frameBytes];
            while (running) {
                int r = line.read(buf, 0, buf.length);
                if (r > 0) {
                    byte[] frame = new byte[r];
                    System.arraycopy(buf, 0, frame, 0, r);
                    onFrame.accept(frame);
                }
            }
        }, "mic-capture");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        if (thread != null) {
            try { thread.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }
}
