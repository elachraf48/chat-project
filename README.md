# JavaChat — Real-Time Global Chat Application

A real-time messaging application built with Java. Desktop and Android users communicate in one shared global chat room via a central Java TCP server.

---

## Project Status

| Component | Technology |
|---|---|
| **Chat Server** | Java 21, TCP Sockets, Jackson  |
| **Desktop Client** | Java 21, Swing |
| **Mobile Client** | Java, Android (API 26–35) ||

---

## Project Structure

```
JavaChat/
│
├── chat server/                  Java TCP Server — fully implemented
│   ├── pom.xml
│   ├── javachat.service          systemd unit for Linux VPS
│   ├── vps-deploy.md             Step-by-step VPS deployment guide
│   ├── chat-server-doc.md        Full technical documentation
│   └── src/main/java/com/javachat/server/
│       ├── Main.java
│       ├── config/               ServerConfig
│       ├── network/              ChatServer · ClientHandler · ClientManager
│       ├── protocol/             Message · MessageType · ProtocolManager
│       ├── auth/                 UserService
│       ├── chat/                 GlobalChatRoom
│       ├── transfer/             FileTransferManager · AudioTransferManager
│       ├── ai/                   GeminiService
│       └── util/                 Logger
│
├── client desktop/               Java Swing Desktop Client — fully implemented
│   ├── pom.xml
│   ├── client-desktop.md         Technical documentation
│   └── src/main/java/com/javachat/desktop/
│       ├── Main.java
│       ├── config/               ClientConfig
│       ├── network/              SocketClient · ConnectionManager · MessageListener
│       ├── protocol/             Message · MessageType · ProtocolManager
│       ├── ui/                   LoginFrame · MainFrame · GlobalChatPanel
│       │                         MessagePanel · OnlineUsersPanel · MessageInputPanel
│       ├── transfer/             FileTransferManager · FileProgressListener
│       ├── audio/                AudioRecorder · AudioPlayer · AudioManager
│       ├── ai/                   GeminiChatManager
│       └── util/                 SwingUtils · ChatTheme
│
├── client-mobile/                Android Client (Java) — fully implemented
└── README.md
```

---

## Architecture

```
                 ┌──────────────────────────┐
                 │       Chat Server        │
                 │                          │
                 │  TCP port 12345          │
                 │  ExecutorService         │
                 │  JSON Protocol           │
                 │  Username claim          │
                 │  Message broadcast       │
                 │  File relay (50 MB max)  │
                 │  Audio relay (10 MB max) │
                 │  Gemini AI integration   │
                 └──────────┬───────────────┘
                            │
                  Global Chat Room
                            │
             ┌──────────────┴──────────────┐
             │                             │
    ┌────────▼────────┐           ┌────────▼────────┐
    │  Desktop Client │           │  Mobile Client  │
    │  Java Swing     │           │  Java Android   │
    └─────────────────┘           └─────────────────┘
```

All users share **one global chat room**. No private messaging. No groups.

---

## Chat Server

### What it does

- Accepts unlimited simultaneous TCP clients (one thread per client via `ExecutorService`).
- Each client claims a unique username with a `JOIN` message — no password required.
- Broadcasts real-time chat messages to all connected users.
- Relays file transfers (up to 50 MB) and audio messages (up to 10 MB) between clients using Base64-encoded chunks — no data stored on disk.
- Forwards AI requests to the Gemini API and returns responses only to the requesting client.
- Enforces input validation: username format, message length, file size, prompt length.
- Shuts down cleanly on `Ctrl+C` via JVM shutdown hook.

### How to build

```bash
mvn package -f "chat server/pom.xml"
# Produces: chat server/target/chat-server.jar  (~2.2 MB fat JAR)
```

### How to run locally

```bash
java -jar "chat server/target/chat-server.jar"
```

### Configuration

`src/main/resources/application.properties`:

```properties
server.port=12345
```

Gemini API key (optional — AI requests return an error if absent):

```bash
export GEMINI_API_KEY=your_key_here
java -jar chat-server.jar
```

---

## Desktop Client

### What it does

- WhatsApp-style dark UI — custom-painted Swing components, no third-party LAF.
- Login screen: enter a username to claim it on the server (no password).
- Main window: chat bubbles panel, online users sidebar with avatar initials, dark input bar.
- Own messages rendered as green bubbles (right-aligned); received messages as dark bubbles (left-aligned) with sender name.
- File sharing: choose any file up to 50 MB via file picker, sent as 64 KB Base64 chunks.
- Audio messages: record with Java Sound API (16 kHz PCM → `.wav`), 10 MB limit, playback in-app.
- Gemini AI: type a `@ai` prompt in the input bar; response appears as a distinct AI bubble.
- Reconnect dialog on unexpected disconnection.
- Never blocks the Swing EDT — all network I/O on background threads.

### How to build

```bash
mvn package -f "client desktop/pom.xml"
# Produces: client desktop/target/client-desktop.jar  (fat JAR)
```

### How to run

```bash
java -jar "client desktop/target/client-desktop.jar"
```

By default connects to `127.0.0.1:12345`. Edit [`ClientConfig.java`](client%20desktop/src/main/java/com/javachat/desktop/config/ClientConfig.java) to change the server address.

---

## Communication Protocol

All messages are single-line JSON objects over a TCP connection, separated by newlines (`\n`).

### Message format

```json
{
  "type": "CHAT",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "sender": "alice",
  "text": "Hello everyone!",
  "timestamp": "2025-01-01T12:00:00Z"
}
```

