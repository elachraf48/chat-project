# Mobile Client Bug Fixes Plan

## Overview

Fix 6 known issues in the Android mobile client. All are isolated, small changes
in the mobile codebase. No server changes required.

The bugs were identified by a static-code audit and are documented in
[`client-mobile/README.md`](client-mobile/README.md). The two red bugs (BUG 1 +
BUG 3) are the most impactful — the sender literally cannot see any message they
send. The rest are medium/low severity.

---

## Sub-Tasks

---

### Sub-Task 1 — BUG 1 + BUG 3: Own messages never appear / no timestamp

**Status:** [ ] pending

**Intent**

The server uses `broadcastExcept` so it never echoes the sender's message back.
`sendTextMessage()` also never adds the message locally. Combined, the sender
never sees their own messages. Fixing BUG 1 (add locally) immediately surfaces
BUG 3 (no timestamp on the locally-created message), so both are fixed together.

**Expected Outcomes**

- After tapping Send, the sender's message appears immediately in their own chat list.
- The message bubble shows the correct send time.
- Other clients still receive the broadcast as before (server unchanged).

**Todo List**

1. In [`Message.chat()`](client-mobile/app/src/main/java/com/javachat/mobile/protocol/Message.java:49),
   add `m.timestamp = java.time.Instant.now().toString();` before the `return`.
2. In [`GlobalChatFragment.sendTextMessage()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:183),
   capture the created `Message` in a local variable, call `addMessage(msg)` after
   `send()`, and remove the stale comment.

**Relevant Context**

- `Message.chat()` — `client-mobile/app/src/main/java/com/javachat/mobile/protocol/Message.java` line 49
- `GlobalChatFragment.sendTextMessage()` — line 183
- `MessageAdapter.formatTime()` — reads `message.getTimestamp()`; empty string if null

---

### Sub-Task 2 — BUG 2: FILE_COMPLETE notification always says "file"

**Status:** [ ] pending

**Intent**

The server's `FILE_COMPLETE` message carries only `fileId`; `fileName` is null.
`GlobalChatFragment` reads `message.getFileName()` which is always null, so the
system message always says *"📎 file received"* with no actual name.

The filename was received earlier in `FILE_META` and is already stored in
`FileTransferManager.incomingMeta`. We need to expose a lookup method and call it
before clearing the map in `handleIncoming`.

**Expected Outcomes**

- When a file transfer completes, the system message reads *"📎 report.pdf received — saved to Downloads"* (or whatever the real filename is).
- No change to the wire protocol or server.

**Todo List**

1. Add a `getFileNameForId(String fileId)` public method to
   [`FileTransferManager`](client-mobile/app/src/main/java/com/javachat/mobile/transfer/FileTransferManager.java)
   that returns `incomingMeta.get(fileId).getFileName()` (null-safe).
2. In [`GlobalChatFragment.handleMessage()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:151),
   `case FILE_COMPLETE`: call `getFileNameForId(message.getFileId())` **before**
   calling `handleIncoming()` (which removes the meta entry), and use that name in
   the system message.

**Relevant Context**

- `FileTransferManager.incomingMeta` — map populated on `FILE_META`, cleared on `FILE_COMPLETE`
- `GlobalChatFragment` line 151–157 — current FILE_COMPLETE handling

---

### Sub-Task 3 — IMP 3: Self not shown in online users list

**Status:** [ ] pending

**Intent**

`USER_JOINED` broadcasts only go to *other* clients. The joining user receives
`JOIN_SUCCESS` but never gets a `USER_JOINED` for themselves. So the user never
appears in their own online users drawer.

**Expected Outcomes**

- After joining, the user sees their own username at the top of the online users drawer.
- Other users who join later still appear via the normal `USER_JOINED` path.

**Todo List**

1. Add a public `addSelf(String username)` method to
   [`GlobalChatFragment`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java)
   that calls the existing `addOnlineUser(username)`.
