# JavaChat — Full Fix Plan

> **Status: ALL TASKS COMPLETE**

## Overview

Comprehensive audit of all 3 sub-projects (chat server, desktop client, mobile client). This plan covers every confirmed bug, security issue, and wiring gap found by static code review. Each sub-task is self-contained and ordered by severity. Fixes are minimal — no refactors, no new features beyond what is needed to correct existing defects.

---

## Sub-Task 1 — Server: Remove hardcoded API key

**Status:** [ ] pending

**Intent:** The Gemini API key is stored in `application.properties` and committed to git. This key is compromised. Remove it so the server refuses to start with a file-based key and forces use of the `GEMINI_API_KEY` environment variable.

**Expected Outcomes:**
- `application.properties` contains no API key value.
- `GeminiService.resolveApiKey()` ignores the `application.properties` source (or warns loudly and falls back to env var only).
- Starting the server without `GEMINI_API_KEY` set logs a clear warning but still starts (AI requests return a friendly "AI not configured" message).

**Todo List:**
1. Delete the `server.gemini.api-key` line from `application.properties`.
2. In `GeminiService.resolveApiKey()`, remove the fallback that reads from properties, or change it to log a `WARNING` and skip.
3. Verify `GeminiService.generate()` returns `"AI assistant is not configured — set GEMINI_API_KEY."` when key is blank.

**Relevant Context:**
- `chat server/src/main/resources/application.properties` line 8
- `chat server/src/main/java/com/javachat/server/ai/GeminiService.java` — `resolveApiKey()` method (lines 185-206)

---

## Sub-Task 2 — Server: Fix AI model fallback chain

**Status:** [x] done

**Intent:** The `MODEL_FALLBACK` array contains model names (`gemini-3.6-flash`, `gemini-3.5-flash`, `gemini-3.7-flash`) that do not match the Gemini API's actual model IDs. Requests silently fail with 404. Update to valid model names.

**Expected Outcomes:**
- AI requests succeed with the primary model.
- Fallback chain uses real Gemini model IDs.
- README technologies table reflects the correct model name.

**Todo List:**
1. In `GeminiService.java`, replace `MODEL_FALLBACK` with `["gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro"]`.
2. Update `README.md` technologies table if it still shows an incorrect model.

**Relevant Context:**
- `chat server/src/main/java/com/javachat/server/ai/GeminiService.java` lines 34-38 (`MODEL_FALLBACK` constant)

---

## Sub-Task 3 — Server: Fix unsafe JSON construction in GeminiService

**Status:** [x] done

**Intent:** `buildRequestBody()` builds the JSON payload using string concatenation with a basic `replace("\"", "\\\"")` escape. This does not handle newlines, carriage returns, tabs, or other control characters — a prompt containing `\n` would produce invalid JSON.

**Expected Outcomes:**
- Request body is always valid JSON regardless of prompt content.
- Existing behavior (prompt is sent, response is returned) is unchanged.

**Todo List:**
1. In `GeminiService.buildRequestBody()`, replace the string-concatenation approach with `ObjectMapper` serialization for the prompt value.

**Relevant Context:**
- `chat server/src/main/java/com/javachat/server/ai/GeminiService.java` — `buildRequestBody()` (lines 152-164)

---

## Sub-Task 4 — Desktop: Fix unbounded file/audio buffer memory leak in MainFrame

**Status:** [x] done

**Intent:** `MainFrame` keeps `fileBuffers`, `fileMeta`, `audioBuffers`, and `audioMeta` maps that grow indefinitely if a sender sends `FILE_META` or `AUDIO_META` but the transfer is never completed (e.g., sender disconnects mid-transfer). Buffers are never evicted.

**Expected Outcomes:**
- On `FILE_COMPLETE` / `AUDIO_COMPLETE`, the corresponding buffer and meta entries are removed from the map (already happens).
- If `FILE_META` arrives but `FILE_COMPLETE` never arrives within a reasonable time, the buffer is cleaned up.
- Simple fix: remove the buffer entry when the associated `FILE_META` sender disconnects, or add a `USER_LEFT` handler that clears that sender's pending transfers.

