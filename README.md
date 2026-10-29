# Real-Time Voice Processing Pipeline (Java)

Audio capture → VAD → ASR (OpenAI Whisper) → LLM (GPT-4o, streaming) → TTS (OpenAI) → playback,
with async, bounded, queue-based handoffs and barge-in (interruption).

## Requirements
- Java 21+ (tested on 25/27)
- Maven 3.9+
- `OPENAI_API_KEY` environment variable

## Build
```bash
mvn -q package
```

## Run
```bash
export OPENAI_API_KEY=sk-...
java -jar target/voice-pipeline-1.0.0-SNAPSHOT.jar
```
Speak into the microphone. Press Ctrl+C to stop.

## Configuration
Edit `src/main/resources/application.properties` (or pass your own via classpath) for models, VAD thresholds, queue sizes, etc.

## Tests
```bash
mvn test
```

## Architecture
See package layout under `com.voicepipeline`: `core` (interfaces), `audio`, `vad`, `asr`, `llm`, `tts`, `orchestration`, `util`, `config`.
