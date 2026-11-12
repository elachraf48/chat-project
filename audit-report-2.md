# JavaChat — Audit Report #2

> Fresh static code review after all previous fixes were applied.  
> Previous audit-report.md issues are verified resolved.

---

## Overall Health

| Project | Status | New Open Issues |
|---|---|---|
| **Chat Server** | ⚠️ Some gaps remain | 1 critical, 4 high, 6 medium, 4 low |
| **Desktop Client** | 🔴 Critical bug found | 1 critical, 3 high, 5 medium |
| **Mobile Client** | ⚠️ Several gaps | 1 critical, 5 high, 8 medium |

---

## Chat Server — Issues

### 🔴 CRITICAL

**ClientHandler.java line 79–82 — Line-length check is ineffective as a DoS protection**

`BufferedReader.readLine()` reads the entire line into memory **before** the length check runs. A 100 MB line is already allocated in the JVM heap before `line.length() > MAX_LINE_LENGTH` is evaluated. The check correctly terminates the connection, but it cannot prevent the initial memory allocation.

**Fix:** Set a socket read timeout via `socket.setSoTimeout()` at connection start, or wrap the reader with a `LimitedInputStream` that throws before allocating oversized buffers. The simplest safe fix is:
```java
socket.setSoTimeout(60_000); // 60s read timeout prevents zombie connections
```
This also closes the slow-read attack vector (client connects and sends data one byte per minute, holding the thread indefinitely).

---

### 🟠 HIGH

**GeminiService.java lines 112–117 — Rate limiting is not atomic (race condition)**

```java
Long last = lastRequestTime.get(username);
if (last != null && (now - last) < RATE_LIMIT_MS) { ... }
lastRequestTime.put(username, now);
```

Between `get()` and `put()`, two concurrent requests from the same user on two TCP connections can both pass the check simultaneously. The window is tiny but real.

**Fix:** Replace the get/check/put pattern with an atomic `compute`:
```java
long now = System.currentTimeMillis();
long[] blocked = {-1};
lastRequestTime.compute(username, (k, last) -> {
    if (last != null && (now - last) < RATE_LIMIT_MS) {
        blocked[0] = RATE_LIMIT_MS - (now - last);
        return last; // keep old timestamp
    }
    return now;
});
if (blocked[0] > 0) {
    return "Please wait " + (blocked[0] + 999) / 1000 + " second(s) before sending another AI request.";
}
```

---

**ChatServer.java line 44 — Unbounded thread pool**

`Executors.newCachedThreadPool()` creates one thread per connected client with no upper bound. 10,000 simultaneous clients = 10,000 threads = likely OOM. Each thread consumes ~1 MB stack.