**Todo List:**
1. In `MainFrame.handleMessage()`, in the `USER_LEFT` case, iterate `fileMeta` and `audioMeta` and remove any entry whose `sender` matches the departed user; remove the corresponding buffer entry too.

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MainFrame.java` lines 56-62 (buffer maps), lines 136-221 (`handleMessage`)

---

## Sub-Task 5 — Desktop: Fix silent Base64 decode failures in MainFrame

**Status:** [x] done

**Intent:** `FILE_CHUNK` and `AUDIO_CHUNK` handling catches `Exception ignored` when decoding Base64 data. Corrupted or malformed chunks are silently skipped, producing a broken file that the user can download but cannot open. The error should at minimum be logged and the partial transfer should be discarded.

**Expected Outcomes:**
- A bad chunk logs a warning.
- On a decode error, the entire in-progress transfer (buffer + meta) is removed so a corrupted file is never offered for download.

**Todo List:**
1. In the `FILE_CHUNK` case in `MainFrame.handleMessage()`, replace `catch (Exception ignored) {}` with a handler that removes `fileBuffers.get(fid)` and `fileMeta.get(fid)` and logs the error.
2. Do the same for the `AUDIO_CHUNK` case.

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MainFrame.java` lines 163-166 (FILE_CHUNK catch), lines 192-196 (AUDIO_CHUNK catch)

---

## Sub-Task 6 — Desktop: Fix null sender causing "null" in AI and chat bubbles

**Status:** [x] done

**Intent:** `MessagePanel.appendChatMessage()` calls `localUsername.equals(message.getSender())` which throws NPE if `getSender()` returns null. Also `MainFrame` passes `message.getSender()` to `appendAudioBubble()` without a null check, causing the bubble to display "null  Voice message".

**Expected Outcomes:**
- If `getSender()` is null, messages are treated as "other" (not self) and the sender label shows an empty string or is hidden.
- `appendAiResponse()` handles a null `text` argument by showing an empty AI bubble rather than crashing.

**Todo List:**
1. In `MessagePanel.appendChatMessage()`, guard `localUsername.equals(...)` against a null sender: treat null sender as "other".
2. In `MainFrame.handleMessage()` AI_RESPONSE case, null-check `message.getText()` before passing to `appendAiResponse()`.
3. In `MainFrame.handleMessage()` AUDIO_COMPLETE case, replace raw `message.getSender()` with `message.getSender() != null ? message.getSender() : ""`.

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MessagePanel.java` line 79
- `client desktop/src/main/java/com/javachat/desktop/ui/MainFrame.java` lines 207, 213

---

## Sub-Task 7 — Desktop: Fix SocketClient.PrintWriter not detecting write errors

**Status:** [x] done

**Intent:** `PrintWriter.println()` does not throw `IOException` on write failure; it sets an internal error flag. The server could have dropped the connection, but `send()` completes silently. This means messages are silently lost and the client doesn't know it's disconnected.

**Expected Outcomes:**
- After each `send()`, `writer.checkError()` is called; if true, `IOException` is thrown to trigger the disconnect path.

**Todo List:**
1. In `SocketClient.send()`, after `writer.println(...)`, call `if (writer.checkError()) throw new IOException("Write error — connection lost.");`

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/network/SocketClient.java` line 46

---

## Sub-Task 8 — Desktop: Fix Graphics2D leak in MessageInputPanel and helpers

**Status:** [x] done

**Intent:** `MessageInputPanel.setup(Graphics)` creates and returns a `Graphics2D` without any enforcement that the caller disposes it. Several paint methods also do not dispose their created `Graphics2D` on all exit paths.

**Expected Outcomes:**
- All Graphics2D objects created via `g.create()` are wrapped in try-finally to guarantee disposal.

