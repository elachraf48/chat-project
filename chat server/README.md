# JavaChat — Chat Server

The central TCP server for the JavaChat application. Accepts connections from desktop and Android clients on port 12345, manages usernames, broadcasts chat messages, relays file and audio transfers, and forwards Gemini AI requests.

---

## Quick Start

### Build

```bash
mvn package -f "chat server/pom.xml"
# Output: chat server/target/chat-server.jar  (~2.2 MB fat JAR)
```

### Run locally

```bash
java -jar "chat server/target/chat-server.jar"
```

On startup the server prints its local IP addresses and listens for connections:

```
[INFO] ==========================================
[INFO]          JavaChat Server Starting
[INFO] ==========================================
[INFO] Port: 12345
[INFO] Connect your clients to one of these addresses:
[INFO]   [eth0]  192.168.1.100:12345
[INFO]   Localhost (same machine): 127.0.0.1:12345
[INFO] Server ready — waiting for clients...
```

### Run with Gemini AI enabled

```bash
export GEMINI_API_KEY=your_key_here
java -jar "chat server/target/chat-server.jar"
```

> ⚠️ **Never** put the API key in `application.properties`. Use the `GEMINI_API_KEY` environment variable or the JVM property `-Dgemini.api.key=…` instead.

---

## Configuration

**`src/main/resources/application.properties`:**

```properties
server.port=12345
```

The port resolution priority order:
1. Environment variable `SERVER_PORT` ← useful for containerized / Docker deployments
2. `application.properties` → `server.port`
3. Default: `12345`

The Gemini API key is resolved in this priority order:
1. Environment variable `GEMINI_API_KEY` ← **recommended for all deployments**
2. JVM system property `-Dgemini.api.key=…`
3. `application.properties` → `server.gemini.api-key` ← ⚠️ do not use in production

---

## Architecture

```
Main
 └─ ChatServer                    Opens ServerSocket on port 12345
     └─ ThreadPoolExecutor        Bounded pool — core 10, max 500 threads; 5-min read timeout per client
         └─ ClientHandler (×N)    Per-client read loop; routes all messages
             ├─ ClientManager     ConcurrentHashMap<username → ClientHandler>
             ├─ UserService       Username claim / release
             ├─ GlobalChatRoom    Broadcasts CHAT to all except sender
             ├─ FileTransferManager   Validates + relays FILE_* messages
             ├─ AudioTransferManager  Validates + relays AUDIO_* messages
             └─ GeminiService     HTTP call to Gemini API; returns to requester only
```

---

## Package Structure

```
com.javachat.server
├── Main                        Entry point; discovers local network addresses
├── config/
│   └── ServerConfig            Reads application.properties (port)
├── network/
│   ├── ChatServer              ServerSocket accept loop
│   ├── ClientHandler           Per-client thread; message routing; security enforcement
│   └── ClientManager           Thread-safe client registry; broadcast helpers
├── auth/
│   └── UserService             Username claim / release (delegates to ClientManager)
├── chat/
│   └── GlobalChatRoom          Stamps UUID + timestamp; calls broadcastExcept(sender)
├── transfer/
│   ├── FileTransferManager     FILE_META/CHUNK/COMPLETE relay; 50 MB limit
│   └── AudioTransferManager    AUDIO_META/CHUNK/COMPLETE relay; 10 MB limit; format allowlist
├── ai/
│   └── GeminiService           Gemini REST client; model fallback; retry on 503
├── protocol/
│   ├── Message                 Jackson DTO; @JsonInclude(NON_NULL); static factory methods
│   ├── MessageType             Enum — 16 message types
│   └── ProtocolManager         Jackson serialize / deserialize helpers
└── util/
    └── Logger                  java.util.logging wrapper
```

---

## Message Protocol

All messages are newline-delimited JSON over a persistent TCP connection.

### Supported message types

| Type | Direction | Key fields |
|---|---|---|
| `JOIN` | Client → Server | `sender` |
| `JOIN_SUCCESS` | Server → Client | `sender`, `timestamp` |
| `JOIN_ERROR` | Server → Client | `text` |
| `CHAT` | Client → Server | `text` |
| `CHAT` | Server → all others | `messageId`, `sender`, `text`, `timestamp` |
| `USER_JOINED` | Server → all others | `sender` |
| `USER_LEFT` | Server → all others | `sender` |
| `ERROR` | Server → Client | `text` |
| `FILE_META` | Client → Server → others | `fileId`, `fileName`, `fileSize`, `totalChunks`, `sender` |
| `FILE_CHUNK` | Client → Server → others | `fileId`, `chunkIndex`, `data` (Base64) |
| `FILE_COMPLETE` | Client → Server → others | `fileId`, `sender` |
| `AUDIO_META` | Client → Server → others | `fileId`, `fileName`, `fileSize`, `totalChunks`, `sender` |
| `AUDIO_CHUNK` | Client → Server → others | `fileId`, `chunkIndex`, `data` (Base64) |
| `AUDIO_COMPLETE` | Client → Server → others | `fileId`, `sender` |
| `AI_REQUEST` | Client → Server | `prompt` |
| `AI_RESPONSE` | Server → Client (requester only) | `text` |

