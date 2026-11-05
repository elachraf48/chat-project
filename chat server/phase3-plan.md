# Phase 3 – Multiple Client Management

## Overview

Upgrade the server from handling one client sequentially to handling many clients concurrently. `ChatServer` runs a continuous accept loop. Each accepted connection is handed off to a thread from an `ExecutorService`, so the accept loop is never blocked by a client. `ClientManager` tracks every connected handler so the server knows who is online and can remove them on disconnect. `ClientHandler` is upgraded to keep reading lines in a loop until the client disconnects. `Main` launches the server and blocks until it is stopped.

This is the Phase 3 checkpoint: _"Multiple clients connect."_

This implements Phase 3 from [`chat server.md`](chat server.md).

Note: The subagent also implemented large portions of Phases 4, 5, and 6 (JSON protocol, UserService, broadcast) as part of this phase. See notes in each sub-task.

---

## Sub-Task 1 — Implement `ClientManager`

**Status:** [x] done

`ClientManager` uses a `ConcurrentHashMap<String, ClientHandler>` keyed by username (not a list). Provides `register`, `deregister`, `isUsernameTaken`, `getAll`, `count`, `broadcast`, and `broadcastExcept`.

---

## Sub-Task 2 — Upgrade `ClientHandler` to a continuous read loop

**Status:** [x] done

`ClientHandler implements Runnable`. Constructor takes `(Socket, ClientManager, UserService, GlobalChatRoom)`. `run()` reads lines in a loop, dispatches to `handleLine()` which parses JSON via `ProtocolManager` and routes on `MessageType`. Registers/deregisters via `UserService` on connect/disconnect.

---

## Sub-Task 3 — Upgrade `ChatServer` to a continuous accept loop with `ExecutorService`

**Status:** [x] done

`ChatServer` uses `Executors.newCachedThreadPool()`. `start()` is `void` and blocks in a `while (!serverSocket.isClosed())` accept loop. Each accepted socket is wrapped in a `ClientHandler` and submitted to the executor. `stop()` calls `executor.shutdown()` then closes the `ServerSocket`.

---

## Sub-Task 4 — Update `Main` and verify with multiple clients

**Status:** [x] done

`Main` creates `ClientManager`, `ChatServer(config, clientManager)`, registers a JVM shutdown hook, and calls `chatServer.start()`. Compilation verified: **BUILD SUCCESS, 14 files, zero errors** (JDK 21, Maven from IntelliJ bundle).

---

## Additional work done ahead of schedule

The subagent also implemented the following (covering Phases 4, 5, and 6):

- **`Message.java`** — JSON DTO with static factories: `chat`, `joinSuccess`, `joinError`, `userJoined`, `userLeft`, `error`.
- **`MessageType.java`** — Enum: `JOIN`, `JOIN_SUCCESS`, `JOIN_ERROR`, `CHAT`, `USER_JOINED`, `USER_LEFT`, `ERROR`, plus placeholder types for file/audio/AI.
- **`ProtocolManager.java`** — Jackson `ObjectMapper` wrapper with `serialize(Message)` and `deserialize(String)`.
- **`UserService.java`** — Thin auth service delegating to `ClientManager.register/deregister`.
- **`GlobalChatRoom.java`** — `broadcast(senderUsername, text)` creates a `CHAT` message and delegates to `ClientManager.broadcast`.