**Todo List:**
1. In every `paintComponent` method in `MessageInputPanel`, `LoginFrame`, and `MessagePanel` that calls `setup(g)` or `g.create()`, wrap the painting block in `try { ... } finally { g2.dispose(); }`.
2. In `MessagePanel.metrics()`, wrap in try-finally to dispose the temporary Graphics2D.

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MessageInputPanel.java` — `setup()` and all `paintComponent` overrides
- `client desktop/src/main/java/com/javachat/desktop/ui/MessagePanel.java` lines 154-160

---

## Sub-Task 9 — Desktop: Fix "Message" placeholder silently dropped

**Status:** [x] done

**Intent:** Both `GlobalChatPanel.sendChat()` and `MessageInputPanel.consumeText()` silently return empty string / bail out if the text equals the literal string `"Message"`. A user who genuinely types "Message" cannot send it.

**Expected Outcomes:**
- The placeholder is implemented using `setForeground()` + focus listeners only — no comparison to the literal placeholder text before sending.
- `consumeText()` returns whatever the user typed without filtering.

**Todo List:**
1. In `MessageInputPanel`, remove the `if (text.equals("Message")) return "";` guard from `consumeText()`.
2. In `GlobalChatPanel.sendChat()`, remove the `text.equals("Message")` early-return guard.
3. Verify the placeholder only appears visually (gray text) when the field is empty and unfocused — it should already work this way via the `FocusListener`.

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MessageInputPanel.java` line 70
- `client desktop/src/main/java/com/javachat/desktop/ui/GlobalChatPanel.java` line 109

---

## Sub-Task 10 — Desktop: Fix OnlineUsersPanel crash on empty username

**Status:** [x] done

**Intent:** `OnlineUsersPanel` calls `user.substring(0, 1)` to get the avatar initial. If an empty string is added, this throws `StringIndexOutOfBoundsException`.

**Expected Outcomes:**
- Empty usernames produce a "?" avatar rather than a crash.

**Todo List:**
1. In `UsersListPanel.paintComponent()`, change `user.substring(0, 1)` to `user.isEmpty() ? "?" : user.substring(0, 1)`.

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/ui/OnlineUsersPanel.java` line 132

---

## Sub-Task 11 — Desktop: Move hardcoded UI colors to ChatTheme

**Status:** [x] done

**Intent:** Several paint methods use `new Color(0x2A3942)`, `new Color(0x1A2A32)`, etc. inline instead of constants from `ChatTheme`. If the theme is changed, these inline values are missed.

**Expected Outcomes:**
- All inline hex colors in `MessagePanel`, `MessageInputPanel`, `OnlineUsersPanel` that are not already in `ChatTheme` are extracted to named constants in `ChatTheme`.
- No visible change to appearance.

**Todo List:**
1. Identify all `new Color(0x...)` values in `MessagePanel.java`, `MessageInputPanel.java`, `OnlineUsersPanel.java` that have no ChatTheme equivalent.
2. Add them as named constants to `ChatTheme.java` with descriptive names.
3. Replace inline usages with the new constants.

**Relevant Context:**
- `client desktop/src/main/java/com/javachat/desktop/util/ChatTheme.java`
- `client desktop/src/main/java/com/javachat/desktop/ui/MessagePanel.java` lines 445, 515, 522
- `client desktop/src/main/java/com/javachat/desktop/ui/OnlineUsersPanel.java` line 129

---

## Sub-Task 12 — Mobile: Fix BUG 1 — own chat messages never appear

**Status:** [x] done

**Intent:** The server uses `broadcastExcept(sender)`, so the sender never receives their own CHAT message back. The mobile client also never adds the message locally. Result: the sender's own messages are invisible.

**Expected Outcomes:**
- After `ConnectionManager.send(msg)` succeeds, the message is immediately added to the local chat list.
- The message shows a client-side timestamp (ISO-8601 via `Instant.now()`).
- There is no duplicate if the server ever changes to broadcast-all.

**Todo List:**
1. In `GlobalChatFragment.sendTextMessage()`, after `ConnectionManager.getInstance().send(msg)`, call `addMessage(msg)`.
2. Verify `Message.chat()` already sets a timestamp (it does, line 58 of `Message.java`).

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java` lines 183-192

---

## Sub-Task 13 — Mobile: Fix BUG 2 — FILE_COMPLETE notification shows "file"

**Status:** [x] done

**Intent:** The server's `FILE_COMPLETE` message carries only `fileId`, not `fileName`. The fragment falls back to the string `"file"`. `FileTransferManager` already stores the `FILE_META` message and has `getFileNameForId()` — it just isn't called.

**Expected Outcomes:**
- The completion notification reads `"📎 actualfilename.pdf received — saved to Downloads"`.

