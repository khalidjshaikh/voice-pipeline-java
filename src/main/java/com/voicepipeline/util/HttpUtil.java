package com.voicepipeline.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public final class HttpUtil {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private HttpUtil() {}

    public static HttpClient client() { return CLIENT; }

    public static HttpResponse<String> postJson(String url, Map<String, String> headers, String body, int maxRetries) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json");
                headers.forEach(b::header);
                HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 500 || resp.statusCode() == 429) {
                    if (attempt < maxRetries) { sleep(backoff(attempt)); continue; }
                }
                return resp;
            } catch (Exception e) {
                last = e;
                if (attempt < maxRetries) sleep(backoff(attempt));
            }
        }
        throw last == null ? new IOException("request failed") : last;
    }

    public static HttpResponse<byte[]> postBytes(String url, Map<String, String> headers, byte[] body, String contentType, int maxRetries) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", contentType);
                headers.forEach(b::header);
                HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() >= 500 || resp.statusCode() == 429) {
                    if (attempt < maxRetries) { sleep(backoff(attempt)); continue; }
                }
                return resp;
            } catch (Exception e) {
                last = e;
                if (attempt < maxRetries) sleep(backoff(attempt));
            }
        }
        throw last == null ? new IOException("request failed") : last;
    }

    /** Stream SSE; invokes onEvent for each "data:" payload (excluding [DONE]). Returns when stream ends. */
    public static void streamSse(String url, Map<String, String> headers, String body, java.util.function.Consumer<String> onEvent) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream");
        headers.forEach(b::header);
        HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.io.InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() >= 400) {
            String err = new String(resp.body().readAllBytes());
            throw new IOException("HTTP " + resp.statusCode() + ": " + err);
        }
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(resp.body()))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    if (!data.isEmpty()) onEvent.accept(data);
                }
            }
        }
    }

    private static long backoff(int attempt) { return (long) (500 * Math.pow(2, attempt)); }
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
