package com.javachat.desktop.ui;

import com.javachat.desktop.audio.AudioManager;
import com.javachat.desktop.network.ConnectionManager;
import com.javachat.desktop.protocol.Message;
import com.javachat.desktop.transfer.FileTransferManager;
import com.javachat.desktop.util.ChatTheme;
import com.javachat.desktop.util.SwingUtils;

import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Main chat panel: bubbles (center) + users sidebar (right) + input bar (bottom).
 */
public class GlobalChatPanel extends JPanel {

    private final MessagePanel     messagePanel = new MessagePanel();
    private final OnlineUsersPanel usersPanel   = new OnlineUsersPanel();
    private final MessageInputPanel inputPanel  = new MessageInputPanel();

    private String              localUsername;
    private ConnectionManager   connectionManager;
    private FileTransferManager fileTransferManager;
    private AudioManager        audioManager;

    public GlobalChatPanel() {
        super(new BorderLayout());
        setBackground(ChatTheme.BG_MAIN);
        add(messagePanel, BorderLayout.CENTER);
        add(usersPanel,   BorderLayout.EAST);
        add(inputPanel,   BorderLayout.SOUTH);
    }

    public void init(String username,
                     ConnectionManager connectionManager,
                     FileTransferManager fileTransferManager,
                     AudioManager audioManager) {
        this.localUsername       = username;
        this.connectionManager   = connectionManager;
        this.fileTransferManager = fileTransferManager;
        this.audioManager        = audioManager;

        // Tell the bubble panel who the local user is so it can right-align own bubbles
        messagePanel.setLocalUsername(username);

        inputPanel.setSendListener(e -> sendChat());
        inputPanel.setFileListener(e -> fileTransferManager.selectAndSendFile());
        inputPanel.setAudioListener(e -> {
            audioManager.toggleRecording(
                recording -> SwingUtils.runOnEDT(() -> inputPanel.setRecording(recording)),
                error     -> SwingUtils.runOnEDT(() -> appendNotification("[Audio error] " + error))
            );
        });
    }

    // ── Public update methods (called on EDT by MainFrame) ───────────────────

    public void appendMessage(Message message) {
        messagePanel.appendChatMessage(message);
    }

    public void appendNotification(String text) {
        messagePanel.appendNotification(text);
    }

    public void addUser(String name)    { usersPanel.addUser(name); }
    public void removeUser(String name) { usersPanel.removeUser(name); }

    // ─────────────────────────────────────────────────────────────────────────

    private void sendChat() {
        String text = inputPanel.consumeText();
        if (text.isEmpty() || text.equals("Message")) return;
        // Build a local copy with sender+timestamp for the bubble panel,
        // then send the wire version (no sender field needed by server).
        Message local = Message.chat(localUsername, text);
        try {
            connectionManager.send(Message.chat(text));
            // Server uses broadcastExcept so we never receive our own echo —
            // add the bubble immediately after a successful send.
            messagePanel.appendChatMessage(local);
        } catch (Exception ex) {
            messagePanel.appendNotification("Failed to send: " + ex.getMessage());
        }
    }
}