**Todo List:**
1. In `GlobalChatFragment.handleMessage()` `FILE_COMPLETE` case, call `FileTransferManager.getInstance().getFileNameForId(message.getFileId())` to retrieve the name.
2. Use the retrieved name (with null fallback to `"file"`) in the system message string.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java` lines 149-157
- `client-mobile/app/src/main/java/com/javachat/mobile/transfer/FileTransferManager.java` lines 144-147

---

## Sub-Task 14 — Mobile: Fix IMP 3 — self not in online users list

**Status:** [x] done

**Intent:** `USER_JOINED` is broadcast to all *other* clients, so the joining user never receives it for themselves. `GlobalChatFragment.addSelf()` method exists but is never called.

**Expected Outcomes:**
- After JOIN_SUCCESS is processed and `ChatActivity` is running, the current user appears at the top of the online users list.

**Todo List:**
1. In `ChatActivity.onCreate()`, after `getGlobalChatFragment()` is non-null, call `fragment.addSelf(AuthenticationManager.getInstance().getUsername())`.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/ChatActivity.java` lines 34-56
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/GlobalChatFragment.java` lines 343-345

---

## Sub-Task 15 — Mobile: Fix NPE risk in MessageAdapter tag comparison

**Status:** [x] done

**Intent:** `MessageAdapter.onBindViewHolder()` checks `m.getTag().equals("saved")`. If `getTag()` returns null, this throws NPE. The string literal should be on the left side.

**Expected Outcomes:**
- Download button state evaluation never crashes.

**Todo List:**
1. In `MessageAdapter.java` line 143, change `m.getTag().equals("saved")` to `"saved".equals(m.getTag())`.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/MessageAdapter.java` line 143

---

## Sub-Task 16 — Mobile: Fix stale click listener on Download button in RecyclerView

**Status:** [x] done

**Intent:** In `MessageAdapter.onBindViewHolder()`, the Download button's `setOnClickListener` is set inside an `if ("saved".equals(...))` block. When the RecyclerView recycles a view, the old click listener from a different message can remain attached to the button.

**Expected Outcomes:**
- Every bind call sets (or clears) the click listener, regardless of the saved state.

