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

    private boolean sidebarVisible = true;

    public GlobalChatPanel() {
        super(new BorderLayout());
        setBackground(ChatTheme.BG_MAIN);
        add(messagePanel, BorderLayout.CENTER);
        add(usersPanel,   BorderLayout.EAST);
        add(inputPanel,   BorderLayout.SOUTH);
    }

    /** Show/hide the online-users sidebar. Safe to call on the EDT. */
    public void toggleSidebar() {
        sidebarVisible = !sidebarVisible;
        if (sidebarVisible) {
            add(usersPanel, BorderLayout.EAST);
        } else {
            remove(usersPanel);
        }
        revalidate();
        repaint();
    }

    public void init(String username,
                     ConnectionManager connectionManager,
                     FileTransferManager fileTransferManager,
                     AudioManager audioManager) {
        this.localUsername       = username;
        this.connectionManager   = connectionManager;
        this.fileTransferManager = fileTransferManager;
        this.audioManager        = audioManager;

        messagePanel.setLocalUsername(username);

        inputPanel.setSendListener(e -> sendChat());

        // File button: open picker, then immediately show a sent-file bubble
        inputPanel.setFileListener(e ->
            fileTransferManager.selectAndSendFile((fileName, fileSize) ->
                messagePanel.appendFileBubble(null, fileName, fileSize, null)
            )
        );

        // Mic button: toggle recording; when recording stops and send completes show audio bubble
        inputPanel.setAudioListener(e ->
            audioManager.toggleRecording(
                recording -> SwingUtils.runOnEDT(() -> inputPanel.setRecording(recording)),
                error     -> SwingUtils.runOnEDT(() -> appendNotification("[Audio error] " + error)),
                ()        -> SwingUtils.runOnEDT(() -> messagePanel.appendAudioBubble(null, null))
            )
        );
    }

    // ── Public update methods (called on EDT by MainFrame) ───────────────────

    public void appendMessage(Message message) {
        messagePanel.appendChatMessage(message);
    }

    public void appendNotification(String text) {
        messagePanel.appendNotification(text);
    }

    public void appendAiResponse(String text) {
        messagePanel.appendAiResponse(text);
    }

    public void appendFileBubble(String sender, String fileName, long fileSize,
                                 java.util.function.Consumer<Void> onDownload) {
        messagePanel.appendFileBubble(sender, fileName, fileSize, onDownload);
    }

    public void appendAudioBubble(String sender, java.util.function.Consumer<Void> onPlay) {
        messagePanel.appendAudioBubble(sender, onPlay);
    }

    public void addUser(String name)    { usersPanel.addUser(name); }
    public void removeUser(String name) { usersPanel.removeUser(name); }

    // ─────────────────────────────────────────────────────────────────────────

    private void sendChat() {
        String text = inputPanel.consumeText();
        if (text.isEmpty()) return;
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
