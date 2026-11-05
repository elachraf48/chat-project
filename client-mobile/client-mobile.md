# JavaChat Mobile Client – Development Roadmap

## 1. Overview

The Mobile Client is an Android application developed using Java and the Android SDK.

It allows users to connect to the centralized JavaChat Server and communicate in real time with users connected through both desktop and mobile platforms.

The application uses one shared global chat room.

### Main objectives

- Connect to the JavaChat Server using TCP sockets.
- Authenticate users.
- Send and receive real-time text messages.
- Share files and documents.
- Record, send, receive and play audio messages.
- Display online users.
- Interact with the Gemini AI assistant.

## 2. Project Structure

```text
client-mobile/
│
├── README.md
├── build.gradle
├── settings.gradle
│
└── app/
    ├── build.gradle
    │
    └── src/
        └── main/
            ├── AndroidManifest.xml
            │
            ├── java/
            │   └── com/javachat/mobile/
            │       │
            │       ├── MainActivity.java
            │       │
            │       ├── config/
            │       │   └── ClientConfig.java
            │       │
            │       ├── network/
            │       │   ├── SocketClient.java
            │       │   ├── ConnectionManager.java
            │       │   └── MessageListener.java
            │       │
            │       ├── protocol/
            │       │   ├── Message.java
            │       │   ├── MessageType.java
            │       │   └── ProtocolManager.java
            │       │
            │       ├── auth/
            │       │   ├── LoginActivity.java
            │       │   └── AuthenticationManager.java
            │       │
            │       ├── ui/
            │       │   ├── ChatActivity.java
            │       │   ├── GlobalChatFragment.java
            │       │   ├── MessageAdapter.java
            │       │   ├── OnlineUsersAdapter.java
            │       │   └── MessageInputController.java
            │       │
            │       ├── transfer/
            │       │   └── FileTransferManager.java
            │       │
            │       ├── audio/
            │       │   ├── AudioRecorder.java
            │       │   ├── AudioPlayer.java
            │       │   └── AudioManager.java
            │       │
            │       ├── ai/
            │       │   └── GeminiChatManager.java
            │       │
            │       └── util/
            │           └── PermissionManager.java
            │
            └── res/
                ├── layout/
                ├── drawable/
                ├── mipmap/
                ├── values/
                └── xml/
```

## 3. Development Roadmap

### Phase 1 – Android Project Setup

**Goal:** Create the Android application.

Tasks:
- Install and configure Android Studio.
- Configure Java development.
- Create an Android project.
- Configure Gradle.
- Configure minimum and target Android SDK versions.
- Create package structure.
- Configure Internet permission.
- Create the first Activity.
- Run the application on an emulator.

Expected result: A working Android application.

### Phase 2 – Mobile User Interface

**Goal:** Design the application interface.

Tasks:
- Create the login screen.
- Create the main chat screen.
- Create the global conversation interface.
- Display online users.
- Create the message input area.
- Add attachment button.
- Add audio recording button.
- Add Gemini assistant interface.
- Support different screen sizes.

Expected result: A complete mobile interface.

### Phase 3 – TCP Socket Connection

**Goal:** Connect Android to the JavaChat Server.

Tasks:
- Implement `SocketClient`.
- Configure server IP and port.
- Establish TCP connections.
- Implement message sending and receiving.
- Handle connection errors.
- Manage network disconnections.
- Handle Android Activity lifecycle.
- Ensure network operations run outside the main UI thread.

Expected result: Android connects successfully to the central server.

### Phase 4 – Communication Protocol

**Goal:** Implement the same protocol used by the desktop client.

Tasks:
- Integrate Jackson or another compatible JSON library.
- Implement JSON serialization.
- Implement JSON deserialization.
- Implement TCP message framing.
- Handle server responses.
- Process different message types.
- Handle protocol errors.

Expected result: Android and desktop clients exchange compatible messages.

### Phase 5 – Authentication

**Goal:** Allow users to authenticate.

Tasks:
- Create login interface.
- Send login requests.
- Receive authentication responses.
- Validate login results.
- Manage authenticated sessions.
- Handle invalid credentials.
- Open the chat interface after successful authentication.

Expected result: Authenticated users can access the global chat room.

### Phase 6 – Global Chat

**Goal:** Implement real-time communication.

Tasks:
- Send text messages.
- Receive messages from all connected users.
- Display sender names.
- Display message timestamps.
- Update online users.
- Display connection notifications.
- Update the interface when new messages arrive.
- Handle messages while the application is in the foreground.

Expected result: Android users communicate with desktop and other Android users in the same global room.

### Phase 7 – File Sharing

**Goal:** Allow users to exchange files.

Tasks:
- Implement Android file selection using Storage Access Framework.
- Select documents and images.
- Send file metadata.
- Transfer binary data in chunks.
- Receive files.
- Display transfer progress.
- Handle interrupted transfers.
- Validate file sizes and types.
- Save received files using appropriate Android storage APIs.

