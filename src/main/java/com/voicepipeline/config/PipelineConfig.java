package com.voicepipeline.config;

import com.voicepipeline.core.AudioFormatSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record PipelineConfig(
        String openaiApiKey,
        String openaiBaseUrl,
        String asrModel,
        String llmModel,
        String ttsModel,
        String ttsVoice,
        int audioSampleRate,
        int audioFrameMs,
        int vadSpeechOnsetFrames,
        int vadSilenceHangoverFrames,
        int vadMinUtteranceMs,
        double vadNoiseMultiplier,
        double vadAbsoluteFloor,
        boolean vadMuteDuringPlayback,
        double vadBargeInMultiplier,
        int queueAsrCapacity,
        int queueLlmCapacity,
        int queueTtsCapacity,
        int queueAudioCapacity,
        int conversationMaxTurns,
        int playbackSampleRate
) {
    public static PipelineConfig load() throws IOException {
        Properties p = new Properties();
        try (InputStream in = PipelineConfig.class.getResourceAsStream("/application.properties")) {
            if (in == null) throw new IOException("application.properties not found on classpath");
            p.load(in);
        }
        // allow env override for apiKey
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) apiKey = resolve(p.getProperty("openai.apiKey", ""));
        // resolve ${ENV} placeholders
        apiKey = resolveEnv(apiKey);
        String baseUrl = resolveEnv(p.getProperty("openai.baseUrl", "https://api.openai.com/v1"));
        return new PipelineConfig(
                apiKey,
                baseUrl,
                p.getProperty("openai.asrModel", "whisper-1"),
                p.getProperty("openai.llmModel", "gpt-4o-mini"),
                p.getProperty("openai.ttsModel", "tts-1"),
                p.getProperty("openai.ttsVoice", "alloy"),
                Integer.parseInt(p.getProperty("audio.sampleRate", "16000")),
                Integer.parseInt(p.getProperty("audio.frameMs", "20")),
                Integer.parseInt(p.getProperty("vad.speechOnsetFrames", "3")),
                Integer.parseInt(p.getProperty("vad.silenceHangoverFrames", "15")),
                Integer.parseInt(p.getProperty("vad.minUtteranceMs", "300")),
                Double.parseDouble(p.getProperty("vad.noiseMultiplier", "3.0")),
                Double.parseDouble(p.getProperty("vad.absoluteFloor", "500.0")),
                Boolean.parseBoolean(p.getProperty("vad.muteDuringPlayback", "true")),
                Double.parseDouble(p.getProperty("vad.bargeInMultiplier", "4.0")),
                Integer.parseInt(p.getProperty("queue.asrCapacity", "2")),
                Integer.parseInt(p.getProperty("queue.llmCapacity", "2")),
                Integer.parseInt(p.getProperty("queue.ttsCapacity", "4")),
                Integer.parseInt(p.getProperty("queue.audioCapacity", "8")),
                Integer.parseInt(p.getProperty("conversation.maxTurns", "10")),
                Integer.parseInt(p.getProperty("playback.sampleRate", "24000"))
        );
    }

    public static PipelineConfig load(Path path) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) { p.load(in); }
        // reuse by writing temp? simpler: delegate via system property not; just inline load from properties
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) apiKey = resolveEnv(p.getProperty("openai.apiKey", ""));
        String baseUrl = resolveEnv(p.getProperty("openai.baseUrl", "https://api.openai.com/v1"));
        return new PipelineConfig(
                apiKey, baseUrl,
                p.getProperty("openai.asrModel", "whisper-1"),
                p.getProperty("openai.llmModel", "gpt-4o-mini"),
                p.getProperty("openai.ttsModel", "tts-1"),
                p.getProperty("openai.ttsVoice", "alloy"),
                Integer.parseInt(p.getProperty("audio.sampleRate", "16000")),
                Integer.parseInt(p.getProperty("audio.frameMs", "20")),
                Integer.parseInt(p.getProperty("vad.speechOnsetFrames", "3")),
                Integer.parseInt(p.getProperty("vad.silenceHangoverFrames", "15")),
                Integer.parseInt(p.getProperty("vad.minUtteranceMs", "300")),
                Double.parseDouble(p.getProperty("vad.noiseMultiplier", "3.0")),
                Double.parseDouble(p.getProperty("vad.absoluteFloor", "500.0")),
                Boolean.parseBoolean(p.getProperty("vad.muteDuringPlayback", "true")),
                Double.parseDouble(p.getProperty("vad.bargeInMultiplier", "4.0")),
                Integer.parseInt(p.getProperty("queue.asrCapacity", "2")),
                Integer.parseInt(p.getProperty("queue.llmCapacity", "2")),
                Integer.parseInt(p.getProperty("queue.ttsCapacity", "4")),
                Integer.parseInt(p.getProperty("queue.audioCapacity", "8")),
                Integer.parseInt(p.getProperty("conversation.maxTurns", "10")),
                Integer.parseInt(p.getProperty("playback.sampleRate", "24000"))
        );
    }

    private static String resolve(String v) { return v == null ? "" : v; }

    private static String resolveEnv(String v) {
        if (v == null) return "";
        if (v.startsWith("${") && v.endsWith("}")) {
            String name = v.substring(2, v.length() - 1);
            String env = System.getenv(name);
            return env == null ? "" : env;
        }
        return v;
    }

    public AudioFormatSpec captureFormat() {
        return new AudioFormatSpec(audioSampleRate, 1, 16, true, false);
    }
}
