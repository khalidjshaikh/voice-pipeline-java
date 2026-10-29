package com.voicepipeline.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates streamed tokens and yields complete sentences at punctuation/newline
 * or when max length is reached.
 */
public class SentenceSplitter {
    private final StringBuilder buf = new StringBuilder();
    private final int maxLen;

    public SentenceSplitter(int maxLen) { this.maxLen = maxLen; }

    /** Append token; return a list of completed sentences (possibly empty). */
    public List<String> append(String token) {
        buf.append(token);
        List<String> out = new ArrayList<>();
        while (true) {
            int idx = findBoundary();
            if (idx < 0) break;
            String sentence = buf.substring(0, idx + 1).trim();
            buf.delete(0, idx + 1);
            if (!sentence.isEmpty()) out.add(sentence);
        }
        // force flush if too long
        if (buf.length() >= maxLen) {
            int cut = buf.length();
            String sentence = buf.substring(0, cut).trim();
            buf.setLength(0);
            if (!sentence.isEmpty()) out.add(sentence);
        }
        return out;
    }

    /** Flush remaining buffer. */
    public String flush() {
        String s = buf.toString().trim();
        buf.setLength(0);
        return s;
    }

    private int findBoundary() {
        for (int i = 0; i < buf.length(); i++) {
            char c = buf.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // avoid splitting on abbreviations? keep simple
                return i;
            }
        }
        return -1;
    }
}
