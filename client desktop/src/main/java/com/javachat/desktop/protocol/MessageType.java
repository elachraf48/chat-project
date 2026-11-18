package com.javachat.desktop.protocol;

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
    /** Sent once to a newcomer — full list of already-connected usernames. */
    USER_LIST,

    // General error
    ERROR,

    // Future: file transfer
    FILE_META,
    FILE_CHUNK,
    FILE_COMPLETE,

    // Future: audio messages
    AUDIO_META,
    AUDIO_CHUNK,
    AUDIO_COMPLETE,

    // Future: AI assistant
    AI_REQUEST,
    AI_RESPONSE
}