Null fields are omitted from the wire format (`@JsonInclude(NON_NULL)`).

### Message types

| Type | Direction | Description |
|---|---|---|
| `JOIN` | Client → Server | Claim a username. Field: `sender`. |
| `JOIN_SUCCESS` | Server → Client | Username accepted. |
| `JOIN_ERROR` | Server → Client | Username taken or invalid. Field: `text` = reason. Client may retry. |
| `CHAT` | Client → Server | Send a message. Field: `text`. |
| `CHAT` | Server → all | Broadcast with `messageId`, `sender`, `text`, `timestamp`. |
| `USER_JOINED` | Server → all others | Presence notification. |
| `USER_LEFT` | Server → all others | Presence notification. |
| `ERROR` | Server → Client | Validation or protocol error. |
| `FILE_META` | Client → Server | Start a file transfer. Fields: `fileId`, `fileName`, `fileSize`, `totalChunks`. |
| `FILE_CHUNK` | Client → Server | File data chunk. Fields: `fileId`, `chunkIndex`, `data` (Base64). |
| `FILE_COMPLETE` | Client → Server | End of transfer. Field: `fileId`. |
| `AUDIO_META` | Client → Server | Start an audio transfer. Same fields as `FILE_META`. |
| `AUDIO_CHUNK` | Client → Server | Audio data chunk. Same fields as `FILE_CHUNK`. |
| `AUDIO_COMPLETE` | Client → Server | End of audio transfer. Field: `fileId`. |
| `AI_REQUEST` | Client → Server | Ask the AI assistant. Field: `prompt`. |
| `AI_RESPONSE` | Server → Client | AI answer (only to the requester). Field: `text`. |

File and audio messages are relayed from server to all other clients with the same field structure, adding `sender`.

### Session flow

```
Client                              Server
  │── JOIN {"sender":"alice"} ──────▶
  │◀── JOIN_SUCCESS ────────────────
  │                   ◀── USER_JOINED (broadcast to others)
  │── CHAT {"text":"Hello"} ─────── ▶
  │◀── CHAT (broadcast back) ───────
  │    [disconnect]
  │                   ◀── USER_LEFT (broadcast to others)
```

### Validation limits

| Rule | Limit |
|---|---|
| Max line length | 1 MB (connection dropped if exceeded) |
| Max username length | 32 characters, `[A-Za-z0-9_-]` only |
| Max chat text | 2 000 characters |
| Max file size | 50 MB |
| Max audio size | 10 MB |
| Allowed audio formats | `.ogg`, `.mp3`, `.wav`, `.m4a`, `.aac`, `.opus` |
| Max AI prompt length | 2 000 characters |

---

## Technologies

| Component | Technology | Version |
|---|---|---|
| Server language | Java | 21 |
| Desktop language | Java | 21 |
| Mobile language | Java | Android SDK |
| Transport | TCP Sockets | — |
| Message format | JSON (newline-delimited) | — |
| JSON library | Jackson Databind | 2.17.2 |
| AI integration | Gemini API (gemini-2.0-flash) | v1beta |
| Build (server / desktop) | Maven | 3.x |
| Build (mobile) | Gradle | — |
| Logging | java.util.logging | JDK built-in |
| Deployment | Linux VPS + systemd | — |

---

## Design Decisions

| Decision | Rationale |
|---|---|
| One global room | Simplicity — no room management needed |
| No password authentication | Username uniqueness is enforced in memory; freed on disconnect |
| No database | No persistent state — all session data lives in RAM |
| No private messaging | Out of scope for this project |
| Base64 chunk relay | Keeps the protocol purely JSON; avoids a separate binary channel |
| Server-side Gemini calls | API key never exposed to clients |
| ConcurrentHashMap for clients | Lock-free reads; `putIfAbsent` guarantees atomic username claim |
| One thread per client | Simple and correct; `CachedThreadPool` reuses threads |
| Custom-painted Swing UI | WhatsApp-style look without third-party LAF dependencies |

---

## VPS Deployment

See [`chat server/vps-deploy.md`](chat%20server/vps-deploy.md) for the full guide.

The server ships with:
- A fat JAR (`target/chat-server.jar`) containing all dependencies.
- A `systemd` unit file (`javachat.service`) with security hardening (`NoNewPrivileges`, `PrivateTmp`, `ProtectSystem`), automatic restart on failure, and Gemini key injection via `/etc/javachat/env`.

```bash
# Build and deploy in 3 commands
mvn package -f "chat server/pom.xml"
scp "chat server/target/chat-server.jar" user@VPS:/opt/javachat/
ssh user@VPS "sudo systemctl restart javachat"
```

---

## Documentation

| File | Contents |
|---|---|
| [`README.md`](README.md) | This file — project overview, protocol, technologies |
| [`chat server/chat-server-doc.md`](chat%20server/chat-server-doc.md) | Full technical documentation — every class, method, config option |
| [`chat server/vps-deploy.md`](chat%20server/vps-deploy.md) | Step-by-step VPS deployment guide including TLS setup |
| [`client desktop/client-desktop.md`](client%20desktop/client-desktop.md) | Desktop client — architecture, phases, build instructions |
| [`client-mobile/README.md`](client-mobile/README.md) | Android client — build instructions, architecture, permissions |

---

## Project Goal

Build a cross-platform real-time chat application where desktop and Android users communicate in one global chat room, exchange text messages, files, and audio, and use an integrated AI assistant.

**One server. One global chat room. Multiple platforms. Real-time communication.**
