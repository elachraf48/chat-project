package com.javachat.desktop.ui;

import com.javachat.desktop.ai.GeminiChatManager;
import com.javachat.desktop.audio.AudioManager;
import com.javachat.desktop.network.ConnectionManager;
import com.javachat.desktop.network.MessageListener;
import com.javachat.desktop.protocol.Message;
import com.javachat.desktop.protocol.MessageType;
import com.javachat.desktop.transfer.FileProgressListener;
import com.javachat.desktop.transfer.FileTransferManager;
import com.javachat.desktop.util.ChatTheme;
import com.javachat.desktop.util.SwingUtils;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Main application window — WhatsApp-style dark theme.
 * Header bar (top) → chat panel (center) → AI prompt bar (bottom).
 */
public class MainFrame extends JFrame implements MessageListener {

    private final GlobalChatPanel   chatPanel;
    private final ConnectionManager connectionManager;
    private final AudioManager      audioManager;
    private final GeminiChatManager geminiChatManager;
    private final String            username;

    // Incoming file reassembly
    private final Map<String, java.io.ByteArrayOutputStream> fileBuffers = new HashMap<>();
    private final Map<String, Message>                        fileMeta    = new HashMap<>();

    // Incoming audio reassembly
    private final Map<String, java.io.ByteArrayOutputStream> audioBuffers = new HashMap<>();

    public MainFrame(String username, ConnectionManager connectionManager) {
        super("JavaChat");
        this.username          = username;
        this.connectionManager = connectionManager;

        FileTransferManager fileTransferManager = new FileTransferManager(connectionManager, this);
        this.audioManager      = new AudioManager(connectionManager);
        this.geminiChatManager = new GeminiChatManager(connectionManager);

        fileTransferManager.setProgressListener(new FileProgressListener() {
            @Override public void onProgress(String f, long sent, long total) {
                int pct = (int) (sent * 100 / total);
                SwingUtils.runOnEDT(() ->
                    chatPanel.appendNotification("Sending " + f + " … " + pct + "%"));
            }
            @Override public void onComplete(String f) {
                SwingUtils.runOnEDT(() -> chatPanel.appendNotification("✓ File sent: " + f));
            }
            @Override public void onError(String f, String reason) {
                SwingUtils.runOnEDT(() ->
                    chatPanel.appendNotification("[File error] " + f + ": " + reason));
            }
        });

        chatPanel = new GlobalChatPanel();
        chatPanel.init(username, connectionManager, fileTransferManager, audioManager);
        chatPanel.addUser(username);

        // Root layout
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ChatTheme.BG_MAIN);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(chatPanel,     BorderLayout.CENTER);
        root.add(buildAiBar(),  BorderLayout.SOUTH);
        setContentPane(root);

