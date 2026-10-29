package com.voicepipeline.util;

public final class WavUtil {
    private WavUtil() {}

    /** Wrap raw PCM (16-bit LE, mono) into a WAV byte array. */
    public static byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataLen = pcm.length;
        int totalLen = 36 + dataLen;
        byte[] out = new byte[44 + dataLen];
        // RIFF header
        putAscii(out, 0, "RIFF");
        putIntLE(out, 4, totalLen);
        putAscii(out, 8, "WAVE");
        putAscii(out, 12, "fmt ");
        putIntLE(out, 16, 16);
        putShortLE(out, 20, (short) 1); // PCM
        putShortLE(out, 22, (short) channels);
        putIntLE(out, 24, sampleRate);
        putIntLE(out, 28, byteRate);
        putShortLE(out, 32, (short) blockAlign);
        putShortLE(out, 34, (short) bitsPerSample);
        putAscii(out, 36, "data");
        putIntLE(out, 40, dataLen);
        System.arraycopy(pcm, 0, out, 44, dataLen);
        return out;
    }

    private static void putAscii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }
    private static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v); b[off+1] = (byte)(v >> 8); b[off+2] = (byte)(v >> 16); b[off+3] = (byte)(v >> 24);
    }
    private static void putShortLE(byte[] b, int off, short v) {
        b[off] = (byte) (v); b[off+1] = (byte)(v >> 8);
    }
}
