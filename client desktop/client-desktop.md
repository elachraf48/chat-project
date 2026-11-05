# JavaChat Desktop Client

A Java Swing desktop client for the JavaChat real-time chat application. Connects to the central JavaChat TCP server and participates in a shared global chat room with other desktop and Android users.

---

## Status

✅ **Fully implemented** — all phases complete, fat JAR builds clean.

---

## Features

- **WhatsApp-style dark UI** — custom-painted Swing components, no third-party look-and-feel.
- **Login screen** — enter any username (1–32 chars, `[A-Za-z0-9_-]`) to claim it on the server. No password.
- **Chat bubbles** — own messages in green (right-aligned), received messages in dark grey (left-aligned) with sender name and timestamp.
- **Online users sidebar** — live list of connected users with coloured avatar circles showing initials.
- **File sharing** — send any file up to 50 MB via file picker; received files are saved via a save dialog. Progress shown during transfer.
- **Audio messages** — record with the Java Sound API (16 kHz PCM, saved as `.wav`), 10 MB limit. Playback in-app via a play/stop button in the chat bubble.
- **Gemini AI assistant** — type `@ai <question>` in the input bar; the response appears as a distinct AI bubble (API key lives on the server only).
- **Reconnect dialog** — prompts the user on unexpected disconnection.
- **EDT-safe** — all network I/O on background threads; all UI updates via `SwingUtilities.invokeLater`.

---

## Project Structure

```
client desktop/
│
├── pom.xml
├── client-desktop.md
│
└── src/main/java/com/javachat/desktop/
    │
    ├── Main.java                      Entry point — system L&F + EDT launch
    │
    ├── config/
    │   └── ClientConfig.java          SERVER_HOST · SERVER_PORT constants
    │
    ├── network/
    │   ├── SocketClient.java          Raw TCP read/write, background reader thread
    │   ├── ConnectionManager.java     Connect/disconnect lifecycle
    │   └── MessageListener.java       Callback interface for incoming messages
    │
    ├── protocol/
    │   ├── Message.java               JSON POJO — all fields (fileId, data, prompt …)
    │   ├── MessageType.java           Enum — JOIN, CHAT, FILE_*, AUDIO_*, AI_*, …
    │   └── ProtocolManager.java       Jackson serialise/deserialise helpers
    │
    ├── ui/
    │   ├── LoginFrame.java            Dark login window — username input + connect button
    │   ├── MainFrame.java             Root window — wires all panels + handles all message types
    │   ├── GlobalChatPanel.java       Scrollable chat area using MessagePanel bubbles
    │   ├── MessagePanel.java          Custom bubble renderer (text / file / audio / AI)
    │   ├── OnlineUsersPanel.java      Sidebar — avatar initials + username list
    │   └── MessageInputPanel.java     Dark input bar — text field, send, file, audio, AI buttons
    │
    ├── transfer/
    │   ├── FileTransferManager.java   Send: UUID fileId, 64 KB Base64 chunks, FILE_META/CHUNK/COMPLETE
    │   └── FileProgressListener.java  Progress callback interface
    │
    ├── audio/
    │   ├── AudioRecorder.java         Java Sound API — 16 kHz / 16-bit / mono PCM capture
    │   ├── AudioPlayer.java           javax.sound playback from byte array
    │   └── AudioManager.java         Orchestrates record → send, receive → play; 10 MB guard
    │
    ├── ai/
    │   └── GeminiChatManager.java     Sends AI_REQUEST, surfaces AI_RESPONSE to MainFrame
    │
    └── util/
        ├── SwingUtils.java            runOnEDT helper
        └── ChatTheme.java             WhatsApp colour palette + font constants
```

---

## Build

Requires Java 21+ and Maven 3.x.

```bash
# Compile only
mvn compile -f "client desktop/pom.xml"

# Build fat JAR (includes Jackson)
mvn package -f "client desktop/pom.xml"
# Output: client desktop/target/client-desktop.jar
```

---

## Run

```bash
java -jar "client desktop/target/client-desktop.jar"
```

Connects to `127.0.0.1:12345` by default. Change `SERVER_HOST` / `SERVER_PORT` in [`ClientConfig.java`](src/main/java/com/javachat/desktop/config/ClientConfig.java).

