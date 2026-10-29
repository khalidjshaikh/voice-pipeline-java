package com.voicepipeline.core;

public interface Tts {
    byte[] synthesize(String text) throws Exception;
}
