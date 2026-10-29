package com.voicepipeline.vad;

import com.voicepipeline.core.VadEvent;
import com.voicepipeline.core.VadEventType;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EnergyVadTest {
    private static byte[] frame(int amplitude) {
        byte[] b = new byte[640];
        for (int i = 0; i < 320; i++) {
            int v = amplitude;
            b[i*2] = (byte)(v & 0xFF);
            b[i*2+1] = (byte)((v >> 8) & 0xFF);
        }
        return b;
    }
    private static byte[] silence() { return new byte[640]; }

    @Test
    void detectsSpeechThenEnd() {
        EnergyVad vad = new EnergyVad(16000, 20, 3, 5, 100, 3.0, 100.0);
        // silence baseline
        for (int i = 0; i < 5; i++) vad.processFrame(silence());
        Optional<VadEvent> start = Optional.empty();
        for (int i = 0; i < 3; i++) {
            Optional<VadEvent> e = vad.processFrame(frame(20000));
            if (e.isPresent()) start = e;
        }
        assertTrue(start.isPresent(), "expected speech start");
        assertEquals(VadEventType.SPEECH_START, start.get().type());
        // hangover
        Optional<VadEvent> end = Optional.empty();
        for (int i = 0; i < 8; i++) {
            Optional<VadEvent> e = vad.processFrame(silence());
            if (e.isPresent()) end = e;
        }
        assertTrue(end.isPresent(), "expected speech end");
        assertEquals(VadEventType.SPEECH_END, end.get().type());
        assertNotNull(end.get().audioSegment());
    }
}
