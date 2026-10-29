package com.voicepipeline.core;

public interface AudioPlayer {
    void enqueue(byte[] pcm);
    void stop();
    void drain();
    boolean isPlaying();
    void close();
}
