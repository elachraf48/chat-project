# JavaChat — Audit #2 Fixes + README Refresh Plan

> **Goal:** Apply all 30 open issues from `audit-report-2.md` across all three sub-projects,
> then update all 4 README files to reflect the current state of the codebase.

---

## Overview

| Phase | Sub-tasks | Projects touched |
|---|---|---|
| A — Critical & High fixes | 1–6 | Server, Desktop, Mobile |
| B — Medium fixes | 7–10 | Server, Desktop, Mobile |
| C — Low fixes | 11 | Server, Desktop |
| D — README refresh | 12 | All |

---

## Sub-task 1 — Server: Socket read timeout + atomic rate limiting + bounded thread pool

**Status:** [ ] pending

**Intent:**
Fix the three most impactful server issues: (1) unbounded threads crash under load, (2) no socket timeout lets
zombie / slow-read clients hold threads forever, (3) rate limiting has a race window that can be double-bypassed.

**Expected Outcomes:**
- `ChatServer.java` uses a bounded `ThreadPoolExecutor` (core 10, max 500).
- `ClientHandler.java` calls `socket.setSoTimeout(300_000)` (5 min) inside `run()` before `readLine()`.
- `GeminiService.java` rate-limit check uses `ConcurrentHashMap.compute()` to make it atomic.

**Todo List:**
1. Read `chat server/src/main/java/com/javachat/server/network/ChatServer.java`.
2. Replace `Executors.newCachedThreadPool()` with a `ThreadPoolExecutor(10, 500, 60s, SynchronousQueue, CallerRunsPolicy)`.
3. Read `chat server/src/main/java/com/javachat/server/network/ClientHandler.java`.
4. Add `socket.setSoTimeout(300_000)` at the start of `run()`.
5. Read `chat server/src/main/java/com/javachat/server/ai/GeminiService.java` — the rate-limit block (lines ~112–117).
6. Replace the `get/check/put` pattern with a `compute()` lambda as described in `audit-report-2.md`.

**Relevant Context:**
- `ChatServer.java` — `start()` method, field `executor`
- `ClientHandler.java` — `run()` method, socket field
- `GeminiService.java` — `generate()` method, `lastRequestTime` field

---

## Sub-task 2 — Server: Silent catch + log injection + eviction for lastRequestTime

**Status:** [ ] pending

**Intent:**
Fix three lower-priority server issues: (1) swallowed IOException when loading model list, (2) chat text echoed to
logs without sanitization (log injection), (3) `lastRequestTime` map grows forever.

**Expected Outcomes:**
- `GeminiService.java` model-list IOException is logged as a warning, not swallowed.
- `GlobalChatRoom.java` sanitizes `text` before logging (`\n`→`\\n`, `\r`→`\\r`).
- `GeminiService.java` sweeps entries older than `RATE_LIMIT_MS` to prevent unbounded map growth
  (use `entrySet().removeIf(e -> now - e.getValue() > RATE_LIMIT_MS * 2)` before the compute).

**Todo List:**
1. Read `GeminiService.java` — the model-list load catch block (line ~264).
2. Replace `ignored` catch with a `Logger.warn(...)` call.
3. Add an LRU sweep of `lastRequestTime` entries older than `RATE_LIMIT_MS * 2` at the start of `generate()`.
4. Read `chat server/src/main/java/com/javachat/server/chat/GlobalChatRoom.java` line ~22.
5. Add `text.replace("\n", "\\n").replace("\r", "\\r")` before the `Logger.info` call.

**Relevant Context:**
- `GeminiService.java` — `loadModels()` catch block and `generate()` method
- `GlobalChatRoom.java` — `broadcast()` or `handleChat()` method

---

## Sub-task 3 — Desktop: Graphics2D leak fix (CRITICAL)

**Status:** [ ] pending

**Intent:**
Every paint cycle in `MessageInputPanel` leaks a native graphics peer because `setup(g)` returns a
`Graphics2D` created with `g.create()` but callers never call `dispose()`. This is the only desktop CRITICAL.

