package com.javachat.server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javachat.server.util.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

<<<<<<< HEAD

=======
/**
 * Phase 9 – Gemini AI Integration
 *
 * Sends a prompt to the Gemini REST API and returns the generated text.
 * The API key is loaded from the environment variable GEMINI_API_KEY or
 * the JVM property -Dgemini.api.key, never from client messages.
 *
 * Endpoint used: generateContent (v1beta), model list configurable via
 * server.gemini.models in application.properties.
 *
 * Security: API key sent in Authorization header (not URL query param).
 *
 * Error handling:
 *   - If the API key is not configured, returns an error message immediately.
 *   - If the HTTP call fails or returns a non-200 status, an error string is returned.
 *   - If the response JSON cannot be parsed, an error string is returned.
 *   - The calling ClientHandler sends the result back only to the requesting client.
 *
 * Rate limiting: one AI request per user per RATE_LIMIT_MS milliseconds.
 */
>>>>>>> 3246e30 (fix all bugs)
public class GeminiService {

    /** Models tried in order when a 503 is received — fastest/newest first.
     *  Override via application.properties: server.gemini.models=model1,model2 */
    private static final String[] DEFAULT_MODEL_FALLBACK = {
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
    };
    private static final String API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    /** Maximum length of a prompt accepted from clients (Phase 10 hardening). */
    public static final int MAX_PROMPT_LENGTH = 2000;

    /** Max retries per model on 503/timeout before trying the next model. */
    private static final int MAX_RETRIES = 2;
    /** Initial back-off delay in ms — doubles each retry (exponential backoff). */
    private static final long RETRY_DELAY_MS = 1_500;

    /** Minimum milliseconds between AI requests for the same user (rate limiting). */
    private static final long RATE_LIMIT_MS = 5_000;

    private final String   apiKey;
    private final String[] modelFallback;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

<<<<<<< HEAD
  
=======
    /** Tracks last AI request timestamp per username for rate limiting. */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastRequestTime =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Create a GeminiService, loading the API key from the environment.
     * Priority: environment variable {@code GEMINI_API_KEY} first,
     * then system property {@code gemini.api.key}.
     */
>>>>>>> 3246e30 (fix all bugs)
    public GeminiService() {
        this(resolveApiKey(), resolveModels());
        if (this.apiKey == null || this.apiKey.isBlank()) {
            Logger.warn("[GEMINI] No API key found — AI requests will fail.");
        } else {
            Logger.info("[GEMINI] API key loaded (length=" + this.apiKey.length() + ").");
        }
    }

    /** Package-private constructor for testing with an injected key. */
    GeminiService(String apiKey) {
        this(apiKey, DEFAULT_MODEL_FALLBACK);
    }