---

## Configuration

| Constant | Default | File |
|---|---|---|
| `SERVER_HOST` | `127.0.0.1` | `config/ClientConfig.java` |
| `SERVER_PORT` | `12345` | `config/ClientConfig.java` |
| Max file size | 50 MB | `transfer/FileTransferManager.java` |
| Max audio size | 10 MB | `audio/AudioManager.java` |
| Chunk size | 64 KB | `transfer/FileTransferManager.java` |
| Audio format | 16 kHz / 16-bit / mono PCM → `.wav` | `audio/AudioRecorder.java` |

---

## Protocol

Follows the JavaChat newline-delimited JSON protocol exactly. All message fields are identical to the server's `Message.java` POJO.

### Message types used by this client

| Type | Direction | Purpose |
|---|---|---|
| `JOIN` | → Server | Claim username on connect |
| `JOIN_SUCCESS` | ← Server | Accepted — show main window |
| `JOIN_ERROR` | ← Server | Rejected — show error, allow retry |
| `CHAT` | ↔ Server | Send / receive text messages |
| `USER_JOINED` | ← Server | Add user to sidebar |
| `USER_LEFT` | ← Server | Remove user from sidebar |
| `FILE_META` | → Server | Begin file transfer |
| `FILE_CHUNK` | ↔ Server | 64 KB Base64 data block |
| `FILE_COMPLETE` | ↔ Server | Transfer finished |
| `AUDIO_META` | → Server | Begin audio transfer |
| `AUDIO_CHUNK` | ↔ Server | 64 KB Base64 audio block |
| `AUDIO_COMPLETE` | ↔ Server | Audio transfer finished |
| `AI_REQUEST` | → Server | Send Gemini prompt |
| `AI_RESPONSE` | ← Server | Display AI response bubble |
| `ERROR` | ← Server | Show error in UI |

### AI usage

Prefix any message with `@ai ` in the input bar:

```
@ai What is the capital of France?
```

The text after `@ai ` is sent as the `prompt` field of an `AI_REQUEST` message. The Gemini API key is stored only on the server — the client never holds it.

---

## Development Rules

1. Never block the Swing EDT with network or I/O operations.
2. Always dispatch UI updates via `SwingUtils.runOnEDT()`.
3. Protocol field names must match the server exactly (`fileId`, `fileName`, `fileSize`, `totalChunks`, `chunkIndex`, `data`, `prompt`).
4. Audio files must use an extension from the server allowlist: `.wav`, `.m4a`, `.aac`, `.mp3`, `.ogg`, `.opus`.
5. The Gemini API key must never appear in client code or config files.
6. File transfer uses a UUID `fileId` generated client-side — required by the server's `FileTransferManager`.

---

## Implementation Phases (completed)

| Phase | Deliverable | Status |
|---|---|---|
| 1 | Project setup — Maven, package structure, `Main.java` | ✅ |
| 2 | Swing UI skeleton — all frames and panels | ✅ |
| 3 | TCP connection — `SocketClient`, `ConnectionManager` | ✅ |
| 4 | Protocol — Jackson, `Message`, `MessageType`, `ProtocolManager` | ✅ |
| 5 | Username join flow — `JOIN` / `JOIN_SUCCESS` / `JOIN_ERROR` | ✅ |
| 6 | Global chat — send/receive `CHAT`, `USER_JOINED`, `USER_LEFT` | ✅ |
| 7 | File sharing — `FileTransferManager`, progress bar, save dialog | ✅ |
| 8 | Audio messages — record `.wav`, send, receive, playback | ✅ |
| 9 | Gemini AI — `GeminiChatManager`, `@ai` trigger, response bubble | ✅ |
| 10 | Hardening + UI polish — WhatsApp dark theme, reconnect dialog, EDT safety, fat JAR | ✅ |

---

## Integration

```
          JAVACHAT SERVER  (TCP 12345)
                  │
        Global Chat Room
                  │
      ┌───────────┴───────────┐
      │                       │
DESKTOP CLIENT           MOBILE CLIENT
Java 21 + Swing          Java + Android
TCP Socket               TCP Socket
Same JSON protocol       Same JSON protocol
```
