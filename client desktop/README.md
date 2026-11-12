# JavaChat — Desktop Client

A Java 21 Swing desktop chat client with a WhatsApp-style dark UI. Connects to the JavaChat TCP server, participates in the global chat room, and supports text messaging, file sharing, audio recording, and Gemini AI queries.

---

## Quick Start

### Build

```bash
mvn package -f "client desktop/pom.xml"
# Output: client desktop/target/client-desktop.jar  (fat JAR)
```

### Run

```bash
java -jar "client desktop/target/client-desktop.jar"
```

The login screen appears. Enter a username (letters, digits, `_`, `-`, 1–32 chars) to connect to the server.

### Server address

The client connects to `127.0.0.1:12345` by default. To change it, edit [`ClientConfig.java`](src/main/java/com/javachat/desktop/config/ClientConfig.java) before building:

```java
public static final String SERVER_HOST = "your.server.ip";
public static final int    SERVER_PORT = 12345;
```

---

## Features

| Feature | Description |
|---|---|
| **Text chat** | WhatsApp-style bubbles — own messages green right-aligned, others dark left-aligned with sender name |
| **File sharing** | Send any file up to 50 MB via file picker; receive files with Save dialog |
| **Audio messages** | Record 16 kHz PCM WAV with Java Sound API (up to 10 MB); play received audio in-app |
| **Gemini AI** | Type a prompt in the AI bar at the bottom; response shown in a distinct AI bubble |
| **Online users** | Right sidebar with live user list; colored avatar circles with initials |
| **Reconnect** | Dialog prompt on unexpected disconnection; returns to login for retry |

---

## UI Layout

```
┌──────────────────────────────────────────────────────┐
│ Header  ☰  Global Chat                @username      │
├───────────────────────────────────┬──────────────────┤
│                                   │  Online  3       │
│  [System] alice joined            │  ● Alice         │
│                                   │  ● Bob           │
│  ╔══════════════════╗             │  ● You           │
│  ║ Hello everyone!  ║  12:01      │                  │
│  ╚══════════════════╝  (own, R)   │                  │
│                                   │                  │
│  Bob   12:02                      │                  │
│  ╔══════════════╗                 │                  │
│  ║ Hey!         ║                 │                  │
│  ╚══════════════╝ (other, L)      │                  │
│                                   │                  │
│  ╔══════════════════════════════╗ │                  │
│  ║ ✦ AI: The answer is…        ║ │                  │
│  ╚══════════════════════════════╝ │                  │
├───────────────────────────────────┴──────────────────┤
│  📎  [  Type a message…  ]  🎤  ➤                    │
├──────────────────────────────────────────────────────┤
│  ✦  [  Ask Gemini AI…   ]  ➤                         │
└──────────────────────────────────────────────────────┘
```

All icons (attach, mic, send, sparkle) are drawn with `Graphics2D` primitives — no image files or font dependencies.

---

## Package Structure

```
com.javachat.desktop
├── Main                        Launch LoginFrame on the EDT
├── config/
│   └── ClientConfig            SERVER_HOST · SERVER_PORT constants
├── network/
│   ├── SocketClient            TCP wrapper — send() / receive() JSON lines
│   ├── ConnectionManager       Lifecycle + background reader thread
│   └── MessageListener         Callback interface for incoming messages
├── protocol/
│   ├── Message                 Jackson DTO with @JsonInclude(NON_NULL) + factory methods
│   ├── MessageType             Enum — 16 message types
│   └── ProtocolManager         Jackson serialize / deserialize
├── ui/
│   ├── LoginFrame              Dark login card with Graphics2D background
│   ├── MainFrame               Root window — header, AI bar, content area
│   ├── GlobalChatPanel         Container: MessagePanel + OnlineUsersPanel + MessageInputPanel
│   ├── MessagePanel            Bubble renderer — TextBubble · AttachBubble · SystemBubble · AiBubble
│   ├── OnlineUsersPanel        Right sidebar with colored avatar circles
│   └── MessageInputPanel       Input bar — file button, text field, mic toggle, send button
├── transfer/
│   ├── FileTransferManager     Chunked file send (64 KB Base64 blocks) + save-to-disk on receive
│   └── FileProgressListener    Progress callback interface
├── audio/
│   ├── AudioRecorder           Java Sound API capture — 16 kHz, 16-bit, mono PCM
│   ├── AudioPlayer             Java Sound API playback via SourceDataLine
│   └── AudioManager            Orchestrates record → send and receive → play
├── ai/
│   └── GeminiChatManager       Sends AI_REQUEST via ConnectionManager
└── util/
    ├── ChatTheme               Color + font constants (WhatsApp dark palette)
    └── SwingUtils              runOnEDT() — posts runnables to the Swing Event Dispatch Thread
```

