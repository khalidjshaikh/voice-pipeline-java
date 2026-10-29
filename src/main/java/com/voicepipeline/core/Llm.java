package com.voicepipeline.core;

public interface Llm {
    String stream(LlmRequest request, TokenCallback callback) throws Exception;
}
