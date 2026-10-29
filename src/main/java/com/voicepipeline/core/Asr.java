package com.voicepipeline.core;

public interface Asr {
    String transcribe(byte[] pcm16k) throws Exception;
}
