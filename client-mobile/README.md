# JavaChat — Android Mobile Client

A Java Android chat client (API 26–35) for the JavaChat application. Connects to the JavaChat TCP server, participates in the global chat room, and supports text messaging, file sharing, audio recording, and Gemini AI queries.

---

## Quick Start

### Requirements

- Android Studio (Ladybug or newer recommended)
- Android SDK API 35
- A running JavaChat server (see [`../chat server/README.md`](../chat%20server/README.md))

### Build

```bash
cd client-mobile
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Server address

At the login screen, enter the server's IP address and port (`12345` by default).

Default fallback in [`ClientConfig.java`](app/src/main/java/com/javachat/mobile/config/ClientConfig.java):

```java
public static final String DEFAULT_SERVER_IP   = "172.31.48.1"; // Android emulator host
public static final int    DEFAULT_SERVER_PORT  = 12345;
```

---

## Features

| Feature | Description |
|---|---|
| **Text chat** | WhatsApp-style bubbles — green own (right), white others (left), grey system pills |
| **File sharing** | Send any file up to 50 MB; receivers get a ⬇ Download button saved to Downloads |
| **Audio messages** | Record `.m4a` voice messages up to 10 MB; play back in-app with MediaPlayer |
| **Gemini AI** | Toolbar button opens AI dialog; response shown as a system bubble in chat |
| **Online users** | Sliding drawer with live user list and presence notifications |
| **Reconnect** | Clean disconnect on `ChatActivity.onDestroy()`; re-enter credentials to reconnect |

---

## UI Structure

### Screens

| Screen | Class | Description |
|---|---|---|
| Login | `LoginActivity` | Username, server IP, port input + Connect button |
| Chat | `ChatActivity` | Root container hosting `GlobalChatFragment` + online users drawer |
| Global Chat | `GlobalChatFragment` | RecyclerView message list + input bar |

### Message view types (RecyclerView)

| Type | Alignment | Appearance |
|---|---|---|
| Own text message | Right | Green bubble `#DCF8C6` |
| Other text message | Left | White bubble `#FFFFFF` |
| System event | Centre | Green pill `#D4EED4`, dark text |
| Audio message | Right / Left | Teal bubble with ▶ play button + "Voice message" |
| File message | Left (received) / Right (sent) | White bubble with 📄 icon, filename, size, ⬇ Download |

### Color palette

| Token | Hex | Usage |
|---|---|---|
| `colorPrimary` | `#075E54` | Toolbar, buttons |
| `colorAccent` | `#25D366` | Send button, active elements |
| `chatBackground` | `#ECE5DD` | Message list background |
| `bubbleSelf` | `#DCF8C6` | Own message bubble |
| `bubbleOther` | `#FFFFFF` | Received message bubble |
| `systemMsgBg` | `#D4EED4` | System event pill background |

---

## Package Structure

```
com.javachat.mobile
├── MainActivity                Entry point / splash router
├── JavaChatApplication         Application subclass
├── config/
│   └── ClientConfig            Constants: IP, port, timeouts, chunk size, max sizes
├── network/
│   ├── SocketClient            Raw TCP socket — send() / receive() JSON lines
│   ├── ConnectionManager       Lifecycle + background reader thread; AtomicBoolean guard
│   ├── MessageListener         Callback interface for incoming messages
│   └── NotificationHelper      Android notification channel + post helpers
├── protocol/
│   ├── Message                 Jackson DTO — all fields; @JsonInclude(NON_NULL); factory methods
│   ├── MessageType             Enum — 16 message types
│   └── ProtocolManager         Jackson serialize / deserialize
├── auth/
│   ├── AuthenticationManager   Singleton — holds current username
│   └── LoginActivity           Username + server input, connect flow
├── transfer/
│   └── FileTransferManager     Chunked send (64 KB Base64); chunk reassembly; MediaStore save
├── audio/
│   ├── AudioManager            Orchestrates record → send and receive → play
│   ├── AudioRecorder           MediaRecorder capture → .m4a temp file
│   └── AudioPlayer             MediaPlayer playback from temp file
├── ai/
│   └── GeminiChatManager       Sends AI_REQUEST; shows response dialog
├── ui/
│   ├── ChatActivity            Main container — hosts fragment + drawer
│   ├── GlobalChatFragment      Message list + input bar + all message handling
│   ├── MessageAdapter          5-type RecyclerView adapter
│   ├── OnlineUsersAdapter      Online users drawer list
│   └── MessageInputController  Input bar button wiring
└── util/
    ├── InputValidator          Regex + bounds checks (username, IP, port, file size)
    └── PermissionManager       Android runtime permission requests
```

---

## Architecture

### Threading model

| Thread | Role |
|---|---|
| **Main (UI)** | All RecyclerView updates, dialog creation, button handlers |
| **Background reader** | `ConnectionManager` executor — reads JSON from socket, posts to main thread |
| **File send thread** | `FileTransferManager` — reads bytes, sends chunks |
| **Audio record** | MediaRecorder on a background service thread |
| **Audio play** | MediaPlayer callbacks on main thread |

### Connection lifecycle