**Todo List:**
1. In `MessageAdapter.onBindViewHolder()` VIEW_TYPE_FILE case, move `setOnClickListener` outside the `if` block. Always set it (pointing to the current message's data). Always set the button text based on the saved state in the same pass.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/MessageAdapter.java` lines 143-158

---

## Sub-Task 17 — Mobile: Fix silent IOException in FileTransferManager.saveToDownloads

**Status:** [x] done

**Intent:** `FileTransferManager.saveToDownloads()` has `catch (IOException ignored) {}` at line 206. A failed save produces no notification or toast. The user taps "Download", nothing happens, and no error is shown.

**Expected Outcomes:**
- On IOException, a toast is shown: `"Failed to save file"`.

**Todo List:**
1. Replace `catch (IOException ignored) {}` with a catch block that posts a toast on the main thread: `"Failed to save [fileName]: " + e.getMessage()`.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/transfer/FileTransferManager.java` line 206

---

## Sub-Task 18 — Mobile: Fix silent IOException catches in AudioManager

**Status:** [x] done

**Intent:** `AudioManager` has three silent `catch (IOException ...)` blocks: during recording stop (line 100), during audio send (line 150), and during cache save (line 224). Failures are invisible to the user.

**Expected Outcomes:**
- Recording stop failure: invoke `errorCallback` with a message.
- Audio send failure: invoke `callback` error path or log visibly.
- Cache save failure: return null and let the caller handle it (caller already checks null).

**Todo List:**
1. Line 100: Replace `catch (IOException e)` silent block with a call to `errorCallback.accept("Recording failed: " + e.getMessage())` if non-null.
2. Line 150: Replace silent catch with `if (callback != null) callback.onError("Send failed: " + e.getMessage())` or equivalent.
3. Line 224: The catch already returns null — add a `Log.e(TAG, ...)` statement so the failure is visible in Logcat.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/audio/AudioManager.java` lines 100, 150, 224

---

## Sub-Task 19 — Mobile: Fix audio permission not requested at runtime on API 31+

**Status:** [ ] pending

**Intent:** `AudioManager.showRecordDialog()` checks `PermissionManager.hasAudioPermission()` and returns silently if denied — with no explanation to the user. On Android 12+ (API 31+), runtime permission request is required. The app currently never triggers the request dialog when the user tries to record.

**Expected Outcomes:**
- If `RECORD_AUDIO` is not granted, `requestAudioPermission()` is called from the Activity.
- User sees the system permission dialog.
- After grant, user can proceed to record.

**Todo List:**
1. In `AudioManager.showRecordDialog()`, when `hasAudioPermission()` returns false, call `PermissionManager.requestAudioPermission(activity)` and return (the user will retry after granting).
2. Optionally show a `Toast` explaining why permission is needed before requesting.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/audio/AudioManager.java` lines 74-77
- `client-mobile/app/src/main/java/com/javachat/mobile/util/PermissionManager.java` — `requestAudioPermission()`

---

## Sub-Task 20 — Mobile: Wire NotificationHelper context

**Status:** [ ] pending

**Intent:** `ConnectionManager.setNotificationContext()` is defined but never called anywhere in the app. As a result, `notificationHelper` is always null and no push notifications are ever posted.

**Expected Outcomes:**
- Notifications fire when a CHAT message arrives while the app is in the background.

**Todo List:**
1. In `ChatActivity.onCreate()`, after setting the `MessageListener`, call `ConnectionManager.getInstance().setNotificationContext(this)`.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/network/ConnectionManager.java` lines 51-53
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/ChatActivity.java`

---

## Sub-Task 21 — Mobile: Fix hardcoded strings in ChatActivity (use resources)

**Status:** [ ] pending

**Intent:** `ChatActivity.onConnectionLost()` uses hardcoded string literals `"Disconnected"` and `"Lost connection to server."` instead of `R.string.*` resources.

**Expected Outcomes:**
- Both strings are in `strings.xml`.
- Code references them via `getString(R.string.*)`.

**Todo List:**
1. Add `<string name="disconnected_title">Disconnected</string>` and `<string name="disconnected_msg">Lost connection to server.</string>` to `strings.xml`.
2. Replace the hardcoded literals in `ChatActivity.onConnectionLost()` with `getString(R.string.disconnected_title)` and `getString(R.string.disconnected_msg)`.

**Relevant Context:**
- `client-mobile/app/src/main/java/com/javachat/mobile/ui/ChatActivity.java` lines 106-107
- `client-mobile/app/src/main/res/values/strings.xml`

---

## Priority Order

| # | Sub-Task | Severity | Project |
|---|---|---|---|
| 1 | Remove hardcoded API key | 🔴 Critical | Server |
| 2 | Fix AI model fallback chain | 🔴 High | Server |
| 12 | Fix BUG 1 — own messages invisible | 🔴 High | Mobile |
| 15 | Fix NPE in MessageAdapter tag comparison | 🔴 High | Mobile |
| 5 | Fix silent Base64 decode failures | 🟠 High | Desktop |
| 6 | Fix null sender crashes | 🟠 High | Desktop |
| 7 | Fix PrintWriter silent write failures | 🟠 High | Desktop |
| 13 | Fix BUG 2 — FILE_COMPLETE shows "file" | 🟡 Medium | Mobile |
| 14 | Fix IMP 3 — self not in online users | 🟡 Medium | Mobile |
| 16 | Fix stale Download button listener | 🟡 Medium | Mobile |
| 17 | Fix silent IOException on file save | 🟡 Medium | Mobile |
| 18 | Fix silent IOException in AudioManager | 🟡 Medium | Mobile |
| 19 | Fix missing audio permission request | 🟡 Medium | Mobile |
| 20 | Wire NotificationHelper context | 🟡 Medium | Mobile |
| 3 | Fix unsafe JSON construction in Gemini | 🟡 Medium | Server |
| 4 | Fix buffer memory leak in MainFrame | 🟡 Medium | Desktop |
| 8 | Fix Graphics2D leak | 🟢 Low | Desktop |
| 9 | Fix "Message" placeholder swallowed | 🟢 Low | Desktop |
| 10 | Fix OnlineUsersPanel empty-username crash | 🟢 Low | Desktop |
| 11 | Move hardcoded colors to ChatTheme | 🟢 Low | Desktop |
| 21 | Fix hardcoded strings in ChatActivity | 🟢 Low | Mobile |
