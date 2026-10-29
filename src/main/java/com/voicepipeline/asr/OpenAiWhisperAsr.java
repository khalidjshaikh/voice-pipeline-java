package com.voicepipeline.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicepipeline.config.PipelineConfig;
import com.voicepipeline.core.Asr;
import com.voicepipeline.util.HttpUtil;
import com.voicepipeline.util.WavUtil;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OpenAiWhisperAsr implements Asr {
    private static final ObjectMapper M = new ObjectMapper();
    private final PipelineConfig cfg;

    public OpenAiWhisperAsr(PipelineConfig cfg) { this.cfg = cfg; }

    @Override
    public String transcribe(byte[] pcm16k) throws Exception {
        byte[] wav = WavUtil.pcmToWav(pcm16k, cfg.audioSampleRate(), 1, 16);
        String boundary = "----VoicePipelineBoundary" + System.currentTimeMillis();
        // build multipart body
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        String CRLF = "\r\n";
        // file part
        baos.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"" + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: audio/wav" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(wav);
        baos.write(CRLF.getBytes(StandardCharsets.UTF_8));
        // model part
        baos.write(("--" + boundary + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"model\"" + CRLF + CRLF).getBytes(StandardCharsets.UTF_8));
        baos.write(cfg.asrModel().getBytes(StandardCharsets.UTF_8));
        baos.write(CRLF.getBytes(StandardCharsets.UTF_8));
        // language optional (none)
        baos.write(("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));

        Map<String, String> headers = Map.of("Authorization", "Bearer " + cfg.openaiApiKey());
        var resp = HttpUtil.postBytes(cfg.openaiBaseUrl() + "/audio/transcriptions", headers,
                baos.toByteArray(), "multipart/form-data; boundary=" + boundary, 3);
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("Whisper HTTP " + resp.statusCode() + ": " + new String(resp.body()));
        }
        JsonNode node = M.readTree(resp.body());
        JsonNode t = node.get("text");
        return t == null ? "" : t.asText().trim();
    }
}
