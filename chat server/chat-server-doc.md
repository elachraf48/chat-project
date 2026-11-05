# JavaChat Server — Full Technical Documentation

## Overview

The JavaChat Server is a Java 21 TCP socket server that acts as the central hub for all connected clients. It handles authentication, real-time chat broadcasting, file and audio relay, and AI assistant requests. All communication uses newline-delimited JSON over a persistent TCP connection.

**Build status:** `BUILD SUCCESS — 14 source files, 0 errors`
**Deployable artifact:** `target/chat-server.jar` (fat JAR, ~2.2 MB)

---

## Project Structure

```
chat server/
├── pom.xml                          Maven build file
├── javachat.service                 systemd unit for Linux VPS deployment
├── vps-deploy.md                    Step-by-step VPS deployment guide
│
└── src/main/
    ├── java/com/javachat/server/
    │   ├── Main.java                Entry point
    │   ├── config/
    │   │   └── ServerConfig.java    Reads application.properties
    │   ├── network/
    │   │   ├── ChatServer.java      TCP accept loop + ExecutorService
    │   │   ├── ClientHandler.java   Per-client thread — message router
    │   │   └── ClientManager.java   Thread-safe username registry + broadcast
    │   ├── protocol/
    │   │   ├── Message.java         JSON DTO for all message types
    │   │   ├── MessageType.java     Enum of all protocol message types
    │   │   └── ProtocolManager.java Jackson serialize / deserialize
    │   ├── auth/
    │   │   └── UserService.java     Username claim and release
    │   ├── chat/
    │   │   └── GlobalChatRoom.java  Broadcasts CHAT messages to all clients
    │   ├── transfer/
    │   │   ├── FileTransferManager.java   Validates and relays file chunks
    │   │   └── AudioTransferManager.java  Validates and relays audio chunks
    │   ├── ai/
    │   │   └── GeminiService.java   Calls Gemini REST API, returns response
    │   └── util/
    │       └── Logger.java          java.util.logging wrapper
    │
    └── resources/
        ├── application.properties   Server port configuration
        └── logging.properties       Log format and level
```

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `jackson-databind` | 2.17.2 | JSON serialization / deserialization |
| `junit-jupiter` | 5.11.0 | Unit testing (test scope) |
| Java `HttpClient` | JDK built-in | Gemini API HTTP calls |
| `java.util.logging` | JDK built-in | Logging |

Build plugins: `maven-shade-plugin 3.6.0` (fat JAR), `exec-maven-plugin 3.3.0` (run locally), `maven-surefire-plugin 3.3.1` (JUnit 5).

---

## How to Build and Run

### Build the fat JAR

```bash
mvn package -f "chat server/pom.xml"
# Output: chat server/target/chat-server.jar
```

### Run locally

```bash
java -jar "chat server/target/chat-server.jar"
```

Or with Maven:

```bash
mvn exec:java -f "chat server/pom.xml"
```

### Configure the port

Edit `src/main/resources/application.properties`:

```properties
server.port=12345
```

Default is `12345` if the file is missing or the key is absent.

---

## Class Reference

### `Main`

Entry point. Creates `ServerConfig` and `ClientManager`, builds `ChatServer`, registers a JVM shutdown hook (`chatServer.stop()`), then calls `chatServer.start()` which blocks until the server is stopped.

---

### `ServerConfig`

Loads `application.properties` from the classpath at construction time. Exposes `getPort()`. Falls back to port `12345` on any error.

---

### `ChatServer`

Opens a `ServerSocket` on the configured port. Runs a `while (!serverSocket.isClosed())` accept loop on the main thread. Each accepted `Socket` is submitted to a `Executors.newCachedThreadPool()` as a new `ClientHandler`. Constructs all service singletons (`UserService`, `GlobalChatRoom`, `FileTransferManager`, `AudioTransferManager`, `GeminiService`) at startup and passes them to each handler.

`stop()` calls `executor.shutdown()` then closes the `ServerSocket`, causing the accept loop to exit.

---

### `ClientManager`

Thread-safe registry of all **joined** clients, keyed by username (`ConcurrentHashMap<String, ClientHandler>`).

| Method | Description |
|---|---|
| `register(username, handler)` | Atomically claims the username via `putIfAbsent`. Returns `false` if taken. |
| `deregister(username)` | Removes the username on disconnect. |
| `isUsernameTaken(username)` | Non-atomic check (for informational use only). |
| `getAll()` | Unmodifiable view of current handlers. |
| `count()` | Current number of joined clients. |
| `broadcast(message)` | Serializes and sends to every joined client. |
| `broadcastExcept(username, message)` | Same, skipping the named client. |

---

### `ClientHandler`

Implements `Runnable`. One instance per connected client, running on its own thread from the pool.

**Lifecycle:**

1. Opens `BufferedReader` and `PrintWriter` on the socket streams (UTF-8).
2. Reads lines in a loop until `null` (clean disconnect) or `IOException`.
3. Each line is checked against the 1 MB size limit, then dispatched via `handleLine()`.
4. On exit: calls `UserService.leave()`, broadcasts `USER_LEFT`, closes socket.

