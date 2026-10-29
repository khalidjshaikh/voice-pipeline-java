package com.voicepipeline.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voicepipeline.config.PipelineConfig;
import com.voicepipeline.core.Tts;
import com.voicepipeline.util.HttpUtil;

import java.util.Map;

public class OpenAiTts implements Tts {
    private static final ObjectMapper M = new ObjectMapper();
    private final PipelineConfig cfg;

    public OpenAiTts(PipelineConfig cfg) { this.cfg = cfg; }

    @Override
    public byte[] synthesize(String text) throws Exception {
        if (text == null || text.isBlank()) return new byte[0];
        ObjectNode body = M.createObjectNode();
        body.put("model", cfg.ttsModel());
        body.put("input", text);
        body.put("voice", cfg.ttsVoice());
        body.put("response_format", "pcm");

        Map<String, String> headers = Map.of("Authorization", "Bearer " + cfg.openaiApiKey());
        var resp = HttpUtil.postBytes(cfg.openaiBaseUrl() + "/audio/speech", headers,
                M.writeValueAsBytes(body), "application/json", 3);
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("TTS HTTP " + resp.statusCode() + ": " + new String(resp.body()));
        }
        return resp.body();
    }
}
