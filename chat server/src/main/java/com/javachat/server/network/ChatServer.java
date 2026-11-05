package com.javachat.server.network;

import com.javachat.server.ai.GeminiService;
import com.javachat.server.auth.UserService;
import com.javachat.server.chat.GlobalChatRoom;
import com.javachat.server.config.ServerConfig;
import com.javachat.server.transfer.AudioTransferManager;
import com.javachat.server.transfer.FileTransferManager;
import com.javachat.server.util.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

    private final ServerConfig config;
    private final ClientManager clientManager;
    private final UserService userService;
    private final GlobalChatRoom globalChatRoom;
    private final FileTransferManager fileTransferManager;
    private final AudioTransferManager audioTransferManager;
    private final GeminiService geminiService;

    private ServerSocket serverSocket;
    private ExecutorService executor;

    public ChatServer(ServerConfig config, ClientManager clientManager) {
        this.config = config;
        this.clientManager = clientManager;
        this.userService = new UserService(clientManager);
        this.globalChatRoom = new GlobalChatRoom(clientManager);
        this.fileTransferManager = new FileTransferManager(clientManager);
        this.audioTransferManager = new AudioTransferManager(clientManager);
        this.geminiService = new GeminiService();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getPort());
        executor = Executors.newCachedThreadPool();
        Logger.info("Server listening on port " + config.getPort());

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                Logger.info("[CONNECT] " + socket.getRemoteSocketAddress());
                executor.submit(new ClientHandler(
                        socket,
                        clientManager,
                        userService,
                        globalChatRoom,
                        fileTransferManager,
                        audioTransferManager,
                        geminiService
                ));
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    break;
                }
                Logger.error("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    public void stop() {
        if (executor != null) {
            executor.shutdown();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Logger.error("Error closing server socket: " + e.getMessage());
            }
        }
    }
}
