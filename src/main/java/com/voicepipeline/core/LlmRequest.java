package com.voicepipeline.core;

import java.util.List;

public record LlmRequest(String userText, List<ChatMessage> history) {}