    private GeminiService(String apiKey, String[] modelFallback) {
        this.apiKey = apiKey;
        this.modelFallback = modelFallback;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

<<<<<<< HEAD
    public String generate(String prompt) {
=======
    /**
     * Send a prompt to Gemini and return the AI-generated text.
     *
     * @param username the requesting user (used for rate limiting).
     * @param prompt   the user's question / prompt (already validated by caller).
     * @return the generated text, or an error description if the call fails.
     */
    public String generate(String username, String prompt) {
>>>>>>> 3246e30 (fix all bugs)
        if (apiKey == null || apiKey.isBlank()) {
            Logger.error("[GEMINI] API key not configured — set GEMINI_API_KEY environment variable.");
            return "AI assistant is not available: API key not configured on the server.";
        }

        // Rate limiting: atomic compute prevents two concurrent requests bypassing the check
        if (username != null) {
            long now = System.currentTimeMillis();
            // Sweep stale entries to prevent unbounded map growth (keep entries ≤ 2× rate limit window)
            lastRequestTime.entrySet().removeIf(e -> now - e.getValue() > RATE_LIMIT_MS * 2);
            long[] waitMs = {-1};
            lastRequestTime.compute(username, (k, last) -> {
                if (last != null && (now - last) < RATE_LIMIT_MS) {
                    waitMs[0] = RATE_LIMIT_MS - (now - last);
                    return last; // keep old timestamp, request is rejected
                }
                return now; // update timestamp, request is allowed
            });
            if (waitMs[0] > 0) {
                long waitSec = (waitMs[0] + 999) / 1000;
                return "Please wait " + waitSec + " second(s) before sending another AI request.";
            }
        }

        String requestBody = buildRequestBody(prompt);

        // Try each model in fallback order; retry up to MAX_RETRIES on 503/timeout
        for (String model : modelFallback) {
            // API key passed in Authorization header — never in URL query param
            String url = API_BASE + model + ":generateContent";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    HttpResponse<String> response =
                            httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        if (!model.equals(modelFallback[0]) || attempt > 1) {
                            Logger.info("[GEMINI] Succeeded with model=" + model + " attempt=" + attempt);
                        }
                        return extractText(response.body());
                    }

                    if (response.statusCode() == 503) {
                        Logger.warn("[GEMINI] 503 on " + model + " attempt " + attempt
                                + (attempt < MAX_RETRIES ? " — retrying" : " — trying next model"));
                        if (attempt < MAX_RETRIES) {
                            // Exponential backoff: 1.5s, 3s, 6s …
                            Thread.sleep((long)(RETRY_DELAY_MS * Math.pow(2, attempt - 1)));
                        }
                        continue;
                    }

                    // Non-retriable error (401, 404, 400…) — give up immediately
                    Logger.error("[GEMINI] HTTP " + response.statusCode()
                            + " on " + model + ": " + response.body());
                    return "AI assistant returned an error (HTTP " + response.statusCode() + ").";

                } catch (IOException e) {
                    Logger.warn("[GEMINI] Request failed on " + model + " attempt " + attempt
                            + ": " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        try {
                            // Exponential backoff on network failure too
                            Thread.sleep((long)(RETRY_DELAY_MS * Math.pow(2, attempt - 1)));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return "AI request was interrupted.";
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "AI request was interrupted.";
                }
            }
        }

        Logger.error("[GEMINI] All models exhausted.");
        return "AI assistant is currently unavailable. Please try again later.";
    }

    /**
     * Overload for callers that don't track username (backwards-compat / tests).
     */
    public String generate(String prompt) {
        return generate(null, prompt);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildRequestBody(String prompt) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);
        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            // Should never happen with a simple ObjectNode — fall back to safe serialization
            ObjectNode fallback = mapper.createObjectNode();
            fallback.putArray("contents").addObject()
                    .putArray("parts").addObject()
                    .put("text", "[prompt serialization failed]");
            try { return mapper.writeValueAsString(fallback); }
            catch (IOException ex) { return "{}"; }
        }
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            // Path: candidates[0].content.parts[0].text
            JsonNode text = root
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text");
            if (!text.isMissingNode() && !text.isNull()) {
                return text.asText();
            }
            Logger.error("[GEMINI] Unexpected response structure: " + responseBody);
            return "AI assistant returned an unrecognized response.";
        } catch (IOException e) {
            Logger.error("[GEMINI] Failed to parse response: " + e.getMessage());
            return "AI assistant returned an unreadable response.";
        }
    }

    private static String resolveApiKey() {
        // 1. Environment variable (highest priority — use for production/VPS)
        String fromEnv = System.getenv("GEMINI_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        // 2. JVM system property: -Dgemini.api.key=...
        String fromProp = System.getProperty("gemini.api.key");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;

        // application.properties fallback intentionally removed — never store keys in source files.
        return null;
    }

    /** Load model fallback list from application.properties, or use defaults. */
    private static String[] resolveModels() {
        try (java.io.InputStream is = GeminiService.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String models = props.getProperty("server.gemini.models");
                if (models != null && !models.isBlank()) {
                    String[] parts = models.split(",");
                    java.util.List<String> list = new java.util.ArrayList<>();
                    for (String p : parts) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) list.add(trimmed);
                    }
                    if (!list.isEmpty()) return list.toArray(new String[0]);
                }
            }
        } catch (java.io.IOException e) {
            Logger.warn("[GEMINI] Could not load model list from application.properties: "
                    + e.getMessage() + " — using defaults.");
        }
        return DEFAULT_MODEL_FALLBACK;
    }
}
