package com.voicepipeline.core;

@FunctionalInterface
public interface TokenCallback {
    void onToken(String token) throws Exception;
}
