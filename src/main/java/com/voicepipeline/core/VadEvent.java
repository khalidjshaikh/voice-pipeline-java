package com.voicepipeline.core;

public record VadEvent(VadEventType type, byte[] audioSegment, long timestampMs) {
    public static VadEvent speechStart(long ts) { return new VadEvent(VadEventType.SPEECH_START, null, ts); }
    public static VadEvent speechEnd(byte[] seg, long ts) { return new VadEvent(VadEventType.SPEECH_END, seg, ts); }
}
