package com.javachat.desktop.ui;

import com.javachat.desktop.util.ChatTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * WhatsApp-style bottom input bar.
 * Dark background, rounded text field, icon-style buttons.
 *
 * All buttons use Swing's built-in text rendering (no custom drawString hacks)
 * so they display reliably across all JVMs and font configs.
 */
public class MessageInputPanel extends JPanel {

    private final JTextField inputField;
    private final JButton    sendButton;
    private final JButton    fileButton;
    private final JButton    audioButton;

    private boolean recording = false;

    public MessageInputPanel() {
        super(new BorderLayout(6, 0));
        setBackground(ChatTheme.BG_INPUT);
        setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        inputField  = createInputField();
        fileButton  = createIconButton("File", ChatTheme.TEXT_SECONDARY);
        audioButton = createIconButton("Mic", ChatTheme.TEXT_SECONDARY);
        sendButton  = createSendButton();

        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setOpaque(false);
        left.add(fileButton,  BorderLayout.WEST);
        left.add(audioButton, BorderLayout.EAST);

        add(left,        BorderLayout.WEST);
        add(inputField,  BorderLayout.CENTER);
        add(sendButton,  BorderLayout.EAST);

        inputField.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendButton.doClick();
            }
        });
    }

    public String consumeText() {
        String text = inputField.getText().trim();
        if (text.equals("Message")) return "";
        inputField.setText("");
        return text;
    }

    public void setRecording(boolean rec) {
        this.recording = rec;
        audioButton.setText(rec ? "Stop" : "Mic");
        audioButton.setForeground(rec ? ChatTheme.ACCENT : ChatTheme.TEXT_SECONDARY);
    }

    public void setSendListener(ActionListener l)  { sendButton.addActionListener(l); }
    public void setFileListener(ActionListener l)  { fileButton.addActionListener(l); }
    public void setAudioListener(ActionListener l) { audioButton.addActionListener(l); }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        fileButton.setEnabled(enabled);
        audioButton.setEnabled(enabled);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JTextField createInputField() {
        JTextField f = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x2A3942));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        f.setOpaque(false);
        f.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        f.setFont(ChatTheme.FONT_INPUT);
        f.setForeground(ChatTheme.TEXT_PRIMARY);
        f.setCaretColor(ChatTheme.ACCENT);
        f.setPreferredSize(new Dimension(0, 42));

        final String placeholder = "Message";
        f.setText(placeholder);
        f.setForeground(ChatTheme.TEXT_SECONDARY);
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
        return f;
    }

    /**
     * Small rectangular button with text label.
     * Uses Swing's own rendering — no custom drawString — so text is guaranteed visible.
     * Background matches the input bar so it blends in visually.
     */
    private JButton createIconButton(String label, Color fg) {
        JButton btn = new JButton(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Draw a subtle rounded dark background
                g2.setColor(new Color(0x2A3942));
                g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(fg);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(48, 38));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Round teal send button — draws filled circle + "Send" text. */
    private JButton createSendButton() {
        JButton btn = new JButton("Send") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Filled circle background
                Color bg = isEnabled() ? ChatTheme.ACCENT : new Color(0x2A3942);
                g2.setColor(bg);
                g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                // Draw label text centred
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String label = getText();
                int tx = (getWidth()  - fm.stringWidth(label)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(label, tx, ty);
                g2.dispose();
                // Do NOT call super.paintComponent — we've drawn everything ourselves
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(52, 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