**Expected Outcomes:**
- All three `paintComponent` overrides that call `setup(g)` wrap the graphics work in `try { … } finally { g2.dispose(); }`.

**Todo List:**
1. Read `client desktop/src/main/java/com/javachat/desktop/ui/MessageInputPanel.java` — all three button `paintComponent` lambdas/overrides.
2. In each override: assign `Graphics2D g2 = setup(g)`, wrap the paint code in `try { } finally { g2.dispose(); }`.
3. Verify there are exactly 3 call sites (attach button, mic button, send button).

**Relevant Context:**
- `MessageInputPanel.java` — `createFileButton()` line ~143, `createMicButton()` line ~193, `createSendButton()` line ~233
- `setup()` helper at line ~274

---

## Sub-task 4 — Desktop: Socket leak + unbounded AudioManager threads + decode logging

**Status:** [ ] pending

**Intent:**
Three high-severity desktop issues: (1) socket orphaned if stream init throws, (2) unbounded `new Thread()`
per audio operation, (3) decode failures never logged so transfer bugs are invisible.

**Expected Outcomes:**
- `SocketClient.java` wraps stream initialization in try/catch; closes new socket before re-throwing.
- `AudioManager.java` holds a single `ExecutorService` and submits all `new Thread()` bodies to it.
- `MainFrame.java` decode exception catch blocks log `e.getMessage()` (or stack trace) to `System.err`.

**Todo List:**
1. Read `client desktop/src/main/java/com/javachat/desktop/network/SocketClient.java` — `connect()` method.
2. Apply the try/catch socket-leak fix from `audit-report-2.md` (open new socket, init streams, close old only on success).
3. Read `client desktop/src/main/java/com/javachat/desktop/audio/AudioManager.java` — all `new Thread(...)` usages.
4. Add `private final ExecutorService executor = Executors.newCachedThreadPool();` and replace all `new Thread(r).start()` with `executor.submit(r)`.
5. Read `client desktop/src/main/java/com/javachat/desktop/ui/MainFrame.java` — decode exception catch blocks (lines ~175, ~211).
6. Add `System.err.println("[WARN] File/audio decode error: " + e.getMessage());` in each catch.

**Relevant Context:**
- `SocketClient.java` — `connect()` method
- `AudioManager.java` — `toggleRecording()`, `playReceived()`, `startRecording()`, `stopAndSend()`
- `MainFrame.java` — `FILE_COMPLETE` and `AUDIO_COMPLETE` handler catch blocks

---

## Sub-task 5 — Mobile: ConcurrentHashMap for audioCache + incomingBuffers + incomingMeta (CRITICAL)

**Status:** [ ] pending

**Intent:**
`audioCache`, `incomingBuffers`, and `incomingMeta` in mobile `AudioManager` are plain `HashMap` accessed from both
the background reader thread and the main thread. This is a data race that can corrupt JVM internal state.

**Expected Outcomes:**
- All three maps changed from `HashMap` to `ConcurrentHashMap` in `AudioManager.java`.

**Todo List:**
1. Read `client-mobile/app/src/main/java/com/javachat/mobile/audio/AudioManager.java` — field declarations.
2. Change `audioCache`, `incomingBuffers`, and `incomingMeta` field types and initializers to `ConcurrentHashMap`.
3. Verify the import `java.util.concurrent.ConcurrentHashMap` is present.

**Relevant Context:**
- `AudioManager.java` — fields `audioCache`, `incomingBuffers`, `incomingMeta`

---

## Sub-task 6 — Mobile: AudioPlayer thread + AudioRecorder bad state + GlobalChatFragment null-check + isAdded guard

**Status:** [ ] pending

**Intent:**
Four high-priority mobile issues: (1) `MediaPlayer` completion listener fires off-main-thread, (2) failed
`recorder.prepare()` leaves `MediaRecorder` in bad state, (3) `tvAiResponse`/`progressAi` NPE in callback,
(4) `requireActivity()` called without `isAdded()` guard.