**Message routing (post-JOIN):**

| Received type | Handler method | Action |
|---|---|---|
| `CHAT` | `handleChat` | Validates text, calls `GlobalChatRoom.broadcast` |
| `FILE_META` | `handleFileMeta` | Delegates to `FileTransferManager` |
| `FILE_CHUNK` | `handleFileChunk` | Delegates to `FileTransferManager` |
| `FILE_COMPLETE` | `handleFileComplete` | Delegates to `FileTransferManager` |
| `AUDIO_META` | `handleAudioMeta` | Delegates to `AudioTransferManager` |
| `AUDIO_CHUNK` | `handleAudioChunk` | Delegates to `AudioTransferManager` |
| `AI_REQUEST` | `handleAiRequest` | Validates prompt, calls `GeminiService.generate`, replies with `AI_RESPONSE` |

**Security limits (Phase 10):**

| Limit | Value |
|---|---|
| Max raw line length | 1 MB (connection dropped if exceeded) |
| Max username length | 32 characters |
| Username character set | `[A-Za-z0-9_-]` only |
| Max chat text length | 2 000 characters |
| Max AI prompt length | 2 000 characters |

---

### `UserService`

Thin service layer over `ClientManager`. `tryJoin(username, handler)` returns `true` if the username was successfully claimed. `leave(username)` releases it on disconnect.

---

### `GlobalChatRoom`

`broadcast(senderUsername, text)` creates a `CHAT` message (with UUID `messageId` and ISO-8601 `timestamp`) and calls `ClientManager.broadcast`, delivering it to **all** joined clients including the sender.

---

### `FileTransferManager`

Validates and immediately relays file transfer messages to all clients except the sender. **The server does not store file data.**

| Message | Validation |
|---|---|
| `FILE_META` | `fileId` non-blank, `fileName` non-blank, `fileSize` 0 < size ≤ 50 MB, `totalChunks` > 0 |
| `FILE_CHUNK` | `fileId` non-blank, `chunkIndex` ≥ 0, `data` non-blank |
| `FILE_COMPLETE` | `fileId` non-blank |

Size limit: **50 MB** per file.

---

### `AudioTransferManager`

Same relay pattern as `FileTransferManager`.

| Message | Additional validation |
|---|---|
| `AUDIO_META` | File extension must be one of: `.ogg`, `.mp3`, `.wav`, `.m4a`, `.aac`, `.opus` |
| `AUDIO_CHUNK` | Same as `FILE_CHUNK` |

Size limit: **10 MB** per audio message.

---

### `GeminiService`

Sends a prompt to the Gemini `gemini-2.0-flash` model via HTTPS REST (`generateContent` endpoint). Returns the generated text string, or a descriptive error message if the call fails.

**API key resolution order:**
1. Environment variable `GEMINI_API_KEY`
2. JVM system property `gemini.api.key` (`-Dgemini.api.key=...`)

The key is **never** received from clients or stored in source code.

Timeouts: connect 10 s, request 30 s. Errors (network failure, non-200 HTTP, malformed response) are caught and returned as user-readable strings — the server never crashes on Gemini failures.

---

### `ProtocolManager`

Static utility. Uses a single shared `ObjectMapper` (Jackson) configured with `FAIL_ON_UNKNOWN_PROPERTIES=false` (forward compatibility).

- `serialize(Message)` → single-line JSON string, no trailing newline.
- `deserialize(String)` → `Message`. Throws `IOException` on malformed input.

---

### `Message`

Jackson-annotated DTO. `@JsonInclude(NON_NULL)` — null fields are omitted from the wire format.

**Fields:**

| Field | Type | Used by |
|---|---|---|
| `type` | `MessageType` | All messages |
| `messageId` | `String` (UUID) | `CHAT` |
| `sender` | `String` | `JOIN`, `CHAT`, `USER_JOINED`, `USER_LEFT`, transfer messages |
| `text` | `String` | `CHAT`, `JOIN_ERROR`, `ERROR`, `AI_RESPONSE` |
| `timestamp` | `String` (ISO-8601) | Most server-originated messages |
| `fileId` | `String` | `FILE_*`, `AUDIO_*` |
| `fileName` | `String` | `FILE_META`, `AUDIO_META` |
| `fileSize` | `Long` | `FILE_META`, `AUDIO_META` |
| `totalChunks` | `Integer` | `FILE_META`, `AUDIO_META` |
| `chunkIndex` | `Integer` | `FILE_CHUNK`, `AUDIO_CHUNK` |
| `data` | `String` (Base64) | `FILE_CHUNK`, `AUDIO_CHUNK` |
| `prompt` | `String` | `AI_REQUEST` |

---

### `Logger`

Wraps `java.util.logging`. On class load, reads `logging.properties` from the classpath to configure format and level without JVM flags.

Methods: `info`, `error`, `warn`, `debug`.

