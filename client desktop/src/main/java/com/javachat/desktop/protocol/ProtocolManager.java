package com.javachat.desktop.protocol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * JSON serialization / deserialization for the newline-delimited protocol.
 * Must stay in sync with the server's ProtocolManager.
 */
public class ProtocolManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ProtocolManager() {}

    /** Serialize a Message to a single-line JSON string (no trailing newline). */
    public static String serialize(Message message) throws IOException {
        return MAPPER.writeValueAsString(message);
    }

    /** Deserialize a single-line JSON string into a Message. */
    public static Message deserialize(String json) throws IOException {
        return MAPPER.readValue(json, Message.class);
    }
}
