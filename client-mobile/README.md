# JavaChat — Full Functionality Audit Report

**Date:** November 2026  
**Scope:** Chat Server + Mobile Client  
**Audit method:** Static code review of all source files

---

## Summary

| Feature | Server | Mobile Client | Status |
|---|---|---|---|
| TCP connection | ✅ | ✅ | Working |
| JOIN / authentication | ✅ | ✅ | Working |
| Chat messaging | ✅ | ✅ | Working |
| Presence (USER_JOINED/LEFT) | ✅ | ✅ | Working |
| Online users list | ✅ | ✅ | Working |
| File sharing | ✅ | ⚠️ | 2 issues |
| Audio messaging | ✅ | ⚠️ | 1 issue |
| Gemini AI assistant | ✅ | ✅ | Working |
| Reconnect / disconnect | ✅ | ✅ | Working |
| Input validation | ✅ | ✅ | Working |
| Notifications | N/A | ✅ | Working |
| Own message deduplication | ✅ | ⚠️ | 1 issue |

**Bugs to fix: 3  
Improvements: 3**

---

## ✅ Working correctly

### 1. TCP Connection & JOIN
- Server accepts unlimited concurrent clients via `ExecutorService`.
- `LoginActivity` validates username format (regex `[A-Za-z0-9_\-]{1,32}`) client-side before connecting.
- `ConnectionManager` guards against double-connect with `AtomicBoolean`.
- JOIN_SUCCESS correctly triggers navigation to `ChatActivity`.
- `ChatActivity` uses `Theme.JavaChat.NoActionBar` — no more toolbar crash on start.

### 2. Chat messages
- Server uses `broadcastExcept` — sender does NOT receive their own echo.
- Mobile does NOT add own message locally — waits for server broadcast.
- **Inconsistency detected:** see BUG 1 below.

### 3. Presence & online users list
- `USER_JOINED` / `USER_LEFT` broadcast correctly to all others.
- Online users drawer updates correctly.

### 4. Gemini AI
- `AI_REQUEST` uses `prompt` field — matches server's `Message.getPrompt()`.
- `AI_RESPONSE` returns only to the requesting client.
- API key loaded from `GEMINI_API_KEY` env var — never exposed to clients.
- AI dialog guarded against dangling views (`aiDialog.isShowing()` check).

### 5. Input validation & security
- Server: 1 MB line limit, 32-char username, 2000-char message, file size limits.
- Client: same username regex as server, port range check, chunk size cap.
- `InputValidator.isValidIncoming` no longer crashes on null `fileSize` (fixed).

### 6. Reconnect
- `LoginActivity.onCreate()` calls `disconnect()` + `logout()` — clean state on return to login.
- `ChatActivity.onDestroy()` disconnects cleanly.

---

## 🐛 Bugs to Fix

### BUG 1 — Own chat messages never appear for the sender

**Location:** [`GlobalChatFragment.sendTextMessage()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:183) and [`GlobalChatRoom.broadcast()`](chat server/src/main/java/com/javachat/server/chat/GlobalChatRoom.java)

**Problem:** The server uses `broadcastExcept(senderUsername)` — it intentionally does NOT send the message back to the sender. The mobile `sendTextMessage()` also does NOT add the message locally (BUG 3 fix). Result: the sender's own message **never appears** in their chat.

**Fix options (choose one):**  
- **Option A (recommended):** Add the message locally in `sendTextMessage()` with the current timestamp (same as before BUG 3 fix). This gives instant feedback with no round-trip delay.
- **Option B:** Revert server to `broadcast()` (send to everyone including sender), and remove the local add. Then the sender sees their message with the server-stamped UUID and timestamp.

**Recommended fix:** Option A — add message locally for the sender.

```java
// In GlobalChatFragment.sendTextMessage()
private void sendTextMessage() {
    String text = etMessageInput.getText().toString().trim();
    if (text.isEmpty()) return;
    String username = AuthenticationManager.getInstance().getUsername();
    Message msg = Message.chat(username, text);
    ConnectionManager.getInstance().send(msg);
    etMessageInput.setText("");
    addMessage(msg);   // ← add this line back
}
```

---

### BUG 2 — FILE_COMPLETE notification shows null filename

**Location:** [`GlobalChatFragment.handleMessage()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:151)

**Problem:** On `FILE_COMPLETE`, the fragment calls `message.getFileName()`. But the server's `FILE_COMPLETE` message only carries `fileId` — `fileName` is null. The fragment falls back to `"file"`, so the notification always says *"📎 file received — saved to Downloads"*.

**Fix:** Store the `fileName` from `FILE_META` in `FileTransferManager.incomingMeta` and retrieve it when `FILE_COMPLETE` arrives:

```java
// In GlobalChatFragment, case FILE_COMPLETE:
case FILE_COMPLETE:
    String savedFileName = FileTransferManager.getInstance()
            .getFileNameForId(message.getFileId()); // add this method
    FileTransferManager.getInstance().handleIncoming(message, requireContext());
    addSystemMessage(getString(R.string.file_received,
            savedFileName != null ? savedFileName : "file"));
    break;
```

