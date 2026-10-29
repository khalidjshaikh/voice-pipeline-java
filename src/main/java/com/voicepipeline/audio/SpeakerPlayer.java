package com.voicepipeline.audio;

import com.voicepipeline.core.AudioPlayer;

import javax.sound.sampled.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class SpeakerPlayer implements AudioPlayer {
    private final int sampleRate;
    private final BlockingQueue<byte[]> queue;
    private SourceDataLine line;
    private volatile boolean running = true;
    private Thread thread;

    public SpeakerPlayer(int sampleRate, int capacity) throws LineUnavailableException {
        this.sampleRate = sampleRate;
        this.queue = new ArrayBlockingQueue<>(capacity);
        AudioFormat af = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(af, sampleRate * 2 / 10); // 100ms buffer
        line.start();
        thread = new Thread(this::run, "audio-playback");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        while (running) {
            try {
                byte[] data = queue.poll(100, TimeUnit.MILLISECONDS);
                if (data == null) continue;
                int off = 0;
                while (off < data.length && running) {
                    // check cancellation handled externally via stop() flushing
                    int w = line.write(data, off, data.length - off);
                    if (w <= 0) break;
                    off += w;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void enqueue(byte[] pcm) {
        if (pcm == null || pcm.length == 0) return;
        // drop oldest if full
        while (!queue.offer(pcm)) {
            queue.poll();
        }
    }

    @Override
    public void stop() {
        queue.clear();
        if (line != null) {
            line.flush();
        }
    }

    @Override
    public boolean isPlaying() {
        if (line == null) return false;
        if (!queue.isEmpty()) return true;
        return line.available() < line.getBufferSize();
    }

    public void drain() {
        while (!queue.isEmpty()) {
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (line != null) line.drain();
    }

    @Override
    public void close() {
        running = false;
        queue.clear();
        if (line != null) {
            line.drain();
            line.stop();
            line.close();
        }
        try { thread.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
