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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
        serverSocket = new ServerSocket(config.getPort(), 256);
        executor = new ThreadPoolExecutor(
                10, 500,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        Logger.info("Server listening on port " + config.getPort());

        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                Logger.info("[CONNECT] " + socket.getRemoteSocketAddress());
                try {
                    executor.submit(new ClientHandler(
                            socket,
                            clientManager,
                            userService,
                            globalChatRoom,
                            fileTransferManager,
                            audioTransferManager,
                            geminiService
                    ));
                } catch (RejectedExecutionException e) {
                    // Executor is shutting down; close the accepted socket immediately
                    Logger.warn("[CONNECT] Rejected — server is shutting down.");
                    try { socket.close(); } catch (IOException ignored) {}
                }
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
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    Logger.warn("[Server] Executor did not terminate in 30s — forcing shutdown.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Logger.warn("[Server] Interrupted during executor shutdown — forcing shutdown.");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
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
