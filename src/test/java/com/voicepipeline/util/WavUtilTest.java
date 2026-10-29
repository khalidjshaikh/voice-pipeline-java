package com.voicepipeline.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WavUtilTest {
    @Test
    void headerMagicAndLength() {
        byte[] pcm = new byte[1000];
        byte[] wav = WavUtil.pcmToWav(pcm, 16000, 1, 16);
        assertEquals(44 + 1000, wav.length);
        assertEquals('R', wav[0]); assertEquals('I', wav[1]); assertEquals('F', wav[2]); assertEquals('F', wav[3]);
        assertEquals('W', wav[8]); assertEquals('A', wav[9]); assertEquals('V', wav[10]); assertEquals('E', wav[11]);
    }
}
