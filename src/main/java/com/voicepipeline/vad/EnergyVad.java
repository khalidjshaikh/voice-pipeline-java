package com.voicepipeline.vad;

import com.voicepipeline.core.Vad;
import com.voicepipeline.core.VadEvent;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EnergyVad implements Vad {
    private final int sampleRate;
    private final int frameMs;
    private final int speechOnsetFrames;
    private final int silenceHangoverFrames;
    private final int minUtteranceMs;
    private final double noiseMultiplier;
    private final double absoluteFloor;

    private volatile double thresholdMultiplier = 1.0;
    private volatile int onsetOverride = -1;

    private double noiseFloor = -1;
    private boolean inSpeech = false;
    private int onsetCounter = 0;
    private int silenceCounter = 0;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final List<byte[]> preRoll = new ArrayList<>();

    public EnergyVad(int sampleRate, int frameMs, int speechOnsetFrames, int silenceHangoverFrames,
                     int minUtteranceMs, double noiseMultiplier, double absoluteFloor) {
        this.sampleRate = sampleRate;
        this.frameMs = frameMs;
        this.speechOnsetFrames = speechOnsetFrames;
        this.silenceHangoverFrames = silenceHangoverFrames;
        this.minUtteranceMs = minUtteranceMs;
        this.noiseMultiplier = noiseMultiplier;
        this.absoluteFloor = absoluteFloor;
    }

    @Override
    public synchronized Optional<VadEvent> processFrame(byte[] pcmFrame) {
        if (pcmFrame == null || pcmFrame.length == 0) return Optional.empty();
        double energy = rms(pcmFrame);
        if (noiseFloor < 0) noiseFloor = Math.max(energy, 1.0);
        if (!inSpeech) {
            if (energy < noiseFloor) noiseFloor = energy;
            else noiseFloor = noiseFloor * 0.995 + energy * 0.005;
        }
        int requiredOnset = (onsetOverride > 0) ? onsetOverride : speechOnsetFrames;
        double threshold = Math.max(noiseFloor * noiseMultiplier * thresholdMultiplier, absoluteFloor);

        byte[] copy = pcmFrame.clone();
        if (!inSpeech) {
            preRoll.add(copy);
            if (preRoll.size() > requiredOnset) preRoll.remove(0);
            if (energy >= threshold) {
                onsetCounter++;
            } else {
                onsetCounter = 0;
            }
            if (onsetCounter >= requiredOnset) {
                inSpeech = true;
                silenceCounter = 0;
                buffer.reset();
                for (byte[] f : preRoll) {
                    try { buffer.write(f); } catch (Exception ignored) {}
                }
                preRoll.clear();
                return Optional.of(VadEvent.speechStart(System.currentTimeMillis()));
            }
            return Optional.empty();
        } else {
            try { buffer.write(copy); } catch (Exception ignored) {}
            if (energy < threshold) {
                silenceCounter++;
                if (silenceCounter >= silenceHangoverFrames) {
                    inSpeech = false;
                    onsetCounter = 0;
                    int sc = silenceCounter;
                    silenceCounter = 0;
                    byte[] seg = buffer.toByteArray();
                    buffer.reset();
                    long durMs = (long) (seg.length * 1000.0 / (sampleRate * 2));
                    if (durMs >= minUtteranceMs) {
                        return Optional.of(VadEvent.speechEnd(seg, System.currentTimeMillis()));
                    }
                    return Optional.empty();
                }
            } else {
                silenceCounter = 0;
            }
            return Optional.empty();
        }
    }

    public void setThresholdMultiplier(double m) { this.thresholdMultiplier = m; }
    public void setOnsetFramesOverride(int n) { this.onsetOverride = n; }

    @Override
    public synchronized void reset() {
        inSpeech = false;
        onsetCounter = 0;
        silenceCounter = 0;
        buffer.reset();
        preRoll.clear();
        noiseFloor = -1;
    }

    private static double rms(byte[] pcm) {
        if (pcm.length < 2) return 0;
        long sum = 0;
        int n = pcm.length / 2;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (pcm[i] & 0xFF) | (pcm[i + 1] << 8);
            if (s > 32767) s -= 65536;
            sum += (long) s * s;
        }
        return Math.sqrt(sum / (double) n);
    }
}
