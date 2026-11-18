package com.javachat.mobile.protocol;

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
    /** Sent once to a newcomer with the full list of already-connected users. */
    USER_LIST,

    // General error
    ERROR,

    // File transfer
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
