package com.voicepipeline.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voicepipeline.config.PipelineConfig;
import com.voicepipeline.core.ChatMessage;
import com.voicepipeline.core.Llm;
import com.voicepipeline.core.LlmRequest;
import com.voicepipeline.core.TokenCallback;
import com.voicepipeline.util.HttpUtil;

import java.util.List;
import java.util.Map;

public class OpenAiChatLlm implements Llm {
    private static final ObjectMapper M = new ObjectMapper();
    private final PipelineConfig cfg;

    public OpenAiChatLlm(PipelineConfig cfg) { this.cfg = cfg; }

    @Override
    public String stream(LlmRequest request, TokenCallback callback) throws Exception {
        ObjectNode body = M.createObjectNode();
        body.put("model", cfg.llmModel());
        body.put("stream", true);
        body.put("temperature", 0.7);
        body.put("max_tokens", 512);

        ArrayNode messages = M.createArrayNode();
        ObjectNode sys = M.createObjectNode();
        sys.put("role", "system");
        sys.put("content", "You are a helpful voice assistant. Keep replies concise and conversational, 1-3 sentences.");
        messages.add(sys);

        List<ChatMessage> history = request.history();
        if (history != null) {
            for (ChatMessage m : history) {
                ObjectNode mm = M.createObjectNode();
                mm.put("role", m.role());
                mm.put("content", m.content());
                messages.add(mm);
            }
        }
        ObjectNode um = M.createObjectNode();
        um.put("role", "user");
        um.put("content", request.userText());
        messages.add(um);
        body.set("messages", messages);

        StringBuilder full = new StringBuilder();
        Map<String, String> headers = Map.of("Authorization", "Bearer " + cfg.openaiApiKey());
        HttpUtil.streamSse(cfg.openaiBaseUrl() + "/chat/completions", headers, M.writeValueAsString(body), data -> {
            try {
                JsonNode node = M.readTree(data);
                JsonNode choices = node.get("choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    JsonNode delta = choices.get(0).get("delta");
                    if (delta != null) {
                        JsonNode content = delta.get("content");
                        if (content != null && !content.isNull()) {
                            String tok = content.asText();
                            full.append(tok);
                            if (callback != null) callback.onToken(tok);
                        }
                    }
                }
            } catch (Exception e) {
                // ignore parse errors for keepalive chunks
            }
        });
        return full.toString().trim();
    }
}