**Expected Outcomes:**
- `AudioPlayer.java` completion listener posts to `MainLooper` before calling `listener.onCompletion()`.
- `AudioRecorder.java` calls `recorder.release(); recorder = null;` in the `prepare()` catch block.
- `GlobalChatFragment.java` null-checks `tvAiResponse` and `progressAi` before accessing them.
- `GlobalChatFragment.java` `isAdded()` guard is moved to before the `requireActivity()` call.

**Todo List:**
1. Read `client-mobile/app/src/main/java/com/javachat/mobile/audio/AudioPlayer.java` — `OnCompletionListener` lambda.
2. Wrap `listener.onCompletion()` in `new Handler(Looper.getMainLooper()).post(listener::onCompletion)`.
3. Read `client-mobile/app/src/main/java/com/javachat/mobile/audio/AudioRecorder.java` — `startRecording()` method.
4. Add `recorder.release(); recorder = null;` in the `IOException` catch around `prepare()`.
5. Read `client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java` — AI callback block (lines ~243–270).
6. Add null-checks for `tvAiResponse` and `progressAi` as described in `audit-report-2.md`.
7. Move the `isAdded()` guard to before the `requireActivity()` call at line ~206.

**Relevant Context:**
- `AudioPlayer.java` — `play()` method, `OnCompletionListener`
- `AudioRecorder.java` — `startRecording()`, `prepare()` call
- `GlobalChatFragment.java` — AI dialog button click and response callback

---

## Sub-task 7 — Mobile: Hardcoded string + timestamp guard + stale context fix

**Status:** [ ] pending

**Intent:**
Three medium-priority mobile issues: (1) "✓ Saved to Downloads" hardcoded (not translatable), (2) timestamp
substring guard is `< 19` but extraction starts at index 11 and reads to 16 — guard should be `< 16`,
(3) `v.getContext()` in RecyclerView click listeners captures potentially stale context.

**Expected Outcomes:**
- `strings.xml` gets `<string name="file_saved_to_downloads">✓ Saved to Downloads</string>`.
- `MessageAdapter.java` uses `context.getString(R.string.file_saved_to_downloads)` instead of the literal.
- Timestamp guard in `MessageAdapter.java` changed to `iso.length() < 16`.
- Audio play click listeners capture `holder.itemView.getContext().getApplicationContext()` instead of `v.getContext()`.

**Todo List:**
1. Read `client-mobile/app/src/main/res/values/strings.xml` — check for existing `file_saved_to_downloads`.
2. Add the string entry if absent.
3. Read `client-mobile/app/src/main/java/com/javachat/mobile/ui/MessageAdapter.java` — line ~146.
4. Replace hardcoded string with `context.getString(R.string.file_saved_to_downloads)`.
5. In the timestamp helper (line ~197), change `iso.length() < 19` to `iso.length() < 16`.
6. In audio play listeners (lines ~97, ~107), capture `appCtx = holder.itemView.getContext().getApplicationContext()` and use it.

**Relevant Context:**
- `strings.xml`
- `MessageAdapter.java` — `onBindViewHolder()`, timestamp format helper

---

## Sub-task 8 — Mobile: File transfer success toast + silent null read + requireActivity guard (already in sub-task 6)

**Status:** [ ] pending

**Intent:**
Two remaining medium mobile issues: (1) success toast fires unconditionally even if save failed, (2) null
data from `readAllBytes()` returns silently with no callback.

**Expected Outcomes:**
- `FileTransferManager.java` success toast is moved inside the try block (non-exception path only).
- `FileTransferManager.java` null data check calls `callback.onSendFailed(fileName)` if callback is non-null.

**Todo List:**
1. Read `client-mobile/app/src/main/java/com/javachat/mobile/transfer/FileTransferManager.java` — `saveToDownloads()` (line ~172) and `sendFile()` (line ~73).
2. In `saveToDownloads()`: move the success toast inside the try block, before the catch. Remove the unconditional post outside.
3. In `sendFile()`: after `byte[] data = readAllBytes(...)`, add a null guard that calls `callback.onSendFailed(fileName)` if `data == null` and callback is non-null.

**Relevant Context:**
- `FileTransferManager.java` — `saveToDownloads()` and `sendFile()`