### Validation limits enforced by `ClientHandler`

| Rule | Limit |
|---|---|
| Max raw line length | 1 MB — connection dropped if exceeded |
| Max username length | 32 characters, `[A-Za-z0-9_-]` only |
| Max chat text | 2 000 characters |
| Max file size | 50 MB |
| Max audio size | 10 MB |
| Allowed audio extensions | `.ogg`, `.mp3`, `.wav`, `.m4a`, `.aac`, `.opus` |
| Max AI prompt length | 2 000 characters |

---

## Key Classes

### `ClientHandler`

The core of the server. One instance runs per connected client on its own thread. Responsibilities:
- Reads newline-delimited JSON from the socket's `BufferedReader`.
- Enforces the 1 MB line limit (drops connection if exceeded).
- Validates and routes each message type to the appropriate handler.
- Calls `UserService.leave()` and broadcasts `USER_LEFT` on disconnect or error.

### `ClientManager`

Thread-safe registry of all connected clients.

```java
// Atomic username claim
ClientManager.tryRegister(username, handler)  // putIfAbsent

// Broadcast helpers
broadcast(message)              // sends to all clients
broadcastExcept(message, skip)  // sends to all except one username
send(username, message)         // sends to one specific client
```

### `GeminiService`

Calls the Gemini REST API synchronously on the `ClientHandler` thread (each client has its own thread so this does not block others).

- **Model fallback chain:** `gemini-2.0-flash` → `gemini-1.5-flash` → `gemini-1.5-pro`
- **Timeouts:** 10 s connect, 30 s request
- **Retry:** up to 2 attempts per model on 503 / timeout
- **Non-retriable errors** (401, 404, 400) fail immediately with a user-readable string

### `FileTransferManager` / `AudioTransferManager`

Relay-only — the server **never stores** file or audio data on disk. Each incoming chunk is immediately forwarded to all other connected clients. Validation happens only on the `*_META` message (size, extension).

---

## Threading Model

| Thread | Role |
|---|---|
| Main | `ServerSocket.accept()` loop |
| `ClientHandler` (×N) | One per connected client (max 500); reads JSON, calls handlers; 5-min socket timeout |
| JVM shutdown hook | Calls `ExecutorService.shutdown()` + `ServerSocket.close()` |

All shared state goes through `ConcurrentHashMap` in `ClientManager`. `PrintWriter.println()` is synchronized by the JVM, making per-client writes thread-safe.

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `jackson-databind` | 2.17.2 | JSON serialization / deserialization |
| `junit-jupiter` | 5.11.0 | Unit testing (test scope) |
| Java `HttpClient` | JDK 21 built-in | Gemini API HTTP requests |
| `java.util.logging` | JDK built-in | Logging |

**Build plugins:**
- `maven-shade-plugin` 3.6.0 — fat JAR
- `exec-maven-plugin` 3.3.0 — `mvn exec:java` local run
- `maven-surefire-plugin` 3.3.1 — JUnit 5

---

## VPS Deployment

See [`vps-deploy.md`](vps-deploy.md) for the full step-by-step guide.

The included [`javachat.service`](javachat.service) systemd unit:
- Runs as unprivileged user `javachat`.
- Reads the Gemini API key from `/etc/javachat/env` (never in source control).
- Restarts automatically on failure.
- Applies security hardening: `NoNewPrivileges`, `PrivateTmp`, `ProtectSystem=strict`.

```bash
# Build, copy, restart
mvn package -f "chat server/pom.xml"
scp "chat server/target/chat-server.jar" user@VPS:/opt/javachat/
ssh user@VPS "sudo systemctl restart javachat"
```

---

## Known Issues

| Severity | Issue | Notes |
|---|---|---|
| 🟢 Low | No cumulative chunk-size validation | Sender can declare `fileSize=1MB` in META then stream more; server relays all chunks |
| 🟢 Low | No in-flight transfer cleanup on disconnect | Partial transfers stay in memory if sender crashes without `FILE_COMPLETE` |
| 🟢 Low | Audio double-extension bypass | `audio.exe.m4a` passes the extension allowlist (server never executes files, risk is low) |
| 🟢 Low | No heartbeat / ping | Zombie connections (client crash without FIN) cleaned up after 5-min socket timeout |

---

## Full Technical Documentation

See [`chat-server-doc.md`](chat-server-doc.md) for detailed documentation of every class, every method, every configuration option, and the complete session flow.
