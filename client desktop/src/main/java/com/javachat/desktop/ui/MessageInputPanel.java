package com.javachat.desktop.ui;

import com.javachat.desktop.util.ChatTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;

/**
 * WhatsApp-style bottom input bar.
 *
 * All icons are drawn with Graphics2D primitives — no font, no Unicode, no emoji.
 * This renders identically on every JVM and OS regardless of installed fonts.
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
        fileButton  = createFileButton();
        audioButton = createMicButton();
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
        inputField.setText("");
        return text;
    }

    /** Toggle mic button appearance between record and stop states. */
    public void setRecording(boolean rec) {
        this.recording = rec;
        audioButton.setForeground(rec ? ChatTheme.ACCENT : ChatTheme.TEXT_SECONDARY);
        audioButton.repaint();
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

    // ── Input field ───────────────────────────────────────────────────────────

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

    // ── Icon buttons ──────────────────────────────────────────────────────────

    /**
     * File / paperclip button.
     * Draws a small document rectangle with a folded corner.
     */
    private JButton createFileButton() {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = setup(g);
                try {
                    g2.setColor(new Color(0x2A3942));
                    g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 10, 10));

                    // Centre the icon
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    int w = 11, h = 14, fold = 4;

                    // Left-top to fold, then to right-top (skipping corner), down, left, up
                    int lx = cx - w / 2, ty = cy - h / 2;
                    GeneralPath doc = new GeneralPath();
                    doc.moveTo(lx, ty);
                    doc.lineTo(lx + w - fold, ty);          // top edge (leaves fold gap)
                    doc.lineTo(lx + w, ty + fold);          // diagonal fold
                    doc.lineTo(lx + w, ty + h);             // right side
                    doc.lineTo(lx, ty + h);                 // bottom
                    doc.closePath();
                    g2.setColor(isEnabled() ? ChatTheme.TEXT_SECONDARY : new Color(0x445566));
                    g2.fill(doc);

                    // Fold triangle (slightly lighter)
                    GeneralPath corner = new GeneralPath();
                    corner.moveTo(lx + w - fold, ty);
                    corner.lineTo(lx + w - fold, ty + fold);
                    corner.lineTo(lx + w, ty + fold);
                    corner.closePath();
                    g2.setColor(new Color(0x3D5465));
                    g2.fill(corner);

                    // Two horizontal lines on the doc
                    g2.setColor(new Color(0x0D1117));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawLine(lx + 2, ty + h / 2,     lx + w - 2, ty + h / 2);
                    g2.drawLine(lx + 2, ty + h / 2 + 3, lx + w - 2, ty + h / 2 + 3);
                } finally {
                    g2.dispose();
                }
            }
        };
        styleIconButton(btn);
        return btn;
    }

    /**
     * Microphone button.
     * When not recording: capsule body + stand arc.
     * When recording:     filled square (stop icon).
     */
    private JButton createMicButton() {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = setup(g);
                try {
                    g2.setColor(new Color(0x2A3942));
                    g2.fill(new RoundRectangle2D.Float(2, 2, getWidth() - 4, getHeight() - 4, 10, 10));

                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    Color ic = isEnabled()
                            ? (recording ? ChatTheme.ACCENT : ChatTheme.TEXT_SECONDARY)
                            : new Color(0x445566);
                    g2.setColor(ic);

                    if (recording) {
                        // Stop icon: filled rounded square
                        int s = 10;
                        g2.fill(new RoundRectangle2D.Float(cx - s / 2f, cy - s / 2f, s, s, 3, 3));
                    } else {
                        // Mic capsule
                        int mw = 7, mh = 11;
                        g2.fill(new RoundRectangle2D.Float(cx - mw / 2f, cy - mh / 2f - 2, mw, mh, mw, mw));
                        // Stand arc
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.draw(new Arc2D.Float(cx - 6, cy - 1, 12, 10, 0, -180, Arc2D.OPEN));
                        // Vertical stem
                        g2.drawLine(cx, cy + 9, cx, cy + 12);
                        // Base line
                        g2.drawLine(cx - 4, cy + 12, cx + 4, cy + 12);
                    }
                } finally {
                    g2.dispose();
                }
            }
        };
        styleIconButton(btn);
        return btn;
    }

    /**
     * Send button — filled teal circle with a right-pointing triangle arrow.
     * Drawn entirely with Graphics2D; no text, no font.
     */
    private JButton createSendButton() {
        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = setup(g);
                try {
                    // Teal circle background
                    g2.setColor(isEnabled() ? ChatTheme.ACCENT : new Color(0x2A3942));
                    g2.fill(new Ellipse2D.Float(1, 1, getWidth() - 2, getHeight() - 2));

                    // Right-pointing filled triangle (paper-plane / send arrow)
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    int tw = 10, th = 10;
                    GeneralPath arrow = new GeneralPath();
                    arrow.moveTo(cx - tw / 2f + 1, cy - th / 2f);  // top-left
                    arrow.lineTo(cx + tw / 2f + 2, cy);             // tip
                    arrow.lineTo(cx - tw / 2f + 1, cy + th / 2f);  // bottom-left
                    arrow.closePath();
                    g2.setColor(Color.WHITE);
                    g2.fill(arrow);
                } finally {
                    g2.dispose();
                }
            }
        };
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(42, 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static void styleIconButton(JButton btn) {
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(42, 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static Graphics2D setup(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g2;
    }

}
