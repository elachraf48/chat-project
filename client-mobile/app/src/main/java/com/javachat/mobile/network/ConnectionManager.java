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
    // sendExecutor is single-threaded so all outgoing messages are sent in submission order.
    // A CachedThreadPool would allow AUDIO_COMPLETE to overtake AUDIO_META on separate threads.
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    // connectExecutor is separate so connect/reader-loop tasks don't block behind queued sends.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** BUG 7: guard against concurrent connect calls. */
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private MessageListener listener;
    private NotificationHelper notificationHelper;

    /**
     * Pending USER_LIST message — saved when it arrives while LoginActivity is
     * the listener (LoginActivity ignores it). Replayed immediately when the next
     * setListener() call registers a new listener (ChatActivity), so the online
     * users list is always populated regardless of Activity transition timing.
     * Cleared once replayed or on disconnect.
     */
    private volatile Message pendingUserList;

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) instance = new ConnectionManager();
        return instance;
    }

    public void setListener(MessageListener listener) {
        this.listener = listener;
        // Replay a pending USER_LIST that arrived during the LoginActivity→ChatActivity
        // transition gap and was ignored by LoginActivity.
        if (listener != null && pendingUserList != null) {
            final Message pending = pendingUserList;
            pendingUserList = null;
            mainHandler.post(() -> {
                if (this.listener != null) {
                    this.listener.onMessageReceived(pending);
                }
            });
        }
    }

    public MessageListener getListener() {
        return listener;
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
                // Fix A: reset flag now — "connecting" means the TCP handshake phase only.
                // Without this, returning to LoginActivity and tapping Connect again is
                // silently dropped by the compareAndSet guard, causing a stuck spinner.
                connecting.set(false);
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
        sendExecutor.submit(() -> {
            try {
                socketClient.send(message);
            } catch (IOException e) {
                notifyConnectionLost(e.getMessage());
            }
        });
    }

    /**
     * DEV: fire-and-forget error report to the server.
     * Sends an ERROR message containing the full text (crash trace, ANR info, etc.)
     * so it appears in the server log as [CLIENT_ERROR]. Never throws or calls
     * notifyConnectionLost — used inside crash/exception handlers where the
     * connection may already be dying.
     */
    public void sendErrorReport(String errorText) {
        if (errorText == null || errorText.isEmpty()) return;
        executor.submit(() -> {
            try {
                if (socketClient.isConnected()) {
                    Message report = new Message();
                    report.setType(MessageType.ERROR);
                    report.setText(errorText);
                    socketClient.send(report);
                }
            } catch (Exception ignored) {
                // Best-effort only — never propagate from an error reporter
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
        pendingUserList = null;
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

        // USER_LIST: always cache it. If LoginActivity is the current listener it will
        // ignore the message, but setListener() will replay this cached copy to
        // ChatActivity the moment it registers — so the online list is never lost.
        if (msg.getType() == MessageType.USER_LIST) {
            pendingUserList = msg;
        }

        if (msg.getType() == MessageType.CHAT && notificationHelper != null) {
            notificationHelper.showMessageNotification(msg.getSender(), msg.getText());
        }
        if (listener != null) {
            final MessageListener l = listener;
            mainHandler.post(() -> {
                try {
                    l.onMessageReceived(msg);
                } catch (Exception e) {
                    // DEV: a UI-thread crash inside the listener would otherwise
                    // silently kill the Activity. Re-throw so the global handler
                    // in JavaChatApplication shows the full stacktrace on screen.
                    Log.e(TAG, "Listener threw on main thread for " + msg.getType(), e);
                    throw new RuntimeException(
                            "[DEV] UI crash handling message type " + msg.getType(), e);
                }
            });
        }
    }

    private void notifyConnectionLost(String reason) {
        connecting.set(false);
        pendingUserList = null;   // stale on reconnect
        socketClient.disconnect();
        if (listener != null) {
            mainHandler.post(() -> listener.onConnectionLost(reason));
        }
    }

    public interface ConnectionFailureCallback {
        void onFailure(String reason);
    }
}