Add to `FileTransferManager`:
```java
public String getFileNameForId(String fileId) {
    Message meta = incomingMeta.get(fileId);
    return meta != null ? meta.getFileName() : null;
}
```

---

### BUG 3 — Own sent message has no timestamp displayed

**Location:** [`GlobalChatFragment.sendTextMessage()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:183) — linked to BUG 1 fix

**Problem:** When BUG 1 is fixed by adding the message locally, `Message.chat(username, text)` does not set a `timestamp`. `MessageAdapter.formatTime()` will display an empty string for own messages.

**Fix:** Set a client-side timestamp when creating the local message:

```java
// In the mobile Message class, update chat() factory:
public static Message chat(String sender, String text) {
    Message m = new Message();
    m.type = MessageType.CHAT;
    m.sender = sender;
    m.text = text;
    m.timestamp = java.time.Instant.now().toString();  // ← add this
    return m;
}
```

---

## ⚠️ Improvements

### IMP 1 — File transfer: no progress feedback to sender

**Location:** [`FileTransferManager.sendFile()`](client-mobile/app/src/main/java/com/javachat/mobile/transfer/FileTransferManager.java:57)

**Problem:** File sending happens silently in the background. For large files the user has no indication that a transfer is in progress or completed. Errors are silently swallowed (`catch (IOException ignored)`).

**Improvement:** After `FILE_COMPLETE` is sent, add a system message to the chat: *"📎 filename.pdf sent"*. This requires a callback from `FileTransferManager` back to the fragment. For now, the minimum fix is to log a system message via a callback interface:

```java
// Add interface to FileTransferManager:
public interface TransferCallback {
    void onSendComplete(String fileName);
    void onSendFailed(String fileName);
}
```

---

### IMP 2 — Audio: sender sees no confirmation that audio was sent

**Location:** [`GlobalChatFragment.onAudioReady()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:207)

**Problem:** When the user records and sends audio, the recording dialog closes and nothing appears in the chat. The receiver sees an audio bubble, but the sender sees nothing.

**Improvement:** After `AudioManager.sendAudio()` completes, add an audio message locally for the sender too (similar to BUG 1 fix for chat). This requires a completion callback from `AudioManager.sendAudio()`.

---

### IMP 3 — `GlobalChatFragment` not added to the online users list on JOIN_SUCCESS

**Location:** [`ChatActivity.onMessageReceived()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/ChatActivity.java:90) and [`GlobalChatFragment.handleMessage()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:128)

**Problem:** When the user joins, `JOIN_SUCCESS` contains `sender = username`. The online users list is only updated on `USER_JOINED` messages (which go to other clients, not the sender). So the user never sees themselves in the online users list.

**Improvement:** In `LoginActivity.onMessageReceived`, when `JOIN_SUCCESS` is received, also call `addOnlineUser(username)` — or in `ChatActivity.onCreate()` after the fragment is added:

```java
// In ChatActivity after setting listener, add own username to online list
// (requires a public method on GlobalChatFragment)
GlobalChatFragment f = getGlobalChatFragment();
if (f != null) {
    f.addSelf(AuthenticationManager.getInstance().getUsername());
}
```

---

## Protocol field alignment — verified ✅

The following field name mismatches from earlier versions are now **fixed** in the mobile `Message` class:

| Field | Server | Mobile (old) | Mobile (current) |
|---|---|---|---|
| Transfer ID | `fileId` | `transferId` ❌ | `fileId` ✅ |
| Chunk payload | `data` | `text` ❌ | `data` ✅ |
| AI request text | `prompt` | `text` ❌ | `prompt` ✅ |
| File size type | `Long` | `long` ❌ | `Long` ✅ |

---

## Server — verified ✅

| Check | Result |
|---|---|
| `AUDIO_COMPLETE` handler in `ClientHandler` | ✅ Present |
| `Message.audioComplete()` factory method | ✅ Present |
| `AudioTransferManager.handleAudioComplete()` | ✅ Present |
| `GlobalChatRoom` uses `broadcastExcept` | ✅ Fixed |
| `USER_LEFT` broadcasts to all on disconnect | ✅ Working |
| File size limit enforced (50 MB) | ✅ Working |
| Audio size limit enforced (10 MB) | ✅ Working |
| Audio format allowlist enforced | ✅ Working |
| AI prompt length cap (2000 chars) | ✅ Working |
| Username format validated | ✅ Working |

---

## Action plan — priority order

| # | Severity | Fix |
|---|---|---|
| 1 | 🔴 High | **BUG 1** — Add own message locally after send (1 line in `GlobalChatFragment`) |
| 2 | 🔴 High | **BUG 3** — Add timestamp to `Message.chat()` factory in mobile client |
| 3 | 🟡 Medium | **BUG 2** — Store fileName from META and use it in FILE_COMPLETE notification |
| 4 | 🟡 Medium | **IMP 3** — Add self to online users list on JOIN_SUCCESS |
| 5 | 🟢 Low | **IMP 1** — File send completion callback |
| 6 | 🟢 Low | **IMP 2** — Audio send completion callback |
