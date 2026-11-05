package com.javachat.desktop.ui;

import com.javachat.desktop.network.ConnectionManager;
import com.javachat.desktop.network.MessageListener;
import com.javachat.desktop.protocol.Message;
import com.javachat.desktop.protocol.MessageType;
import com.javachat.desktop.util.ChatTheme;
import com.javachat.desktop.util.SwingUtils;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * WhatsApp-style login screen.
 */
public class LoginFrame extends JFrame implements MessageListener {

    private final JTextField  usernameField;
    private final JButton     joinButton;
    private final JLabel      statusLabel;

    private final ConnectionManager connectionManager = new ConnectionManager();

    public LoginFrame() {
        super("JavaChat");
        usernameField = createStyledTextField("Enter your name…");
        joinButton    = createJoinButton();
        statusLabel   = new JLabel(" ");
        statusLabel.setFont(ChatTheme.FONT_SYSTEM);
        statusLabel.setForeground(ChatTheme.TEXT_SECONDARY);
        statusLabel.setHorizontalAlignment(JLabel.CENTER);

        buildUI();
        connectionManager.setListener(this);
    }

    // ── MessageListener ────────────────────────────────────────────────────────

    @Override
    public void onMessage(Message message) {
        SwingUtils.runOnEDT(() -> handleMessage(message));
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtils.runOnEDT(() -> {
            setStatus("Disconnected: " + reason);
            joinButton.setEnabled(true);
        });
    }

    // ── UI ─────────────────────────────────────────────────────────────────────

    private void buildUI() {
        // Dark background panel
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, ChatTheme.BG_LOGIN,
                        0, getHeight(), new Color(0x0D1117)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

        // Card panel
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ChatTheme.BG_PANEL);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(36, 40, 36, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.weightx = 1;

        // Logo / title
        JLabel logo = new JLabel("JavaChat", JLabel.CENTER);
        logo.setFont(ChatTheme.FONT_TITLE);
        logo.setForeground(ChatTheme.ACCENT);
        gbc.gridy = 0; card.add(logo, gbc);

        // Subtitle
        JLabel sub = new JLabel("One server. One global room.", JLabel.CENTER);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(ChatTheme.TEXT_SECONDARY);
        gbc.gridy = 1; card.add(sub, gbc);

        // Spacer
        gbc.gridy = 2; card.add(spacer(12), gbc);

        // Label
        JLabel lbl = new JLabel("Your name");
        lbl.setFont(ChatTheme.FONT_SENDER);
        lbl.setForeground(ChatTheme.TEXT_SECONDARY);
        gbc.gridy = 3; card.add(lbl, gbc);

        // Field
        gbc.gridy = 4; card.add(usernameField, gbc);

        // Status
        gbc.gridy = 5; card.add(statusLabel, gbc);

        // Button
        gbc.gridy = 6; card.add(joinButton, gbc);

        root.add(card, BorderLayout.CENTER);

        setContentPane(root);
        setBackground(ChatTheme.BG_LOGIN);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(400, 460));
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    // ── Logic ──────────────────────────────────────────────────────────────────

    private void attemptJoin() {
        String username = usernameField.getText().trim();
        if (username.isEmpty() || username.equals("Enter your name…")) {
            setStatus("Please enter a username.");
            return;
        }
        if (!username.matches("[A-Za-z0-9_\\-]{1,32}")) {
            setStatus("Letters, digits, _ and - only. Max 32 chars.");
            return;
        }
        joinButton.setEnabled(false);
        setStatus("Connecting…");

        Thread t = new Thread(() -> {
            try {
                connectionManager.connect();
                connectionManager.send(Message.join(username));
            } catch (Exception ex) {
                SwingUtils.runOnEDT(() -> {
                    setStatus("Connection failed: " + ex.getMessage());
                    joinButton.setEnabled(true);
                });
            }
        }, "connect-thread");
        t.setDaemon(true);
        t.start();
    }

    private void handleMessage(Message message) {
        if (message.getType() == MessageType.JOIN_SUCCESS) {
            String username = message.getSender();
            setStatus("Joined as " + username);
            connectionManager.setListener(null);
            MainFrame mainFrame = new MainFrame(username, connectionManager);
            connectionManager.setListener(mainFrame);
            mainFrame.setVisible(true);
            dispose();
        } else if (message.getType() == MessageType.JOIN_ERROR) {
            setStatus(message.getText());
            joinButton.setEnabled(true);
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private JTextField createStyledTextField(String placeholder) {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x2A3942));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        f.setOpaque(false);
        f.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        f.setFont(ChatTheme.FONT_INPUT);
        f.setForeground(ChatTheme.TEXT_SECONDARY);
        f.setCaretColor(ChatTheme.ACCENT);
        f.setPreferredSize(new Dimension(0, 44));

        // Placeholder
        f.setText(placeholder);
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (f.getText().equals(placeholder)) {
                    f.setText("");
                    f.setForeground(ChatTheme.TEXT_PRIMARY);
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (f.getText().isEmpty()) {
                    f.setText(placeholder);
                    f.setForeground(ChatTheme.TEXT_SECONDARY);
                }
            }
        });
        f.addActionListener(e -> attemptJoin());
        return f;
    }

    private JButton createJoinButton() {
        JButton btn = new JButton("Join Chat") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? ChatTheme.ACCENT : new Color(0x2A3942));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(ChatTheme.TEXT_PRIMARY);
                g2.setFont(getFont());
                java.awt.FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setForeground(ChatTheme.TEXT_PRIMARY);
        btn.setPreferredSize(new Dimension(0, 46));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> attemptJoin());
        return btn;
    }

    private static JPanel spacer(int height) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(0, height));
        return p;
    }
}