Log name: `com.javachat.server`.

---

## Communication Protocol

### Transport

TCP socket. Each message is one JSON object on a single line, terminated by `\n`. The server reads with `BufferedReader.readLine()` and writes with `PrintWriter.println()`.

### Message types

| Type | Direction | Description |
|---|---|---|
| `JOIN` | Client → Server | Claim a username. Field: `sender` = desired username. |
| `JOIN_SUCCESS` | Server → Client | Username accepted. Field: `sender` = confirmed username. |
| `JOIN_ERROR` | Server → Client | Username rejected. Field: `text` = reason. Client may retry with a different username. |
| `CHAT` | Client → Server | Send a text message. Field: `text`. |
| `CHAT` | Server → all | Broadcast. Fields: `messageId`, `sender`, `text`, `timestamp`. |
| `USER_JOINED` | Server → all others | Presence notification. Field: `sender`. |
| `USER_LEFT` | Server → all others | Presence notification. Field: `sender`. |
| `ERROR` | Server → Client | Protocol or validation error. Field: `text` = description. |
| `FILE_META` | Client → Server | Start a file transfer. Fields: `fileId`, `fileName`, `fileSize`, `totalChunks`. |
| `FILE_META` | Server → all others | Relayed with `sender` added. |
| `FILE_CHUNK` | Client → Server | One chunk of a file. Fields: `fileId`, `chunkIndex`, `data` (Base64). |
| `FILE_CHUNK` | Server → all others | Relayed immediately. |
| `FILE_COMPLETE` | Client → Server | Signal end of transfer. Field: `fileId`. |
| `FILE_COMPLETE` | Server → all others | Relayed. |
| `AUDIO_META` | Client → Server | Start an audio transfer. Same fields as `FILE_META`. |
| `AUDIO_CHUNK` | Client → Server | One chunk of audio. Same fields as `FILE_CHUNK`. |
| `AI_REQUEST` | Client → Server | Ask the AI assistant. Field: `prompt`. |
| `AI_RESPONSE` | Server → Client | AI answer (only to the requester). Field: `text`. |

### Session flow

```
Client                                Server
  │                                     │
  │── {"type":"JOIN","sender":"alice"} ──▶
  │                                     │  validates username
  │◀── {"type":"JOIN_SUCCESS","sender":"alice","timestamp":"..."} ──
  │                                     │
  │                    ◀── {"type":"USER_JOINED","sender":"alice","timestamp":"..."} ── (broadcast to others)
  │                                     │
  │── {"type":"CHAT","text":"Hello!"}  ──▶
  │                                     │  stamps UUID + timestamp
  │◀── {"type":"CHAT","messageId":"...","sender":"alice","text":"Hello!","timestamp":"..."} ──
  │                    ◀── (same message broadcast to all others)
  │                                     │
  │   [disconnects]                     │
  │                    ◀── {"type":"USER_LEFT","sender":"alice","timestamp":"..."} ── (broadcast to others)
```

### File transfer flow

```
Sender                         Server                        Receivers
  │                              │                              │
  │── FILE_META (fileId, name) ──▶                              │
  │                              │── FILE_META (relayed) ──────▶│
  │── FILE_CHUNK (idx=0, data) ──▶                              │
  │                              │── FILE_CHUNK (idx=0) ───────▶│
  │── FILE_CHUNK (idx=1, data) ──▶                              │
  │                              │── FILE_CHUNK (idx=1) ───────▶│
  │── FILE_COMPLETE (fileId) ───▶                              │
  │                              │── FILE_COMPLETE (relayed) ──▶│
```

Audio uses the identical flow with `AUDIO_META` / `AUDIO_CHUNK`.

---

## Configuration Reference

### `application.properties`

| Key | Default | Description |
|---|---|---|
| `server.port` | `12345` | TCP port the server binds to |

### `logging.properties`

Controls `java.util.logging` format and level. Loaded automatically from classpath at startup. Can be overridden externally on the VPS by placing a different file at a path passed via `-Djava.util.logging.config.file=...`.

### Environment variables

| Variable | Description |
|---|---|
| `GEMINI_API_KEY` | Gemini API key. If absent, AI requests return an "unavailable" message. |

### JVM system properties

| Property | Description |
|---|---|
| `gemini.api.key` | Alternative to `GEMINI_API_KEY` env var (lower priority). |

---

## VPS Deployment Summary

See [`vps-deploy.md`](vps-deploy.md) for the full step-by-step guide.

Quick reference:

```bash
# Build
mvn package -f "chat server/pom.xml"

# Upload
scp "chat server/target/chat-server.jar" user@VPS:/opt/javachat/

# First-time setup on VPS
sudo adduser --system --no-create-home --group javachat
sudo cp /tmp/javachat.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now javachat

# Update
scp "chat server/target/chat-server.jar" user@VPS:/opt/javachat/
ssh user@VPS "sudo systemctl restart javachat"

# Logs
ssh user@VPS "sudo journalctl -u javachat -f"
```