---

## Sub-task 9 — Desktop: Medium issues (bubble MAX_W, dead code, null check, volatile snapshot)

**Status:** [ ] pending

**Intent:**
Four medium desktop issues that are each one-liner fixes.

**Expected Outcomes:**
- `MessagePanel.java` — `MAX_W` for `TextBubble` and `SystemBubble` aligned to `460` and `440` as-is; `AiBubble.MAX_W` reduced to `460` to avoid overflow (or document the intentional difference — pick one).
- `MainFrame.java` — dead `humanSize(Long)` method removed.
- `OnlineUsersPanel.java` — `user` null-checked before `isEmpty()`.
- `ConnectionManager.java` (desktop) — listener snapshotted to a local variable before the null check.
- `ChatTheme.java` — `ACCENT_HOVER` removed or left with a `// reserved` comment.

**Todo List:**
1. Read `client desktop/src/main/java/com/javachat/desktop/ui/MessagePanel.java` — `MAX_W` constants.
2. Align `AiBubble.MAX_W` to `460` to match `TextBubble` (or document divergence).
3. Read `client desktop/src/main/java/com/javachat/desktop/ui/MainFrame.java` — find and remove the dead `humanSize(Long)` method.
4. Read `client desktop/src/main/java/com/javachat/desktop/ui/OnlineUsersPanel.java` — line ~132.
5. Add `(user == null || user.isEmpty())` null-safe guard.
6. Read `client desktop/src/main/java/com/javachat/desktop/network/ConnectionManager.java` — listener dispatch block.
7. Snapshot `MessageListener l = listener; if (l != null) l.onMessage(msg);`.
8. Read `client desktop/src/main/java/com/javachat/desktop/util/ChatTheme.java` — `ACCENT_HOVER`.
9. Remove `ACCENT_HOVER` (or add `// reserved` comment if it may be used soon).

**Relevant Context:**
- `MessagePanel.java` — `TextBubble`, `SystemBubble`, `AiBubble` inner classes
- `MainFrame.java` — `humanSize()` private method
- `OnlineUsersPanel.java` — user list render
- `ConnectionManager.java` — `onMessage` dispatch
- `ChatTheme.java` — color constants

---

## Sub-task 10 — Server Low: Env-var port override + no cumulative chunk tracking (documented)

**Status:** [ ] pending

**Intent:**
Two low-priority server issues: (1) `ServerConfig` should read `SERVER_PORT` env var as override,
(2) document the known limitation that server does not track cumulative chunk sizes.

**Expected Outcomes:**
- `ServerConfig.java` checks `System.getenv("SERVER_PORT")` and, if set, overrides the properties value.
- `audit-report-2.md` issues 29 & 30 (no chunk tracking, no in-flight cleanup) marked as "documented limitation" in `chat server/README.md`.

**Todo List:**
1. Read `chat server/src/main/java/com/javachat/server/config/ServerConfig.java`.
2. After reading `server.port` from properties, check `System.getenv("SERVER_PORT")` — if non-null and parseable, use it.
3. Update `chat server/README.md` to add a row in Known Issues for the cumulative chunk size and in-flight transfer timeout limitations.

**Relevant Context:**
- `ServerConfig.java` — `loadPort()` or constructor
- `chat server/README.md` — Known Issues table

---

## Sub-task 11 — Server Low: AudioTransferManager double-extension bypass (documented)

**Status:** [ ] pending

**Intent:**
`"filename.exe.m4a"` passes the extension allowlist. The server never executes files, so actual risk is low,
but document the behavior explicitly. Optionally normalize to only check the last extension.

**Expected Outcomes:**
- `AudioTransferManager.java` extension check normalized (use `fileName.toLowerCase(Locale.ROOT)` and confirm the last-segment check is correct — no change needed if it already uses `endsWith`; just add a comment).
- A note added to `chat server/README.md` Known Issues.

**Todo List:**
1. Read `chat server/src/main/java/com/javachat/server/transfer/AudioTransferManager.java` — extension check block.
2. If the check uses `endsWith()`, it already only checks the last extension. Add a `// extension allowlist — only checks final segment` comment.
3. Add a Known Issues row in `chat server/README.md`.

