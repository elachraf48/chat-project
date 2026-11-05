package com.javachat.desktop.ui;

import com.javachat.desktop.protocol.Message;
import com.javachat.desktop.util.ChatTheme;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * WhatsApp-style chat bubble panel.
 * Own messages appear on the right in green, others on the left in dark.
 * All methods must be called on the EDT.
 */
public class MessagePanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final int  BUBBLE_RADIUS  = 12;
    private static final int  MAX_BUBBLE_W   = 480;
    private static final int  H_PAD          = 12;
    private static final int  V_PAD          = 8;

    // The username of the local user — set by GlobalChatPanel.init()
    private String localUsername = "";

    private final JPanel   bubbleContainer;
    private final JScrollPane scrollPane;

    public MessagePanel() {
        super(new BorderLayout());
        setBackground(ChatTheme.BG_MAIN);

        bubbleContainer = new JPanel();
        bubbleContainer.setLayout(new BoxLayout(bubbleContainer, BoxLayout.Y_AXIS));
        bubbleContainer.setBackground(ChatTheme.BG_MAIN);
        bubbleContainer.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));

        scrollPane = new JScrollPane(bubbleContainer,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ChatTheme.BG_MAIN);
        styleScrollBar(scrollPane.getVerticalScrollBar());

        add(scrollPane, BorderLayout.CENTER);
    }

    public void setLocalUsername(String username) {
        this.localUsername = username;
    }

    /** Append a CHAT message as a bubble. */
    public void appendChatMessage(Message message) {
        boolean isSelf = localUsername.equals(message.getSender());
        String time = message.getTimestamp() != null
                ? TIME_FMT.format(Instant.parse(message.getTimestamp())) : "";

        BubblePanel bubble = new BubblePanel(
                isSelf ? null : message.getSender(),
                message.getText(),
                time,
                isSelf
        );
        addBubble(bubble);
    }

    /** Append a system notification line (joined, left, error, AI response…). */
    public void appendNotification(String text) {
        SystemMsgPanel sys = new SystemMsgPanel(text);
        addBubble(sys);
    }

    public void clear() {
        bubbleContainer.removeAll();
        bubbleContainer.revalidate();
        bubbleContainer.repaint();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void addBubble(JPanel panel) {
        bubbleContainer.add(panel);
        bubbleContainer.add(spacer(4));
        bubbleContainer.revalidate();
        bubbleContainer.repaint();
        // Scroll to bottom — defer until after layout has run
        javax.swing.SwingUtilities.invokeLater(() -> {
            bubbleContainer.revalidate();
            bubbleContainer.repaint();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            vsb.setValue(vsb.getMaximum());
        });
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setBackground(ChatTheme.BG_MAIN);
        bar.setForeground(new Color(0x2A3942));
        bar.setPreferredSize(new Dimension(4, 0));
        bar.setUnitIncrement(16);
    }

    private static JPanel spacer(int h) {
        JPanel p = new JPanel(); p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        p.setPreferredSize(new Dimension(0, h));
        return p;
    }

    // ── Inner: chat bubble ────────────────────────────────────────────────────

    private static final class BubblePanel extends JPanel {

        private final String  sender;    // null for own messages
        private final String  text;
        private final String  time;
        private final boolean isSelf;

        BubblePanel(String sender, String text, String time, boolean isSelf) {
            this.sender = sender;
            this.text   = text;
            this.time   = time;
            this.isSelf = isSelf;
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        }

        @Override
        public Dimension getPreferredSize() {
            // Use a temporary Graphics to get FontMetrics safely before the
            // component is shown (getParent() may be null during early layout).
            java.awt.image.BufferedImage tmp = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2tmp = tmp.createGraphics();
            FontMetrics fmMsg  = g2tmp.getFontMetrics(ChatTheme.FONT_MESSAGE);
            FontMetrics fmTime = g2tmp.getFontMetrics(ChatTheme.FONT_TIMESTAMP);
            FontMetrics fmSend = g2tmp.getFontMetrics(ChatTheme.FONT_SENDER);
            g2tmp.dispose();

            int senderH = (sender != null) ? fmSend.getHeight() + 2 : 0;
            int timeW   = fmTime.stringWidth(time) + 4;

            // Wrap text at max bubble width
            java.util.List<String> wrappedLines = wrapLines(text, MAX_BUBBLE_W - H_PAD * 2, fmMsg);
            int maxLineW = 0;
            for (String l : wrappedLines) maxLineW = Math.max(maxLineW, fmMsg.stringWidth(l));
            int bubbleW = Math.min(Math.max(maxLineW, timeW) + H_PAD * 2, MAX_BUBBLE_W);
            int linesH  = wrappedLines.size() * fmMsg.getHeight();
            int bubbleH = senderH + linesH + fmTime.getHeight() + V_PAD * 3;

            int containerW = getParent() != null ? getParent().getWidth() : bubbleW + 32;
            return new Dimension(Math.max(containerW, bubbleW + 32), bubbleH + 6);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int pw   = getWidth();
            FontMetrics fmMsg  = g2.getFontMetrics(ChatTheme.FONT_MESSAGE);
            FontMetrics fmTime = g2.getFontMetrics(ChatTheme.FONT_TIMESTAMP);
            FontMetrics fmSend = g2.getFontMetrics(ChatTheme.FONT_SENDER);

            // Measure
            java.util.List<String> lines = wrapLines(text,
                    MAX_BUBBLE_W - H_PAD * 2, fmMsg);
            int maxLineW = 0;
            for (String l : lines) maxLineW = Math.max(maxLineW, fmMsg.stringWidth(l));
            int senderH = (sender != null) ? fmSend.getHeight() + 2 : 0;
            int timeW   = fmTime.stringWidth(time) + 4;
            int bubbleW = Math.min(
                    Math.max(maxLineW, timeW) + H_PAD * 2,
                    MAX_BUBBLE_W);
            int linesH  = lines.size() * fmMsg.getHeight();
            int bubbleH = senderH + linesH + fmTime.getHeight() + V_PAD * 3;

            int bx = isSelf ? pw - bubbleW - 8 : 8;
            int by = 2;

            // Bubble background
            g2.setColor(isSelf ? ChatTheme.BUBBLE_SENT : ChatTheme.BUBBLE_RECV);
            g2.fill(new RoundRectangle2D.Float(bx, by, bubbleW, bubbleH, BUBBLE_RADIUS, BUBBLE_RADIUS));

            // Tail nub
            int[] tx, ty;
            if (isSelf) {
                tx = new int[]{bx + bubbleW,     bx + bubbleW,     bx + bubbleW + 6};
                ty = new int[]{by + 10,           by + 18,          by + 14};
            } else {
                tx = new int[]{bx,                bx,               bx - 6};
                ty = new int[]{by + 10,           by + 18,          by + 14};
            }
            g2.fillPolygon(tx, ty, 3);

            int cx = bx + H_PAD;
            int cy = by + V_PAD;

            // Sender name
            if (sender != null) {
                g2.setFont(ChatTheme.FONT_SENDER);
                g2.setColor(ChatTheme.TEXT_SENDER);
                g2.drawString(sender, cx, cy + fmSend.getAscent());
                cy += fmSend.getHeight() + 2;
            }

            // Message text (wrapped)
            g2.setFont(ChatTheme.FONT_MESSAGE);
            g2.setColor(ChatTheme.TEXT_PRIMARY);
            for (String line : lines) {
                g2.drawString(line, cx, cy + fmMsg.getAscent());
                cy += fmMsg.getHeight();
            }

            // Timestamp (bottom-right of bubble)
            g2.setFont(ChatTheme.FONT_TIMESTAMP);
            g2.setColor(ChatTheme.TEXT_TIMESTAMP);
            int timeX = bx + bubbleW - fmTime.stringWidth(time) - H_PAD;
            g2.drawString(time, timeX, by + bubbleH - V_PAD);

            g2.dispose();
        }

        private static java.util.List<String> wrapLines(String text, int maxW,
                FontMetrics fm) {
            java.util.List<String> result = new java.util.ArrayList<>();
            if (text == null || text.isEmpty()) { result.add(""); return result; }
            String[] words = text.split(" ", -1);
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                String candidate = sb.length() == 0 ? word : sb + " " + word;
                if (fm.stringWidth(candidate) <= maxW) {
                    sb = new StringBuilder(candidate);
                } else {
                    if (sb.length() > 0) result.add(sb.toString());
                    sb = new StringBuilder(word);
                }
            }
            if (sb.length() > 0) result.add(sb.toString());
            if (result.isEmpty()) result.add("");
            return result;
        }
    }

    // ── Inner: system message ─────────────────────────────────────────────────

    private static final class SystemMsgPanel extends JPanel {

        private final String text;

        SystemMsgPanel(String text) {
            this.text = text;
            setOpaque(false);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        }

        @Override
        public Dimension getPreferredSize() {
            java.awt.image.BufferedImage tmp = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2tmp = tmp.createGraphics();
            FontMetrics fm = g2tmp.getFontMetrics(ChatTheme.FONT_SYSTEM);
            g2tmp.dispose();
            int containerW = getParent() != null ? getParent().getWidth() : 600;
            return new Dimension(containerW, fm.getHeight() + 10);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fm = g2.getFontMetrics(ChatTheme.FONT_SYSTEM);
            g2.setFont(ChatTheme.FONT_SYSTEM);
            g2.setColor(ChatTheme.TEXT_SYSTEM);
            int tx = (getWidth() - fm.stringWidth(text)) / 2;
            g2.drawString(text, tx, fm.getAscent() + 5);
            g2.dispose();
        }
    }
}