2. In [`ChatActivity.onCreate()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/ChatActivity.java:48),
   after `fm.beginTransaction()...commit()`, post a `Runnable` on the fragment
   container to call `fragment.addSelf(AuthenticationManager.getInstance().getUsername())`.
   Use `getSupportFragmentManager().executePendingTransactions()` first so the
   fragment is attached before calling.

**Relevant Context**

- `ChatActivity.onCreate()` — line 48–53
- `GlobalChatFragment.addOnlineUser()` — line 280
- `AuthenticationManager.getInstance().getUsername()` — available at this point (set during JOIN_SUCCESS in LoginActivity)

---

### Sub-Task 4 — IMP 1: No feedback when file send completes

**Status:** [ ] pending

**Intent**

When the user sends a file, the transfer happens silently in a background thread.
There is no success or failure message shown in the chat. The minimal fix is a
callback that posts a system message after `FILE_COMPLETE` is sent.

**Expected Outcomes**

- After a file finishes uploading, a system message appears: *"📎 filename.pdf sent"*.
- On I/O error during send, a system message appears: *"Failed to send filename.pdf"*.

**Todo List**

1. Add a `TransferCallback` interface to
   [`FileTransferManager`](client-mobile/app/src/main/java/com/javachat/mobile/transfer/FileTransferManager.java)
   with `void onSendComplete(String fileName)` and `void onSendFailed(String fileName)`.
2. Change `sendFile(Uri, Context)` to accept an additional `TransferCallback callback`
   parameter. Call `callback.onSendComplete(fileName)` after sending `FILE_COMPLETE`,
   and `callback.onSendFailed(fileName)` in the catch block.
3. In [`GlobalChatFragment.onFileSelected()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:198),
   pass a lambda that calls `requireActivity().runOnUiThread(() -> addSystemMessage(...))`.
4. Add the string resources `file_sent` and `file_send_failed` to `strings.xml`.

**Relevant Context**

- `FileTransferManager.sendFile()` — line 57
- `GlobalChatFragment.onFileSelected()` — line 198–200
- `strings.xml` — `client-mobile/app/src/main/res/values/strings.xml`

---

### Sub-Task 5 — IMP 2: Audio sender sees no confirmation bubble

**Status:** [ ] pending

**Intent**

When the user records and sends audio, the recording dialog closes and nothing
appears in their own chat. The receiver sees an audio bubble; the sender sees
nothing. Fix: after `AUDIO_COMPLETE` is sent, call back to the fragment to add a
local audio message for the sender.

**Expected Outcomes**

- After sending audio, the sender sees an audio bubble in their chat (same style as the receiver's bubble) with label *"You"* and a play button.

**Todo List**

1. Add a `SendCallback` (or reuse `TransferCallback`) interface to
   [`AudioManager`](client-mobile/app/src/main/java/com/javachat/mobile/audio/AudioManager.java)
   with `void onSendComplete(String fileId, String fileName)`.
2. Change `sendAudio(Uri, Context)` to accept an additional `SendCallback callback`
   parameter. After sending `AUDIO_COMPLETE`, invoke
   `callback.onSendComplete(fileId, "audio.m4a")`.
3. Cache the sent audio locally: before calling the callback, store the source Uri
   into `audioCache` under `fileId` so the sender can also play it back.
4. In [`GlobalChatFragment.onAudioReady()`](client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java:207),
   pass a lambda that (on the UI thread) builds a local `AUDIO_COMPLETE` `Message`
   with `sender = myUsername`, `fileId`, and `timestamp = Instant.now().toString()`,
   then calls `addMessage()`.

**Relevant Context**

- `AudioManager.sendAudio()` — line 113
- `AudioManager.audioCache` — map of `fileId → Uri` for playback
- `GlobalChatFragment.onAudioReady()` — line 207
- `addAudioMessage()` — line 270 (already constructs correct `AUDIO_COMPLETE` message for incoming; reuse same shape for outgoing)

---

## Fix Order

```
Sub-Task 1  →  Sub-Task 2  →  Sub-Task 3  →  Sub-Task 4  →  Sub-Task 5
(BUG 1+3)      (BUG 2)        (IMP 3)        (IMP 1)        (IMP 2)
```

Each sub-task is independent — they can be reviewed and tested one at a time.
Sub-tasks 4 and 5 introduce callback parameters to `sendFile`/`sendAudio`; those
signature changes must be done in the same commit as the call-site update in the
fragment.
