package com.voicepipeline;

import com.voicepipeline.config.PipelineConfig;
import com.voicepipeline.orchestration.VoicePipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            PipelineConfig cfg = PipelineConfig.load();
            if (cfg.openaiApiKey() == null || cfg.openaiApiKey().isBlank()) {
                System.err.println("OPENAI_API_KEY is not set. Export it or set openai.apiKey in application.properties.");
                System.exit(2);
            }
            VoicePipeline pipeline = new VoicePipeline(cfg);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down...");
                pipeline.stop();
            }));
            pipeline.start();
            // keep main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Fatal", e);
            System.exit(1);
        }
    }
}
