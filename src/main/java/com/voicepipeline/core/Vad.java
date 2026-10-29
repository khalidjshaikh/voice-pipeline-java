package com.voicepipeline.core;

public interface Vad {
    java.util.Optional<VadEvent> processFrame(byte[] pcmFrame);
    void reset();
}
