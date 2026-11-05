package com.javachat.mobile.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.javachat.mobile.R;
import com.javachat.mobile.config.ClientConfig;
import com.javachat.mobile.network.ConnectionManager;
import com.javachat.mobile.network.MessageListener;
import com.javachat.mobile.protocol.Message;
import com.javachat.mobile.protocol.MessageType;
import com.javachat.mobile.ui.ChatActivity;
import com.javachat.mobile.util.InputValidator;

/**
 * Login screen. Collects username, server IP, and port, then connects to
 * the server and sends a JOIN message. Navigates to {@link ChatActivity}
 * on JOIN_SUCCESS.
 *
 * Fixes applied:
 *  - BUG 2: disconnect any existing connection on entry so reconnect is clean.
 *  - BUG 9: validate username format before connecting (server allows [A-Za-z0-9_-]{1,32}).
 */
public class LoginActivity extends AppCompatActivity implements MessageListener {

    private EditText etUsername;
    private EditText etServerIp;
    private EditText etServerPort;
    private Button btnConnect;
    private ProgressBar progressBar;
    private TextView tvError;

    private String pendingUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // BUG 2: cleanly reset any existing connection.
        // disconnect() acquires a synchronized lock on SocketClient. If the reader loop
        // is blocked inside readLine(), the lock is held and calling disconnect() on the
        // main thread blocks indefinitely → ANR. Run it on a background thread.
        AuthenticationManager.getInstance().logout();
        new Thread(ConnectionManager.getInstance()::disconnect).start();

        etUsername   = findViewById(R.id.etUsername);
        etServerIp   = findViewById(R.id.etServerIp);
        etServerPort = findViewById(R.id.etServerPort);
        btnConnect   = findViewById(R.id.btnConnect);
        progressBar  = findViewById(R.id.progressBar);
        tvError      = findViewById(R.id.tvError);

        etServerIp.setText(ClientConfig.DEFAULT_SERVER_IP);
        etServerPort.setText(String.valueOf(ClientConfig.DEFAULT_SERVER_PORT));

        btnConnect.setOnClickListener(v -> onConnectClicked());
    }

    // -------------------------------------------------------------------------

    private void onConnectClicked() {
        String username = etUsername.getText().toString().trim();
        String host     = etServerIp.getText().toString().trim();
        String portText = etServerPort.getText().toString().trim();

        // BUG 9: client-side username format validation (mirrors server rules)
        if (!InputValidator.isValidUsername(username)) {
            if (username.isEmpty()) {
                showError(getString(R.string.error_empty_username));
            } else {
                showError(getString(R.string.error_invalid_username));
            }
            return;
        }
        if (host.isEmpty()) {
            showError(getString(R.string.error_empty_server));
            return;
        }
        if (!InputValidator.isValidPort(portText)) {
            showError(getString(R.string.error_invalid_port));
            return;
        }

        int port = Integer.parseInt(portText.trim());
        pendingUsername = username;          // keep field for onMessageReceived
        final String joinUsername = username; // effectively-final local for lambda (thread-safe)
        showLoading(true);

        ConnectionManager cm = ConnectionManager.getInstance();
        cm.setListener(this);
        cm.connect(host, port,
                () -> cm.send(Message.join(joinUsername)),
                reason -> {
                    showLoading(false);
                    showError(getString(R.string.error_connection));
                });
    }

    // -------------------------------------------------------------------------
    // MessageListener
    // -------------------------------------------------------------------------

    @Override
    public void onMessageReceived(Message message) {
        if (message.getType() == MessageType.JOIN_SUCCESS) {
            AuthenticationManager.getInstance().setLoggedIn(pendingUsername);
            openChat();
        } else if (message.getType() == MessageType.JOIN_ERROR) {
            showLoading(false);
            showError(message.getText() != null ? message.getText()
                    : getString(R.string.error_connection));
        }
    }

    @Override
    public void onConnectionLost(String reason) {
        showLoading(false);
        showError(getString(R.string.error_connection));
    }

    // -------------------------------------------------------------------------

    private void openChat() {
        startActivity(new Intent(this, ChatActivity.class));
        finish();
    }

    private void showLoading(boolean loading) {
        btnConnect.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}
