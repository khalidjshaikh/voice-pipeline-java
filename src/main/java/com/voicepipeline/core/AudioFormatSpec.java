package com.voicepipeline.core;

import java.util.List;

public record AudioFormatSpec(int sampleRate, int channels, int bitsPerSample, boolean signed, boolean bigEndian) {
    public int bytesPerFrame() { return channels * bitsPerSample / 8; }
    public int bytesPerSecond() { return sampleRate * bytesPerFrame(); }
}