Expected result: Android users can exchange files with desktop and mobile users.

### Phase 8 – Audio Messaging

**Goal:** Implement voice messages.

Tasks:
- Request microphone permission when needed.
- Implement audio recording.
- Add recording controls.
- Stop and preview recordings.
- Encode audio in a format supported by desktop and Android.
- Send audio data through the server.
- Receive audio messages.
- Implement audio playback.
- Display audio message controls.

Expected result: Android and desktop users can exchange compatible audio messages.

### Phase 9 – Gemini AI Integration

**Goal:** Allow Android users to interact with Gemini.

Tasks:
- Create AI assistant interface.
- Send AI requests through the JavaChat Server.
- Receive Gemini responses.
- Display AI responses.
- Handle loading states.
- Handle API errors.

Important: The Gemini API key must remain on the server. Never include it in the Android application.

Expected result: Android users can interact with Gemini through the central server.

### Phase 10 – Background Behavior and Notifications

**Goal:** Improve the mobile experience.

Tasks:
- Handle application lifecycle correctly.
- Manage socket disconnections.
- Implement reconnection when appropriate.
- Evaluate Android background execution restrictions.
- Implement notifications for incoming messages when the app is not active.
- Use an appropriate background communication strategy.

Important: A permanent TCP socket is not guaranteed to remain alive when Android puts the application in the background. For reliable notifications, evaluate Firebase Cloud Messaging (FCM) with server-side notification delivery.

Expected result: Users can receive appropriate notifications and reconnect reliably.

### Phase 11 – Security and Testing

**Goal:** Make the Android application reliable and secure.

Tasks:
- Use TLS for production communication.
- Protect authentication credentials and session tokens.
- Validate incoming data.
- Handle network failures.
- Test multiple simultaneous users.
- Test desktop-to-mobile communication.
- Test mobile-to-mobile communication.
- Test file and audio transfers.
- Test different Android versions.
- Test permissions and lifecycle behavior.

Expected result: A stable Android application.

### Phase 12 – Build and Release

**Goal:** Prepare the application for distribution.

Tasks:
- Configure application icon.
- Configure application name and version.
- Generate signed APK or Android App Bundle.
- Test release build.
- Configure production server address.
- Prepare installation and deployment documentation.

Expected result: Android application ready for distribution.

## 4. Technologies

| Technology | Purpose |
|---|---|
| Java | Programming language |
| Android SDK | Mobile development |
| Android Studio | IDE |
| Gradle | Build management |
| TCP Sockets | Server communication |
| Jackson | JSON processing |
| Storage Access Framework | File selection |
| MediaRecorder | Audio recording |
| MediaPlayer | Audio playback |
| JUnit | Unit testing |
| Firebase Cloud Messaging | Push notifications |

## 5. Important Android Permissions

Permissions should be requested only when the corresponding feature is used.

| Permission / Capability | Purpose |
|---|---|
| INTERNET | Network communication |
| RECORD_AUDIO | Record voice messages |
| Notifications | Notify users about incoming messages |
| File access | Use Android file selection and storage APIs |

Android storage access should preferably use the Storage Access Framework instead of requesting broad storage permissions.

## 6. Integration with Other Components

```text
                  JAVA CHAT SERVER
                         |
              Shared Global Chat Room
                         |
             +-----------+-----------+
             |                       |
      DESKTOP CLIENT           MOBILE CLIENT
             |                       |
          Java Swing             Java Android
             |                       |
         TCP Socket               TCP Socket
             |                       |
       Desktop Users            Mobile Users
```

All clients must use the same:
- Authentication protocol.
- Message format.
- TCP framing mechanism.
- File transfer protocol.
- Audio format and transfer protocol.

## 7. Development Checkpoints

| Phase | Expected result |
|---|---|
| 1 | Android project starts |
| 2 | Mobile interface works |
| 3 | TCP connection works |
| 4 | JSON communication works |
| 5 | Authentication works |
| 6 | Global chat works |
| 7 | File sharing works |
| 8 | Audio messaging works |
| 9 | Gemini integration works |
| 10 | Background notifications work |
| 11 | Security and testing completed |
| 12 | APK/AAB generated |

## 8. Important Development Rules

1. One global chat room only.
2. No private messaging or user-created groups.
3. No permanent chat history.
4. Follow the exact JavaChat Server protocol.
5. Keep network operations off the Android main thread.
6. Handle Activity and application lifecycle correctly.
7. Do not expose the Gemini API key.
8. Transfer files and audio as binary streams.
9. Use compatible audio formats across desktop and mobile.
10. Request Android permissions only when required.
11. Use TLS in production.

## 9. Final Objective

Build an Android application that connects to the centralized JavaChat Server and allows mobile users to communicate in real time with desktop and mobile users.

**One server. One global chat room. Multiple platforms. Text, files, audio and AI assistance.**