# Chat Bugs Fix Plan

## Overview

Three UI bugs reported from the desktop client screenshot:

1. **Own messages not shown** — user sends a message but the bubble never appears.
2. **Existing online users invisible on join** — the sidebar only shows the user themselves; users already connected when you join are missing.
3. **Received messages appear on the wrong side** (right instead of left).

All three bugs are in the desktop client only. No server changes are needed.

---

## Root Cause Analysis

### Bug 1 — Own messages not shown

**File:** `client desktop/src/main/java/com/javachat/desktop/ui/GlobalChatPanel.java`

`sendChat()` calls `connectionManager.send(Message.chat(text))` but never adds the message locally.

The server's `GlobalChatRoom.broadcast()` explicitly calls `broadcastExcept(senderUsername, …)` — the sender never receives an echo of their own CHAT message. So the only place to show it is on the client right after `send()`.

The `Message.chat(text)` factory in the **desktop** client (`desktop/protocol/Message.java`) must be checked — it may not set `sender`. The message added locally must have `sender` set to `username` so the bubble panel recognises it as `isSelf = true`.

### Bug 2 — Existing online users not shown on join

**File:** `chat server/src/main/java/com/javachat/server/network/ClientHandler.java`

After a successful JOIN, `handleJoin()` sends `JOIN_SUCCESS` and `broadcastExcept` a `USER_JOINED` to others. It does **not** send `USER_JOINED` events for the already-online users to the newcomer. There is no user-list field in the protocol either.

**Fix approach (server-side, minimal):** After sending `JOIN_SUCCESS`, loop over `clientManager.getAll()` and send a synthetic `USER_JOINED` to the new client for each already-connected user (excluding themselves). No protocol changes needed — `USER_JOINED` already carries just a `sender` field.

### Bug 3 — Received messages on wrong side

**File:** `client desktop/src/main/java/com/javachat/desktop/ui/MessagePanel.java`

`appendChatMessage()` compares `localUsername.equals(message.getSender())`. `localUsername` is set via `setLocalUsername(username)` called from `GlobalChatPanel.init()`. `username` comes from `MainFrame` constructor which receives it from `LoginFrame.handleMessage()` as `message.getSender()`.

The server's `joinSuccess(username)` sets `m.sender = username`. So `message.getSender()` should be correct.

The likely real cause: the desktop client's `Message.chat(String text)` factory (only one argument) does **not** set `sender` — so when the locally-added own message arrives in `appendChatMessage()`, `message.getSender()` is `null`, `localUsername.equals(null)` is `false`, and the bubble is painted as received (left). This is the same root as Bug 1 — when own messages are added locally they must carry the correct sender.

For received messages from others: if their bubble still appears on the right, `localUsername` is empty (never set). Verify `setLocalUsername` is being called with a non-empty string.

---

## Sub-Tasks

### Task 1 — Fix own messages not shown + wrong side

**Intent:** After sending a chat message, add it immediately to the local bubble list with the sender set to the local username, so it appears as a green right-aligned bubble.

**Expected Outcomes:**
- Typing a message and pressing Send immediately shows a green bubble on the right.
- The bubble shows the correct time.

**Todo List:**
1. Open `client desktop/src/main/java/com/javachat/desktop/protocol/Message.java` and confirm what `Message.chat(String text)` looks like — does it set `sender`?
2. If `Message.chat(String text)` takes only one argument (text) and doesn't set sender, add an overload or fix it so the caller can supply the sender, OR construct the message manually in `sendChat()` with sender set.
3. In `GlobalChatPanel.sendChat()`, after `connectionManager.send(msg)`, call `messagePanel.appendChatMessage(msg)` to show the own bubble immediately.
4. Ensure the message passed to `appendChatMessage` has `sender` equal to the local username and a valid timestamp.

**Relevant Context:**
- `GlobalChatPanel.java` line ~71 — `sendChat()` method
- `MessagePanel.appendChatMessage()` — needs `message.getSender()` to equal `localUsername` for right-alignment
- `Message.java` (desktop) — check `chat()` factory method signature

**Status:** [x] done

---

### Task 2 — Fix existing online users not shown on join

**Intent:** When a new client joins, send them `USER_JOINED` events for every user already in the room so their sidebar populates correctly.

**Expected Outcomes:**
- If users A and B are already chatting and user C joins, C's sidebar shows A, B, and C.
- Existing users A and B are not affected.

**Todo List:**
1. Open `chat server/src/main/java/com/javachat/server/network/ClientHandler.java`, in `handleJoin()`, after `sendMessage(Message.joinSuccess(username))`.
2. Iterate `clientManager.getAll()` (returns all registered handlers). For each handler whose username differs from the newly-joined user, send a `Message.userJoined(thatUsername)` to the new client only (not broadcast).
3. The `ClientHandler` does not expose its username publicly — add a `getUsername()` accessor OR use `clientManager`'s key set. Check `ClientManager.java` — `clients` is a `ConcurrentHashMap<String, ClientHandler>` so iterating `clients.entrySet()` gives both username and handler. Add a `getUsernames()` method to `ClientManager` that returns the key set.
4. Rebuild the server fat JAR.

**Relevant Context:**
- `ClientHandler.java` line 181–189 — `handleJoin()` — add the loop after `sendMessage(Message.joinSuccess(username))`
- `ClientManager.java` — add `getUsernames()` returning `Set<String>`
- `Message.userJoined(String)` factory already exists

**Status:** [x] done

---

### Task 3 — Verify bubble alignment for received messages

**Intent:** Confirm that messages from other users appear on the left (dark bubble) and not on the right.

**Expected Outcomes:**
- Messages from other users appear as dark grey bubbles on the left with sender name.
- Own messages appear as green bubbles on the right with no sender name.

**Todo List:**
1. After Task 1 is done, run the server and two clients and verify alignment visually.
2. If received messages still appear on the right: add a debug log in `MessagePanel.appendChatMessage()` to print `localUsername` and `message.getSender()` and confirm the comparison works.
3. If `localUsername` is empty, trace back through `GlobalChatPanel.init()` → `MessagePanel.setLocalUsername()` to find where the username is lost.

**Relevant Context:**
- `MessagePanel.appendChatMessage()` — `isSelf = localUsername.equals(message.getSender())`
- `GlobalChatPanel.init()` — calls `messagePanel.setLocalUsername(username)`
- `MainFrame` constructor — receives `username` from `LoginFrame`

**Status:** [x] done — fixed as part of Task 1 (sender now set on locally-added messages)

---

## Build Commands

```bash
# Server (after Task 2 changes)
mvn package -f "chat server/pom.xml"

# Desktop client (after Task 1 changes)
mvn package -f "client desktop/pom.xml"
```