**Fix:** Use a bounded pool:
```java
executor = new ThreadPoolExecutor(
    10, 500,             // core 10, max 500 threads
    60L, TimeUnit.SECONDS,
    new java.util.concurrent.SynchronousQueue<>(),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

---

**ClientHandler.java line 80 — No socket read timeout (slow-read / zombie connection)**

`readLine()` blocks indefinitely. A client that connects and never sends data (or sends one byte/second) holds a thread forever.

**Fix:** Add `socket.setSoTimeout(300_000)` (5 minutes) in the constructor or at the start of `run()`. `SocketTimeoutException` (a subtype of `IOException`) will be caught by the existing handler.

---

**GeminiService.java line 264 — Silent catch when loading model list from properties**

```java
} catch (java.io.IOException ignored) {}
```

If `application.properties` is unreadable, the exception is swallowed and default models are used with no log output. An operator sees no indication that their configuration was ignored.

**Fix:** Replace with:
```java
} catch (java.io.IOException e) {
    Logger.warn("[GEMINI] Could not load model list from application.properties: " + e.getMessage() + " — using defaults.");
}
```

---

### 🟡 MEDIUM

**FileTransferManager.java / AudioTransferManager.java — No cumulative chunk size validation**

The server validates `fileSize` in `*_META` but never accumulates received chunk sizes. A client can declare `fileSize=1MB` in META, then send 50 MB of chunks. The server relays every chunk without checking totals. Receivers can be DoS'd by receiving far more data than declared.

**Fix:** Track total relayed bytes per `fileId` on the server; reject chunks that exceed the declared `fileSize`.

---

**FileTransferManager.java / AudioTransferManager.java — No in-flight transfer cleanup**

If a client sends `FILE_META` but never sends `FILE_COMPLETE` (e.g., app crash), receiving clients hold the incoming buffer in memory indefinitely. There is no timeout or cleanup.

**Fix:** This is a known design limitation of a stateless relay server. Document it explicitly. A future improvement could add a cleanup sweep on `USER_LEFT`.

---

**Message.java line 85 — Only `CHAT` messages get a `messageId`; others don't**

`messageId` is only set in `Message.chat()`. All other relayed messages (FILE_META, AUDIO_META, etc.) have no unique ID. Clients cannot deduplicate or correlate messages.

**Impact:** Low for current usage, but fragile if retry logic is ever added.

---

**GlobalChatRoom.java line 22 — Chat text echoed directly into logs**

```java
Logger.info("[CHAT] " + senderUsername + ": " + text.substring(0, Math.min(text.length(), 80)));
```

If text contains ANSI escape sequences or newlines, log output is corrupted. Same log injection risk that was fixed for filenames.

**Fix:** Sanitize `text` before logging: `text.replace("\n", "\\n").replace("\r", "\\r")`

---

**Main.java lines 37–41 — `IOException` from `chatServer.start()` exits without cleanup**

If the server fails to bind the port (e.g., port already in use), `start()` throws `IOException`. The `catch` block logs it and `main()` returns. However, the shutdown hook (`chatServer.stop()`) is registered at line 29 and will still fire — calling `stop()` on a server that never fully started. This is harmless but can produce confusing log output.

---

### 🟢 LOW

**GeminiService.java — `lastRequestTime` map grows forever**

Users who made one AI request stay in the map forever. In a long-running server with many unique users, this leaks memory (albeit slowly).

**Fix:** Use a `LinkedHashMap` with LRU eviction, or periodically sweep entries older than `RATE_LIMIT_MS`.

---

**AudioTransferManager.java line 131 — Double-extension bypass**

`"filename.exe.m4a"` passes the extension check. The server relays a file named like audio but containing arbitrary content.

**Impact:** Low (server never stores or executes files), but clients that auto-play received audio could be misled.

---

**application.properties — No port override via environment variable**

`server.port=12345` is read from properties but has no env-var fallback (`SERVER_PORT`). Containerized deployments cannot configure the port without modifying the file.

---

**ClientManager.java — `serializeQuietly` returns null on failure but callers don't distinguish**

When serialization fails, `broadcast()` silently returns (no message sent to anyone). An error is logged but no metric is incremented and no retry occurs.

---

## Desktop Client — Issues

### 🔴 CRITICAL

**MessageInputPanel.java lines 274–277 — `setup()` returns `Graphics2D` that is never disposed**

```java
private static Graphics2D setup(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    return g2;  // caller must dispose — but none do
}
```

Every call site (`createFileButton()` line 143, `createMicButton()` line 193, `createSendButton()` line 233) calls `setup(g)` then **never calls `g2.dispose()`**. `Graphics.create()` allocates a native peer — leaking one per paint cycle. On high-frequency repaints this accumulates thousands of leaked graphics contexts.

**Fix:** Add `g2.dispose()` at the end of each `paintComponent` that calls `setup()`. Or refactor `setup()` to take a lambda:
```java
// Each paintComponent:
Graphics2D g2 = setup(g);
try {
    // ... paint ...
} finally {
    g2.dispose();
}
```

---

### 🟠 HIGH

**SocketClient.java lines 33–37 — Socket leaked if stream initialization fails**

```java
socket = new Socket(host, port);         // socket open
writer = new PrintWriter(...);           // if this throws...
reader = new BufferedReader(...);        // ...socket is already open but never closed
```

If `getOutputStream()` or `getInputStream()` throws, the new socket is orphaned.

**Fix:**
```java
Socket newSocket = new Socket(host, port);
try {
    writer = new PrintWriter(new OutputStreamWriter(newSocket.getOutputStream(), UTF_8), true);
    reader = new BufferedReader(new InputStreamReader(newSocket.getInputStream(), UTF_8));
} catch (IOException e) {
    try { newSocket.close(); } catch (IOException ignored) {}
    throw e;
}
close(); // close old socket only after new one is fully set up
socket = newSocket;
```

---

**AudioManager.java lines 64–134 — Unbounded thread creation**

Each call to `toggleRecording()`, `playReceived()`, `startRecording()`, and `stopAndSend()` creates a `new Thread()`. Rapid user interaction (e.g., repeatedly clicking mic) spawns unlimited threads. No thread pool or guard.

**Fix:** Replace all `new Thread(...)` calls with submissions to a `private final ExecutorService executor = Executors.newCachedThreadPool()`.

---

**MainFrame.java lines 175–180, 211–215 — Decode failures silently swallowed without logging**

```java
catch (Exception e) {
    fileBuffers.remove(fid);
    fileMeta.remove(fid);
    chatPanel.appendNotification("[File error] Transfer corrupted, discarded.");
}
```

The exception `e` is never logged. When debugging a corrupted transfer, there is no stack trace or error message in any log.

**Fix:** Add `System.err.println("[DEBUG] Decode failure: " + e.getMessage());` or route through the logger.

---

### 🟡 MEDIUM

**MessagePanel.java lines 170, 420, 485 — Inconsistent MAX_W for different bubble types**

- `TextBubble.MAX_W = 460`
- `SystemBubble.MAX_W = 440`
- `AiBubble.MAX_W = 520`

Three different max widths in the same panel. AI bubbles are wider than text bubbles. On a narrow window, AI bubbles can overflow or truncate differently than expected.

---

**MainFrame.java lines 440–445 — `humanSize()` is dead code**

A private `humanSize(Long)` method exists in `MainFrame` but is never called (the same method is in `MessagePanel`). Dead code.

**Fix:** Remove it.

---

**ChatTheme.java line 40 — `ACCENT_HOVER` is never referenced**

Defined but unused. Dead constant.

---

**OnlineUsersPanel.java line 132 — `user` not null-checked before `isEmpty()`**

```java
String initial = user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase();
```

If `users` list somehow contains a null entry (added via `addUser(null)`), this throws NPE.

**Fix:** `String initial = (user == null || user.isEmpty()) ? "?" : user.substring(0, 1).toUpperCase();`

---

**ConnectionManager.java lines 79–86 — Listener read without local variable**

```java
if (listener != null) {
    final Message m = msg;
    listener.onMessage(m);   // listener could be set to null here by another thread
}
```

`listener` is `volatile`, so visibility is guaranteed, but between the null check and the call, another thread could set `listener = null`. The old (non-null) listener would still be invoked, which is correct behavior, but the pattern should use a local copy for clarity:

```java
MessageListener l = listener; // snapshot
if (l != null) l.onMessage(msg);
```

---

## Mobile Client — Issues

### 🔴 CRITICAL

**AudioManager.java lines 130, 183 — `audioCache` HashMap accessed from multiple threads without synchronization**

```java
audioCache.put(fileId, audioUri);        // executor thread (line 130)
Uri uri = audioCache.get(fileId);        // main thread via playAudioById (line 204)
```

`HashMap` is not thread-safe. Concurrent put/get can cause `ConcurrentModificationException` or corrupted internal state.

**Fix:** Change `audioCache` to `ConcurrentHashMap`:
```java
private final Map<String, Uri> audioCache = new java.util.concurrent.ConcurrentHashMap<>();
```
Apply the same fix to `incomingBuffers` and `incomingMeta` if accessed from multiple threads.

---

### 🟠 HIGH

**AudioPlayer.java lines 23–24 — `OnCompletionListener` callback fires on MediaPlayer's internal thread**

```java
mp.setOnCompletionListener(mp2 -> {
    release();
    if (listener != null) listener.onCompletion();
});
```

`MediaPlayer`'s completion listener fires on a background thread, not the main thread. If `listener.onCompletion()` updates the UI, this will cause `CalledFromWrongThreadException` or silent corruption.

**Fix:** Post to main thread:
```java
mp.setOnCompletionListener(mp2 -> {
    release();
    if (listener != null) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(listener::onCompletion);
    }
});
```

---

**AudioManager.java line 36 — `prepare()` in AudioRecorder not wrapped in try-catch**

In `AudioRecorder.startRecording()`, `recorder.prepare()` can throw `IOException` (documented in Android SDK), but it's not caught at the call site. The exception propagates to `AudioManager.showRecordDialog()` line 92–101, where it is caught. However, if `recorder` is in a bad state after a failed `prepare()`, subsequent calls to `stopRecording()` may throw `IllegalStateException`.

**Fix:** Add `recorder.release()` in the catch block in `AudioRecorder.startRecording()`:
```java
try {
    recorder.prepare();
} catch (IOException e) {
    recorder.release();
    recorder = null;
    throw e;
}
```

---

**GlobalChatFragment.java line 243–244 — `tvAiResponse`/`progressAi` used without null-check in callback**

```java
tvAiResponse = dialogView.findViewById(R.id.tvAiResponse);
progressAi   = dialogView.findViewById(R.id.progressAi);
```

These can return null if the view ID doesn't exist in the layout. Inside the response callback at lines 266–269:
```java
tvAiResponse.setText(response);       // NPE if null
tvAiResponse.setVisibility(View.VISIBLE);
progressAi.setVisibility(View.GONE);  // NPE if null
```

**Fix:** Add null-checks:
```java
if (tvAiResponse != null) { tvAiResponse.setText(response); tvAiResponse.setVisibility(View.VISIBLE); }
if (progressAi   != null)   progressAi.setVisibility(View.GONE);
```

---

**ConnectionManager.java line 90–91 — `send()` failure is silent to the caller**

```java
} catch (IOException e) {
    notifyConnectionLost(e.getMessage());
}
```

`send()` returns `void`. The caller has no way to know whether a message was delivered. This is by design (fire-and-forget), but for file/audio chunk sends, a failed send mid-transfer results in a broken transfer with no notification beyond `onConnectionLost`.

---

**MessageAdapter.java line 146 — Hardcoded string not in `strings.xml`**

```java
holder.btnDownloadFile.setText("✓ Saved to Downloads");
```

This string is not translatable and not in `strings.xml`. Should be `R.string.file_saved_to_downloads`.

**Fix:** Add to `strings.xml`:
```xml
<string name="file_saved_to_downloads">✓ Saved to Downloads</string>
```
And reference with `context.getString(R.string.file_saved_to_downloads)`.

---

### 🟡 MEDIUM

**MessageAdapter.java lines 199–200 — Timestamp substring extraction is fragile**

```java
return iso.substring(11, 16);
```

This extracts characters 11–15 from an ISO-8601 string assuming format `2025-01-01T12:00:00Z`. If the timestamp is shorter than 16 chars (e.g., `2025-01-01`), this throws `StringIndexOutOfBoundsException`. Line 197 checks `iso.length() < 19` but the check should be `< 16`.

**Fix:** Change guard to `if (iso == null || iso.length() < 16) return "";`

---

**AudioManager.java — `incomingBuffers` and `incomingMeta` not thread-safe**

Same issue as `audioCache` — these HashMaps are written from the background reader thread (via `handleIncoming()`) and could theoretically be read from the main thread. Use `ConcurrentHashMap`.

---

**FileTransferManager.java line 172 — Toast shown even on save failure**

```java
executor.submit(() -> {
    saveToDownloads(context, fileName, data);
    main.post(() -> Toast.makeText(..., "📎 " + fileName + " saved to Downloads", ...).show());
});
```

The success toast is posted unconditionally after `saveToDownloads()`, even though `saveToDownloads()` can fail (it catches IOException and shows an error toast). The success toast fires immediately after (ordering depends on handler queue).

**Fix:** Move the success toast inside `saveToDownloads()` and show it only on the non-exception path (already partially done — just move the success toast into the try block, before the catch).

---

**GlobalChatFragment.java line 206 — `requireActivity()` called before `isAdded()` check**

```java
requireActivity().runOnUiThread(() -> {      // line 206
    if (!isAdded()) return;                   // line 229
```

`requireActivity()` throws `IllegalStateException` if the fragment is not attached. The `isAdded()` check is inside the lambda but the `requireActivity()` call is outside. If the fragment detaches between line 206 and line 229 execution, the outer call throws.

**Fix:** Add guard before `requireActivity()`:
```java
if (!isAdded()) return;
requireActivity().runOnUiThread(() -> {
    if (!isAdded()) return;
    ...
});
```

---

**FileTransferManager.java line 73 — Silent null return on file read failure**

`readAllBytes()` returns `null` on IOException and the caller silently returns without notifying the user or callback. File send failure is invisible.

**Fix:** Call `callback.onSendFailed(fileName)` when `data == null`:
```java
byte[] data = readAllBytes(context, fileUri);
if (data == null) {
    if (callback != null) callback.onSendFailed(fileName);
    return;
}
```

---

**MessageAdapter.java line 97, 107 — Audio play callback captures `v.getContext()`**

```java
holder.btnPlayAudioSelf.setOnClickListener(v ->
    AudioManager.getInstance().playAudioById(v.getContext(), m.getFileId()));
```

`v.getContext()` captures the view's context. If the view is recycled and reattached to a different context (e.g., configuration change), the context may be stale or leaked.

**Fix:** Capture application context at bind time:
```java
Context appCtx = holder.itemView.getContext().getApplicationContext();
holder.btnPlayAudioSelf.setOnClickListener(v ->
    AudioManager.getInstance().playAudioById(appCtx, m.getFileId()));
```

---

## New Fix Plan Summary

| # | Severity | Project | File | Line | Description |
|---|---|---|---|---|---|
| 1 | 🔴 Critical | Desktop | `MessageInputPanel.java` | 274 | `setup()` returns `Graphics2D` never disposed — leak per paint cycle |
| 2 | 🔴 Critical | Mobile | `AudioManager.java` | 130, 183 | `audioCache` HashMap not thread-safe — concurrent get/put |
| 3 | 🔴 Critical | Server | `ClientHandler.java` | 79 | Line-length check after full read — no actual DoS protection |
| 4 | 🟠 High | Desktop | `SocketClient.java` | 33–37 | Socket leaked if stream init throws |
| 5 | 🟠 High | Desktop | `AudioManager.java` | 64–134 | Unbounded `new Thread()` on every audio operation |
| 6 | 🟠 High | Desktop | `MainFrame.java` | 175, 211 | Decode exception `e` never logged |
| 7 | 🟠 High | Mobile | `AudioPlayer.java` | 23 | Completion listener on wrong thread — crashes UI |
| 8 | 🟠 High | Mobile | `AudioRecorder.java` | 36 | `prepare()` failure leaves recorder in bad state |
| 9 | 🟠 High | Mobile | `GlobalChatFragment.java` | 243 | `tvAiResponse`/`progressAi` not null-checked in callback |
| 10 | 🟠 High | Server | `GeminiService.java` | 112 | Rate limiting is not atomic — two requests can pass simultaneously |
| 11 | 🟠 High | Server | `ChatServer.java` | 44 | Unbounded thread pool — 1 thread per client, no limit |
| 12 | 🟠 High | Server | `ClientHandler.java` | 80 | No socket read timeout — slow-read attacks hold threads forever |
| 13 | 🟡 Medium | Desktop | `MessagePanel.java` | 170, 420, 485 | Inconsistent `MAX_W` across bubble types |
| 14 | 🟡 Medium | Desktop | `MainFrame.java` | 440 | Dead `humanSize()` method |
| 15 | 🟡 Medium | Desktop | `OnlineUsersPanel.java` | 132 | `user` not null-checked before `isEmpty()` |
| 16 | 🟡 Medium | Desktop | `ConnectionManager.java` | 79 | Listener not snapshotted — theoretical race on volatile read |
| 17 | 🟡 Medium | Mobile | `MessageAdapter.java` | 146 | Hardcoded "✓ Saved to Downloads" — not in `strings.xml` |
| 18 | 🟡 Medium | Mobile | `MessageAdapter.java` | 197 | Timestamp guard `< 19` should be `< 16` for substring safety |
| 19 | 🟡 Medium | Mobile | `AudioManager.java` | — | `incomingBuffers`/`incomingMeta` are not thread-safe `HashMap`s |
| 20 | 🟡 Medium | Mobile | `FileTransferManager.java` | 172 | Success toast fires unconditionally even if save failed |
| 21 | 🟡 Medium | Mobile | `GlobalChatFragment.java` | 206 | `requireActivity()` called without `isAdded()` guard |
| 22 | 🟡 Medium | Mobile | `FileTransferManager.java` | 73 | Null file read returns silently — no callback fired |
| 23 | 🟡 Medium | Mobile | `MessageAdapter.java` | 97 | Stale context captured in audio play click listener |
| 24 | 🟢 Low | Server | `GeminiService.java` | 264 | Silent catch on model list load — no log warning |
| 25 | 🟢 Low | Server | `GeminiService.java` | — | `lastRequestTime` map grows forever — no eviction |
| 26 | 🟢 Low | Server | `GlobalChatRoom.java` | 22 | Chat text echoed to logs without sanitization |
| 27 | 🟢 Low | Server | `application.properties` | — | No `SERVER_PORT` env-var override for containerized deployments |
| 28 | 🟢 Low | Desktop | `ChatTheme.java` | 40 | `ACCENT_HOVER` constant is unused (dead code) |
| 29 | 🟢 Low | Server | `FileTransferManager.java` | — | No cumulative chunk size validation — declared vs. sent size unchecked |
| 30 | 🟢 Low | Server | `FileTransferManager.java` | — | No in-flight transfer timeout/cleanup on disconnect |