---

## Architecture

### Threading model

| Thread | Role |
|---|---|
| **EDT** | All UI rendering and user interactions |
| **Background reader** | `ConnectionManager` executor — reads JSON lines from socket, calls `MessageListener.onMessage()` |
| **File send thread** | `FileTransferManager` — reads file bytes, sends chunks; never blocks EDT |
| **Audio operations** | `AudioManager` `ExecutorService` — recorder start/stop and playback run on a shared pool |

All calls that update the UI from background threads go through `SwingUtils.runOnEDT()`.

### Connection lifecycle

```
LoginFrame.attemptJoin()       [background thread]
  └─ ConnectionManager.connect()
      └─ SocketClient connects to SERVER_HOST:SERVER_PORT
          └─ Send JOIN message
              └─ Receive JOIN_SUCCESS → create MainFrame, swap MessageListener (atomic)
                 Receive JOIN_ERROR  → re-enable login button, show error
```

---

## File Transfer Protocol

```
Sender                    Server                   Receivers
  │── FILE_META ──────────▶                ──────────▶
  │── FILE_CHUNK ×N ──────▶                ──────────▶
  │── FILE_COMPLETE ───────▶               ──────────▶
```

- **Chunk size:** 64 KB Base64-encoded
- **Max file size:** 50 MB (enforced client-side and server-side)
- **Receive:** chunks accumulated in `ByteArrayOutputStream`; on `FILE_COMPLETE` a Save button appears in the chat bubble

---

## Audio Messaging

- **Record:** 16 kHz, 16-bit, mono PCM WAV via `AudioRecord` (Java Sound API `TargetDataLine`)
- **Send:** same chunked protocol as files (`AUDIO_META` → `AUDIO_CHUNK` × N → `AUDIO_COMPLETE`)
- **Max size:** 10 MB
- **Play:** `SourceDataLine` playback on a background thread

---

## Message Bubble Types

| Type | Appearance | Used for |
|---|---|---|
| `TextBubble` (own) | Green `#005C4B`, right-aligned | Own sent messages |
| `TextBubble` (other) | Dark `#202C33`, left-aligned, blue sender name | Received messages |
| `AttachBubble` | Teal left stripe + icon + label + action button | Files and audio messages |
| `SystemBubble` | Grey pill, centred | USER_JOINED / USER_LEFT events |
| `AiBubble` | 520 px wide, accent left stripe, centred | Gemini AI responses |

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `jackson-databind` | 2.17.2 | JSON serialization / deserialization |
| `junit-jupiter` | 5.11.0 | Unit testing (test scope) |
| Java Sound API | JDK built-in | Audio capture and playback |
| Swing / AWT | JDK built-in | UI framework |

**Build plugins:**
- `maven-shade-plugin` — fat JAR with all dependencies
- `maven-compiler-plugin` — Java 21 source / target

---

## Known Issues & Gaps

| Severity | Issue | Notes |
|---|---|---|
| 🟠 Medium | No file transfer progress bar | `FileProgressListener.onProgress()` fires but no UI element displays it |
| 🟠 Medium | No automatic reconnect retry | Disconnection dialog requires manual click to return to login |
| 🟠 Medium | Server address not runtime-configurable | Requires editing `ClientConfig.java` and rebuilding |
| 🟢 Low | Audio send shows no in-progress indicator | Bubble appears only after all chunks are sent |
| 🟢 Low | No typing indicators | Protocol has no `TYPING` message type |
| 🟢 Low | No read receipts | No ACK messages from server |
| 🟢 Low | `AiBubble.MAX_W` (520 px) wider than `TextBubble.MAX_W` (460 px) | Intentional for readability; may truncate on very narrow windows |

---

## Detailed Documentation

See [`client-desktop.md`](client-desktop.md) for a full phase-by-phase technical breakdown of the implementation.
