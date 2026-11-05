package com.javachat.mobile.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.javachat.mobile.config.ClientConfig;
import com.javachat.mobile.protocol.Message;
import com.javachat.mobile.protocol.MessageType;
import com.javachat.mobile.util.InputValidator;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-level façade over {@link SocketClient}.
 *
 * Fixes applied:
 *  - BUG 7: AtomicBoolean connecting flag prevents double-connect / two reader loops.
 */
public class ConnectionManager {

    private static final String TAG = "JavaChat.CM";

    private static ConnectionManager instance;

    private final SocketClient socketClient = new SocketClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** BUG 7: guard against concurrent connect calls. */
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private MessageListener listener;
    private NotificationHelper notificationHelper;

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) instance = new ConnectionManager();
        return instance;
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public void setNotificationContext(Context context) {
        this.notificationHelper = new NotificationHelper(context);
    }

    // -------------------------------------------------------------------------
    // Connect
    // -------------------------------------------------------------------------

    public void connect(String host, int port,
                        Runnable onSuccess,
                        ConnectionFailureCallback onFailure) {
        // BUG 7: reject if already connecting or connected
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
        executor.submit(() -> {
            try {
                Log.d(TAG, "Connecting to " + host + ":" + port);
                socketClient.connect(host, port, ClientConfig.CONNECT_TIMEOUT_MS);
                Log.d(TAG, "Connected — sending JOIN");
                onSuccess.run();
                Log.d(TAG, "JOIN queued — starting reader loop");
                startReaderLoop();
            } catch (IOException e) {
                Log.e(TAG, "Connect failed: " + e.getMessage(), e);
                connecting.set(false);
                mainHandler.post(() -> onFailure.onFailure(e.getMessage()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Send
    // -------------------------------------------------------------------------

    public void send(Message message) {
        executor.submit(() -> {
            try {
                socketClient.send(message);
            } catch (IOException e) {
                notifyConnectionLost(e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    public boolean isConnected() {
        return socketClient.isConnected();
    }

    public void disconnect() {
        connecting.set(false);
        socketClient.disconnect();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void startReaderLoop() {
        executor.submit(() -> {
            Log.d(TAG, "Reader loop started");
            while (socketClient.isConnected()) {
                try {
                    Message msg = socketClient.receive();
                    Log.d(TAG, "Received: " + (msg != null ? msg.getType() : "null"));
                    dispatchMessage(msg);
                } catch (IOException e) {
                    Log.e(TAG, "Reader loop IOException: " + e.getMessage(), e);
                    if (socketClient.isConnected()) {
                        notifyConnectionLost(e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    // Catch any unexpected runtime exception so we can log it
                    Log.e(TAG, "Reader loop unexpected exception: " + e.getMessage(), e);
                    notifyConnectionLost(e.getMessage());
                    break;
                }
            }
            Log.d(TAG, "Reader loop ended");
            connecting.set(false);
        });
    }

    private void dispatchMessage(Message msg) {
        if (!InputValidator.isValidIncoming(msg)) {
            Log.w(TAG, "Rejected invalid message: " + (msg != null ? msg.getType() : "null"));
            return;
        }
        Log.d(TAG, "Dispatching " + msg.getType()
                + " to " + (listener != null ? listener.getClass().getSimpleName() : "null"));

        if (msg.getType() == MessageType.CHAT && notificationHelper != null) {
            notificationHelper.showMessageNotification(msg.getSender(), msg.getText());
        }
        if (listener != null) {
            mainHandler.post(() -> listener.onMessageReceived(msg));
        }
    }

    private void notifyConnectionLost(String reason) {
        connecting.set(false);
        socketClient.disconnect();
        if (listener != null) {
            mainHandler.post(() -> listener.onConnectionLost(reason));
        }
    }

    public interface ConnectionFailureCallback {
        void onFailure(String reason);
    }
}