```
LoginActivity.connectToServer()     [background thread via ExecutorService]
  └─ ConnectionManager.connect()
      └─ SocketClient connects to HOST:PORT
          └─ Send JOIN message
              └─ Receive JOIN_SUCCESS → navigate to ChatActivity
                 Receive JOIN_ERROR  → post error to UI thread, re-enable button
```

---

## File Transfer Protocol

```
Sender → FILE_META  (fileId UUID, fileName, fileSize Long, totalChunks Integer)
Sender → FILE_CHUNK ×N  (fileId, chunkIndex, data Base64)
Sender → FILE_COMPLETE  (fileId)

Server relays all three to every other connected client, adding sender field.
```

- **Chunk size:** 64 KB
- **Encoding:** `android.util.Base64.NO_WRAP`
- **Max file size:** 50 MB (`ClientConfig.MAX_FILE_SIZE_BYTES`)
- **Save (API 29+):** `MediaStore.Downloads`
- **Save (API 26–28):** direct write to `Environment.DIRECTORY_DOWNLOADS`

---

## Audio Messaging Protocol

Same chunked structure as file transfer but using `AUDIO_META` / `AUDIO_CHUNK` / `AUDIO_COMPLETE` types.

- **Format:** `.m4a` (MediaRecorder default MPEG-4 + AAC)
- **Max size:** 10 MB
- **Record:** `AudioRecorder` writes to a temp file in `getCacheDir()`
- **Play:** `AudioPlayer` copies received bytes to a temp file, plays with `MediaPlayer`

---

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

---

## Build Configuration

| Setting | Value |
|---|---|
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 35 (Android 15) |
| Compile SDK | 35 |
| Java | 11 |
| Gradle | 8.x (Kotlin DSL) |
| Build features | View binding |

**Dependencies:**
- `androidx.appcompat`, `androidx.constraintlayout`, `androidx.drawerlayout`, `androidx.recyclerview`
- `com.google.android.material`
- `com.fasterxml.jackson.core:jackson-databind:2.17.2`

---

## Protocol Field Reference

The `Message` class fields, with their correct names (aligned with server):

| Field | Type | Notes |
|---|---|---|
| `type` | `MessageType` | Message type enum |
| `messageId` | `String` | Server-assigned UUID |
| `sender` | `String` | Username |
| `text` | `String` | Chat message body |
| `timestamp` | `String` | ISO-8601 string |
| `fileId` | `String` | UUID for file / audio transfers |
| `fileName` | `String` | Original filename |
| `fileSize` | `Long` | Size in bytes |
| `totalChunks` | `Integer` | Total chunk count |
| `chunkIndex` | `Integer` | Zero-based chunk index |
| `data` | `String` | Base64-encoded chunk payload |
| `prompt` | `String` | AI request text |

> ⚠️ `fileId` (not `transferId`), `data` (not `text` for chunks), `prompt` (not `text` for AI) — these were corrected from earlier versions.

---

## Known Issues

| # | Severity | Issue |
|---|---|---|
| — | 🟠 Medium | File upload has no progress bar — bubble appears only after all chunks are sent |
| — | 🟢 Low | `RECORD_AUDIO` not runtime-requested on API 31+ — add request in `PermissionManager` |
| — | 🟢 Low | No typing indicators or read receipts |

### Fixed (Audit #1 + #2)

| Issue | Fix |
|---|---|
| Own chat messages never appeared for sender | `addMessage(msg)` added after `send()` in `GlobalChatFragment` |
| FILE_COMPLETE notification showed `"file"` not actual filename | `FileTransferManager.getFileNameForId()` wired in `GlobalChatFragment` |
| User didn't see themselves in online users list | `fragment.addSelf()` called from `ChatActivity` |
| AI dialog crash on config change | `onDestroyView()` dismisses dialog; `isShowing()` guard on updates |
| File save fails on API 26–28 | Fallback writes directly to `Environment.DIRECTORY_DOWNLOADS` |
| Double-connect on fast tap | `AtomicBoolean connecting` guard in `ConnectionManager` |
| Protocol field mismatches (`fileId`, `data`, `prompt`, `Long`) | `Message.java` fields corrected to match server |
| `audioCache`, `incomingBuffers`, `incomingMeta` HashMap race | Changed to `ConcurrentHashMap` |
| MediaPlayer completion listener fires off-main-thread | Posts to `MainLooper` before calling `onCompletion()` |
| `recorder.prepare()` failure left recorder in bad state | `recorder.release()` called in catch before re-throwing |
| `tvAiResponse`/`progressAi` NPE in AI callback | Null-checked before access |
| `requireActivity()` called without `isAdded()` guard | `isAdded()` guard added before `requireActivity()` in callbacks |
| "✓ Saved to Downloads" hardcoded string | Moved to `R.string.file_saved_to_downloads` |
| Success toast fired even on save failure | Toast moved inside try block in `saveToDownloads()` |
| Null data from `readAllBytes()` returned silently | Now calls `callback.onSendFailed()` |
| Timestamp substring guard `< 19` for index 11–16 extraction | Fixed to `< 16` |
| Stale `v.getContext()` in audio play listeners | Captures `getApplicationContext()` at bind time |

---

## Full Documentation

See [`../README.md`](../README.md) for the complete project overview, protocol specification, and session flow diagram.