**Relevant Context:**
- `AudioTransferManager.java` — filename validation block

---

## Sub-task 12 — README refresh for all 4 files

**Status:** [ ] pending

**Intent:**
Update all 4 README files to reflect the current state after all audit fixes:
- Remove stale "Known Issues" entries that are now resolved.
- Add the remaining documented limitations (chunk tracking, double-extension, no heartbeat).
- Update architecture notes (bounded thread pool, socket timeout).
- Sync the general `README.md` "Design Decisions" and "Known Issues" tables.

**Expected Outcomes:**
- `README.md` — "Known Issues" table updated; "Design Decisions" mentions bounded pool; architecture diagram updated.
- `chat server/README.md` — Known Issues table accurate; architecture section mentions bounded pool + socket timeout.
- `client desktop/README.md` — Known Issues table updated; threading section mentions ExecutorService for audio.
- `client-mobile/README.md` — Known Issues "Already fixed" section updated with all Audit #2 fixes.

**Todo List:**
1. Review `README.md` Known Issues — remove items that are fixed; add items still open.
2. Review `chat server/README.md` — update threading model table, Known Issues.
3. Review `client desktop/README.md` — update threading model table, Known Issues.
4. Review `client-mobile/README.md` — move Audit #2 fixes to "Already fixed", update open Known Issues.
5. Write all four updated README files.

**Relevant Context:**
- All 4 README files read above
- `audit-report-2.md` — issues 1–30 (all being fixed in sub-tasks 1–11)

---

## Issue → Sub-task Map

| # | Report ID | Sub-task |
|---|---|---|
| 1 | 🔴 Desktop Graphics2D leak | Sub-task 3 |
| 2 | 🔴 Mobile audioCache HashMap | Sub-task 5 |
| 3 | 🔴 Server line-length after read | Sub-task 1 |
| 4 | 🟠 Desktop SocketClient leak | Sub-task 4 |
| 5 | 🟠 Desktop unbounded threads | Sub-task 4 |
| 6 | 🟠 Desktop decode not logged | Sub-task 4 |
| 7 | 🟠 Mobile AudioPlayer wrong thread | Sub-task 6 |
| 8 | 🟠 Mobile AudioRecorder bad state | Sub-task 6 |
| 9 | 🟠 Mobile tvAiResponse NPE | Sub-task 6 |
| 10 | 🟠 Server rate limit race | Sub-task 1 |
| 11 | 🟠 Server unbounded thread pool | Sub-task 1 |
| 12 | 🟠 Server no socket timeout | Sub-task 1 |
| 13 | 🟡 Desktop bubble MAX_W | Sub-task 9 |
| 14 | 🟡 Desktop dead humanSize() | Sub-task 9 |
| 15 | 🟡 Desktop user null check | Sub-task 9 |
| 16 | 🟡 Desktop volatile listener | Sub-task 9 |
| 17 | 🟡 Mobile hardcoded string | Sub-task 7 |
| 18 | 🟡 Mobile timestamp guard | Sub-task 7 |
| 19 | 🟡 Mobile incomingBuffers/Meta | Sub-task 5 |
| 20 | 🟡 Mobile success toast | Sub-task 8 |
| 21 | 🟡 Mobile requireActivity guard | Sub-task 6 |
| 22 | 🟡 Mobile silent null file read | Sub-task 8 |
| 23 | 🟡 Mobile stale context | Sub-task 7 |
| 24 | 🟢 Server silent catch | Sub-task 2 |
| 25 | 🟢 Server map eviction | Sub-task 2 |
| 26 | 🟢 Server log injection | Sub-task 2 |
| 27 | 🟢 Server env-var port | Sub-task 10 |
| 28 | 🟢 Desktop ACCENT_HOVER | Sub-task 9 |
| 29 | 🟢 Server chunk tracking | Sub-task 10 |
| 30 | 🟢 Server in-flight cleanup | Sub-task 10 |
