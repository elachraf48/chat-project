package com.javachat.server.protocol;

public enum MessageType {

    // Join flow
    JOIN,
    JOIN_SUCCESS,
    JOIN_ERROR,

    // Chat
    CHAT,

    // Presence
    USER_JOINED,
    USER_LEFT,

    // General error
    ERROR,

    // Future: file transfer
    FILE_META,
    FILE_CHUNK,
    FILE_COMPLETE,

    // Audio messages
    AUDIO_META,
    AUDIO_CHUNK,
    AUDIO_COMPLETE,

    // AI assistant
    AI_REQUEST,
    AI_RESPONSE
}
