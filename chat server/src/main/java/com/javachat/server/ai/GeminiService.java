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


public class GeminiService {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";

    /** Maximum length of a prompt accepted from clients (Phase 10 hardening). */
    public static final int MAX_PROMPT_LENGTH = 2000;

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

  
    public GeminiService() {
        this(resolveApiKey());
    }

    /** Package-private constructor for testing with an injected key. */
    GeminiService(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public String generate(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            Logger.error("[GEMINI] API key not configured — set GEMINI_API_KEY environment variable.");
            return "AI assistant is not available: API key not configured on the server.";
        }

        String requestBody = buildRequestBody(prompt);
        String url = BASE_URL + "?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                Logger.error("[GEMINI] HTTP " + response.statusCode() + ": " + response.body());
                return "AI assistant returned an error (HTTP " + response.statusCode() + ").";
            }

            return extractText(response.body());

        } catch (IOException e) {
            Logger.error("[GEMINI] Request failed: " + e.getMessage());
            return "AI assistant is currently unavailable. Please try again later.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.error("[GEMINI] Request interrupted.");
            return "AI request was interrupted.";
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildRequestBody(String prompt) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", prompt);
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            // Should never happen with simple string payloads
            return "{\"contents\":[{\"parts\":[{\"text\":\"" + prompt.replace("\"", "\\\"") + "\"}]}]}";
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
}
