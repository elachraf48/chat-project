
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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class GeminiService {

    private static final String[] DEFAULT_MODEL_FALLBACK = {
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
    };

    private static final String API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    public static final int MAX_PROMPT_LENGTH = 2000;

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 1500;
    private static final long RATE_LIMIT_MS = 5000;

    private final String apiKey;
    private final String[] modelFallback;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    // Tracks the last AI request time for each username.
    private final ConcurrentHashMap<String, Long> lastRequestTime =
            new ConcurrentHashMap<>();

    public GeminiService() {
        this(resolveApiKey(), resolveModels());

        if (this.apiKey == null || this.apiKey.isBlank()) {
            Logger.warn("[GEMINI] No API key found.");
        } else {
            Logger.info("[GEMINI] API key loaded.");
        }
    }

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

    /**
     * Generate an AI response for a specific user.
     */
    public String generate(String username, String prompt) {

        if (apiKey == null || apiKey.isBlank()) {
            Logger.error("[GEMINI] API key not configured.");
            return "AI assistant is not available: API key not configured on the server.";
        }

        if (prompt == null || prompt.isBlank()) {
            return "Please enter a valid prompt.";
        }

        if (prompt.length() > MAX_PROMPT_LENGTH) {
            return "Your prompt is too long. Maximum length is "
                    + MAX_PROMPT_LENGTH + " characters.";
        }

        // Rate limiting per username.
        if (username != null && !username.isBlank()) {

            long now = System.currentTimeMillis();

            lastRequestTime.entrySet().removeIf(
                    entry -> now - entry.getValue() > RATE_LIMIT_MS * 2
            );

            long[] waitMs = {-1};

            lastRequestTime.compute(username, (key, last) -> {

                if (last != null && now - last < RATE_LIMIT_MS) {
                    waitMs[0] = RATE_LIMIT_MS - (now - last);
                    return last;
                }

                return now;
            });

            if (waitMs[0] > 0) {
                long waitSeconds = (waitMs[0] + 999) / 1000;

                return "Please wait " + waitSeconds
                        + " second(s) before sending another AI request.";
            }
        }

        String requestBody = buildRequestBody(prompt);

        // Try available models.
        for (String model : modelFallback) {

            String url = API_BASE + model + ":generateContent";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

                try {

                    HttpResponse<String> response =
                            httpClient.send(
                                    request,
                                    HttpResponse.BodyHandlers.ofString()
                            );

                    if (response.statusCode() == 200) {
                        return extractText(response.body());
                    }

                    if (response.statusCode() == 503
                            || response.statusCode() == 429) {

                        Logger.warn("[GEMINI] HTTP "
                                + response.statusCode()
                                + " on " + model
                                + ", attempt " + attempt);

                        if (attempt < MAX_RETRIES) {
                            Thread.sleep(
                                    RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1)
                            );
                        }

                        continue;
                    }

                    Logger.error("[GEMINI] HTTP "
                            + response.statusCode()
                            + ": " + response.body());

                    return "AI assistant returned an error (HTTP "
                            + response.statusCode() + ").";

                } catch (IOException e) {

                    Logger.warn("[GEMINI] Request failed: " + e.getMessage());

                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(
                                    RETRY_DELAY_MS * (long) Math.pow(2, attempt - 1)
                            );
                        } catch (InterruptedException interrupted) {
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
     * Backwards-compatible method without username.
     */
    public String generate(String prompt) {
        return generate(null, prompt);
    }

    /**
     * Build the JSON request body.
     */
    private String buildRequestBody(String prompt) {

        ObjectNode root = mapper.createObjectNode();

        ArrayNode contents = root.putArray("contents");

        ObjectNode content = contents.addObject();

        ArrayNode parts = content.putArray("parts");

        parts.addObject().put("text", prompt);

        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            Logger.error("[GEMINI] JSON serialization failed: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * Extract generated text from Gemini's JSON response.
     */
    private String extractText(String responseBody) {

        try {

            JsonNode root = mapper.readTree(responseBody);

            JsonNode text = root
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            if (!text.isMissingNode() && !text.isNull()) {
                return text.asText();
            }

            Logger.error("[GEMINI] Unexpected response structure.");

            return "AI assistant returned an unrecognized response.";

        } catch (IOException e) {

            Logger.error("[GEMINI] Failed to parse response: "
                    + e.getMessage());

            return "AI assistant returned an unreadable response.";
        }
    }

    /**
     * Resolve the API key from environment or JVM property.
     */
    private static String resolveApiKey() {

        String fromEnv = System.getenv("GEMINI_API_KEY");

        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        String fromProp = System.getProperty("gemini.api.key");

        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }

        return null;
    }

    /**
     * Load model names from application.properties.
     */
    private static String[] resolveModels() {

        try (java.io.InputStream input =
                     GeminiService.class.getClassLoader()
                             .getResourceAsStream("application.properties")) {

            if (input != null) {

                Properties properties = new Properties();

                properties.load(input);

                String models = properties.getProperty("server.gemini.models");

                if (models != null && !models.isBlank()) {

                    String[] parts = models.split(",");

                    List<String> modelsList = new ArrayList<>();

                    for (String part : parts) {

                        String trimmed = part.trim();

                        if (!trimmed.isEmpty()) {
                            modelsList.add(trimmed);
                        }
                    }

                    if (!modelsList.isEmpty()) {
                        return modelsList.toArray(new String[0]);
                    }
                }
            }

        } catch (IOException e) {

            Logger.warn("[GEMINI] Could not load model configuration: "
                    + e.getMessage());
        }

        return DEFAULT_MODEL_FALLBACK;
    }
}
