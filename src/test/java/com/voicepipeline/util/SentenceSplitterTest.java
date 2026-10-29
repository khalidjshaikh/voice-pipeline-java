package com.voicepipeline.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SentenceSplitterTest {
    @Test
    void splitsOnPunctuation() {
        SentenceSplitter s = new SentenceSplitter(1000);
        var out = s.append("Hello world. How are you? Fine!");
        assertEquals(3, out.size());
        assertEquals("Hello world.", out.get(0));
        assertEquals("How are you?", out.get(1));
        assertEquals("Fine!", out.get(2));
    }

    @Test
    void flushesRemainder() {
        SentenceSplitter s = new SentenceSplitter(1000);
        s.append("Hello");
        assertEquals("Hello", s.flush());
        assertEquals("", s.flush());
    }
}