        setTitle("JavaChat — " + username);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(960, 640));
        pack();
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                connectionManager.disconnect();
            }
        });
    }

    // =========================================================================
    // MessageListener
    // =========================================================================

    @Override public void onMessage(Message message) {
        SwingUtils.runOnEDT(() -> handleMessage(message));
    }

    @Override public void onDisconnected(String reason) {
        SwingUtils.runOnEDT(() -> {
            chatPanel.appendNotification("Disconnected: " + reason);
            int choice = JOptionPane.showConfirmDialog(this,
                    "Disconnected: " + reason + "\n\nReturn to login screen?",
                    "Connection Lost", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                dispose();
                SwingUtils.runOnEDT(() -> new LoginFrame().setVisible(true));
            }
        });
    }

    // =========================================================================
    // Message dispatch
    // =========================================================================

    private void handleMessage(Message message) {
        if (message.getType() == null) return;
        switch (message.getType()) {
            case CHAT ->
                chatPanel.appendMessage(message);

            case USER_JOINED -> {
                chatPanel.addUser(message.getSender());
                chatPanel.appendNotification(message.getSender() + " joined the chat.");
            }
            case USER_LEFT -> {
                chatPanel.removeUser(message.getSender());
                chatPanel.appendNotification(message.getSender() + " left the chat.");
            }

            // ── File receive ──────────────────────────────────────────────
            case FILE_META -> {
                String fid = message.getFileId();
                if (fid != null) {
                    fileBuffers.put(fid, new java.io.ByteArrayOutputStream());
                    fileMeta.put(fid, message);
                    chatPanel.appendNotification(message.getSender()
                            + " is sending: " + message.getFileName()
                            + " (" + humanSize(message.getFileSize()) + ")");
                }
            }
            case FILE_CHUNK -> {
                String fid = message.getFileId();
                var buf = fid != null ? fileBuffers.get(fid) : null;
                if (buf != null && message.getData() != null) {
                    try { buf.write(Base64.getDecoder().decode(message.getData())); }
                    catch (Exception ignored) {}
                }
            }
            case FILE_COMPLETE -> {
                String fid = message.getFileId();
                var buf  = fid != null ? fileBuffers.remove(fid) : null;
                var meta = fid != null ? fileMeta.remove(fid)    : null;
                if (buf != null && meta != null)
                    saveReceivedFile(meta.getFileName(), buf.toByteArray(), meta.getSender());
            }

            // ── Audio receive ─────────────────────────────────────────────
            case AUDIO_META -> {
                String fid = message.getFileId();
                if (fid != null) {
                    audioBuffers.put(fid, new java.io.ByteArrayOutputStream());
                    chatPanel.appendNotification(message.getSender() + " sent a voice message.");
                }
            }
            case AUDIO_CHUNK -> {
                String fid = message.getFileId();
                var buf = fid != null ? audioBuffers.get(fid) : null;
                if (buf != null && message.getData() != null) {
                    try { buf.write(Base64.getDecoder().decode(message.getData())); }
                    catch (Exception ignored) {}
                }
            }
            case AUDIO_COMPLETE -> {
                String fid = message.getFileId();
                var buf = fid != null ? audioBuffers.remove(fid) : null;
                if (buf != null) {
                    chatPanel.appendNotification("▶ Playing voice message…");
                    audioManager.playReceived(buf.toByteArray());
                }
            }

            // ── AI ────────────────────────────────────────────────────────
            case AI_RESPONSE ->
                chatPanel.appendNotification("✦ Gemini: " + message.getText());

            // ── Error ─────────────────────────────────────────────────────
            case ERROR ->
                chatPanel.appendNotification("⚠ " + message.getText());

            default -> {}
        }
    }

    // =========================================================================
    // UI builders
    // =========================================================================

    /** Dark header bar with room name + connected-as label. */
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(ChatTheme.BG_HEADER);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        // Avatar circle + room name
        JLabel room = new JLabel("  Global Chat");
        room.setFont(ChatTheme.FONT_HEADER);
        room.setForeground(ChatTheme.TEXT_PRIMARY);

        // Connected-as badge
        JLabel user = new JLabel("@" + username + "  ");
        user.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        user.setForeground(ChatTheme.TEXT_SECONDARY);

        header.add(room, BorderLayout.WEST);
        header.add(user, BorderLayout.EAST);
        header.setPreferredSize(new Dimension(0, 48));
        return header;
    }

    /** Styled AI prompt bar at the bottom. */
    private JPanel buildAiBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(0x0D1117));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JLabel lbl = new JLabel("✦");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lbl.setForeground(ChatTheme.ACCENT);
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));

        JTextField promptField = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x1C2526));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        promptField.setOpaque(false);
        promptField.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        promptField.setFont(ChatTheme.FONT_INPUT);
        promptField.setForeground(ChatTheme.TEXT_SECONDARY);
        promptField.setCaretColor(ChatTheme.ACCENT);
        promptField.setPreferredSize(new Dimension(0, 38));

        final String placeholder = "Ask Gemini AI…";
        promptField.setText(placeholder);
        promptField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (promptField.getText().equals(placeholder)) {
                    promptField.setText(""); promptField.setForeground(ChatTheme.TEXT_PRIMARY);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (promptField.getText().isEmpty()) {
                    promptField.setText(placeholder); promptField.setForeground(ChatTheme.TEXT_SECONDARY);
                }
            }
        });

        JButton askBtn = new JButton("Ask") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? ChatTheme.ACCENT : new Color(0x2A3942));
                g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                String label = getText();
                g2.drawString(label,
                        (getWidth()  - fm.stringWidth(label)) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
                // Do NOT call super.paintComponent — avoids double-painting the text
            }
        };
        askBtn.setOpaque(false);
        askBtn.setContentAreaFilled(false);
        askBtn.setBorderPainted(false);
        askBtn.setFocusPainted(false);
        askBtn.setPreferredSize(new Dimension(42, 38));
        askBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Runnable send = () -> {
            String prompt = promptField.getText().trim();
            if (prompt.isEmpty() || prompt.equals(placeholder)) return;
            promptField.setText("");
            try {
                geminiChatManager.sendPrompt(prompt);
                chatPanel.appendNotification("You → Gemini: " + prompt);
            } catch (IOException ex) {
                chatPanel.appendNotification("[AI error] " + ex.getMessage());
            }
        };
        askBtn.addActionListener(e -> send.run());
        promptField.addActionListener(e -> send.run());

        bar.add(lbl,         BorderLayout.WEST);
        bar.add(promptField, BorderLayout.CENTER);
        bar.add(askBtn,      BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void saveReceivedFile(String suggestedName, byte[] data, String sender) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save file from " + sender);
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File dest = chooser.getSelectedFile();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(data);
            chatPanel.appendNotification("✓ Saved: " + dest.getName());
        } catch (IOException e) {
            chatPanel.appendNotification("[File error] " + e.getMessage());
        }
    }

    private static String humanSize(Long bytes) {
        if (bytes == null) return "?";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
