package com.voicepipeline.orchestration;

import com.voicepipeline.asr.OpenAiWhisperAsr;
import com.voicepipeline.audio.MicrophoneSource;
import com.voicepipeline.audio.SpeakerPlayer;
import com.voicepipeline.config.PipelineConfig;
import com.voicepipeline.core.AudioFormatSpec;
import com.voicepipeline.core.AudioPlayer;
import com.voicepipeline.core.ChatMessage;
import com.voicepipeline.core.Llm;
import com.voicepipeline.core.LlmRequest;
import com.voicepipeline.core.PipelineState;
import com.voicepipeline.core.Tts;
import com.voicepipeline.core.Vad;
import com.voicepipeline.core.VadEvent;
import com.voicepipeline.core.VadEventType;
import com.voicepipeline.llm.OpenAiChatLlm;
import com.voicepipeline.tts.OpenAiTts;
import com.voicepipeline.util.SentenceSplitter;
import com.voicepipeline.vad.EnergyVad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class VoicePipeline {
    private static final Logger log = LoggerFactory.getLogger(VoicePipeline.class);

    private final PipelineConfig cfg;
    private final EnergyVad vad;
    private final OpenAiWhisperAsr asr;
    private final Llm llm;
    private final Tts tts;
    private final MicrophoneSource mic;
    private final SpeakerPlayer player;

    private final BlockingQueue<AsrJob> asrQueue;
    private final BlockingQueue<LlmJob> llmQueue;
    private final BlockingQueue<TtsJob> ttsQueue;

    private final AtomicReference<PipelineState> state = new AtomicReference<>(PipelineState.IDLE);
    private final AtomicLong currentTurn = new AtomicLong(0);

    private final List<ChatMessage> history = new ArrayList<>();
    private final Object historyLock = new Object();

    private volatile boolean running = false;
    private ExecutorService workers;

    public VoicePipeline(PipelineConfig cfg) throws Exception {
        this.cfg = cfg;
        AudioFormatSpec fmt = cfg.captureFormat();
        this.vad = new EnergyVad(fmt.sampleRate(), cfg.audioFrameMs(),
                cfg.vadSpeechOnsetFrames(), cfg.vadSilenceHangoverFrames(),
                cfg.vadMinUtteranceMs(), cfg.vadNoiseMultiplier(), cfg.vadAbsoluteFloor());
        this.asr = new OpenAiWhisperAsr(cfg);
        this.llm = new OpenAiChatLlm(cfg);
        this.tts = new OpenAiTts(cfg);
        this.mic = new MicrophoneSource(fmt, cfg.audioFrameMs());
        this.player = new SpeakerPlayer(cfg.playbackSampleRate(), cfg.queueAudioCapacity());
        this.asrQueue = new ArrayBlockingQueue<>(cfg.queueAsrCapacity());
        this.llmQueue = new ArrayBlockingQueue<>(cfg.queueLlmCapacity());
        this.ttsQueue = new ArrayBlockingQueue<>(cfg.queueTtsCapacity());
    }

    public void start() throws Exception {
        running = true;
        state.set(PipelineState.LISTENING);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        workers.submit(this::asrWorker);
        workers.submit(this::llmWorker);
        workers.submit(this::ttsWorker);
        mic.start(this::onFrame);
        log.info("Pipeline started. Speak now.");
    }

    public void stop() {
        running = false;
        try { mic.stop(); } catch (Exception ignored) {}
        if (workers != null) {
            workers.shutdownNow();
            try { workers.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        player.close();
        state.set(PipelineState.IDLE);
        log.info("Pipeline stopped.");
    }

    private void onFrame(byte[] frame) {
        if (!running) return;
        // Re-enable the microphone once playback has finished.
        if (state.get() == PipelineState.SPEAKING && !player.isPlaying()) {
            state.set(PipelineState.LISTENING);
        }
        PipelineState ps = state.get();
        // Avoid feeding the assistant's own TTS back into ASR.
        if (cfg.vadMuteDuringPlayback() && ps == PipelineState.SPEAKING) {
            return;
        }
        if (!cfg.vadMuteDuringPlayback() && ps == PipelineState.SPEAKING) {
            vad.setThresholdMultiplier(cfg.vadBargeInMultiplier());
            vad.setOnsetFramesOverride(cfg.vadSpeechOnsetFrames() * 2);
        } else {
            vad.setThresholdMultiplier(1.0);
            vad.setOnsetFramesOverride(-1);
        }

        VadEvent ev;
        try {
            ev = vad.processFrame(frame).orElse(null);
        } catch (Exception e) {
            log.warn("VAD error", e);
            return;
        }
        if (ev == null) return;
        if (ev.type() == VadEventType.SPEECH_START) {
            // reset strict mode once user speech is detected
            vad.setThresholdMultiplier(1.0);
            vad.setOnsetFramesOverride(-1);
            long newTurn = currentTurn.incrementAndGet();
            log.debug("Speech start turn={}", newTurn);
            // barge-in if busy
            PipelineState s = state.get();
            if (s == PipelineState.PROCESSING || s == PipelineState.SPEAKING) {
                log.info("Barge-in: cancelling turn");
                asrQueue.clear();
                llmQueue.clear();
                ttsQueue.clear();
                player.stop();
            }
            state.set(PipelineState.LISTENING);
        } else if (ev.type() == VadEventType.SPEECH_END) {
            byte[] seg = ev.audioSegment();
            if (seg == null || seg.length == 0) return;
            long turn = currentTurn.get();
            // drop oldest if full
            while (!asrQueue.offer(new AsrJob(seg, turn))) {
                asrQueue.poll();
            }
            log.debug("Enqueued utterance turn={} bytes={}", turn, seg.length);
        }
    }

    private void asrWorker() {
        while (running) {
            AsrJob job;
            try {
                job = asrQueue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (job == null) continue;
            if (job.turn() != currentTurn.get()) continue;
            state.set(PipelineState.PROCESSING);
            try {
                String text = asr.transcribe(job.pcm());
                if (job.turn() != currentTurn.get()) continue;
                if (text == null || text.isBlank()) {
                    log.debug("Empty transcription");
                    state.set(PipelineState.LISTENING);
                    continue;
                }
                log.info("ASR: {}", text);
                List<ChatMessage> snap;
                synchronized (historyLock) { snap = new ArrayList<>(history); }
                while (!llmQueue.offer(new LlmJob(text, job.turn()))) {
                    llmQueue.poll();
                }
            } catch (Exception e) {
                log.error("ASR failed", e);
                if (job.turn() == currentTurn.get()) state.set(PipelineState.LISTENING);
            }
        }
    }

    private void llmWorker() {
        while (running) {
            LlmJob job;
            try {
                job = llmQueue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (job == null) continue;
            if (job.turn() != currentTurn.get()) continue;
            state.set(PipelineState.PROCESSING);
            SentenceSplitter splitter = new SentenceSplitter(120);
            try {
                List<ChatMessage> snap;
                synchronized (historyLock) { snap = new ArrayList<>(history); }
                String full = llm.stream(new LlmRequest(job.text(), snap), token -> {
                    if (job.turn() != currentTurn.get()) throw new RuntimeException("cancelled");
                    List<String> sentences = splitter.append(token);
                    for (String s : sentences) {
                        if (job.turn() != currentTurn.get()) throw new RuntimeException("cancelled");
                        while (job.turn() == currentTurn.get() && !ttsQueue.offer(new TtsJob(s, job.turn()))) {
                            ttsQueue.poll();
                        }
                    }
                });
                if (job.turn() != currentTurn.get()) continue;
                String rest = splitter.flush();
                if (!rest.isBlank()) {
                    while (job.turn() == currentTurn.get() && !ttsQueue.offer(new TtsJob(rest, job.turn()))) {
                        ttsQueue.poll();
                    }
                }
                log.info("LLM: {}", full);
                synchronized (historyLock) {
                    history.add(new ChatMessage("user", job.text()));
                    history.add(new ChatMessage("assistant", full));
                    int max = cfg.conversationMaxTurns() * 2;
                    while (history.size() > max) history.remove(0);
                }
            } catch (Exception e) {
                if (!running || e instanceof InterruptedException) {
                    log.debug("LLM interrupted during shutdown");
                } else if (job.turn() == currentTurn.get()) {
                    log.error("LLM failed", e);
                    try {
                        while (job.turn() == currentTurn.get() && !ttsQueue.offer(new TtsJob("Sorry, I had trouble responding.", job.turn()))) {
                            ttsQueue.poll();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void ttsWorker() {
        while (running) {
            TtsJob job;
            try {
                job = ttsQueue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (job == null) continue;
            if (job.turn() != currentTurn.get()) continue;
            try {
                byte[] audio = tts.synthesize(job.text());
                if (job.turn() != currentTurn.get()) continue;
                if (audio != null && audio.length > 0) {
                    state.set(PipelineState.SPEAKING);
                    player.enqueue(audio);
                    log.debug("TTS: {} ({} bytes)", job.text(), audio.length);
                }
            } catch (Exception e) {
                log.error("TTS failed: {}", job.text(), e);
            }
        }
    }

    public PipelineState state() { return state.get(); }
    public AudioPlayer player() { return player; }
}
