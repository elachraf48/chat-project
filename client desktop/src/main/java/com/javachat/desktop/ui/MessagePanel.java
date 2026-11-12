package com.javachat.desktop.ui;

import com.javachat.desktop.protocol.Message;
import com.javachat.desktop.util.ChatTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * WhatsApp-style chat bubble panel.
 *
 *  - Text bubbles:  green right (own) / dark grey left (others)
 *  - File bubbles:  file icon + name + size; received files show "Download" button
 *  - Audio bubbles: mic icon + "Voice message"; received show "Play" button
 *  - System lines:  centred italic grey pill
 *
 * All public methods must be called on the EDT.
 */
public class MessagePanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private String localUsername = "";

    private final JPanel      bubbleContainer;
    private final JScrollPane scrollPane;

    public MessagePanel() {
        super(new BorderLayout());
        setBackground(ChatTheme.BG_MAIN);

        bubbleContainer = new JPanel();
        bubbleContainer.setLayout(new BoxLayout(bubbleContainer, BoxLayout.Y_AXIS));
        bubbleContainer.setBackground(ChatTheme.BG_MAIN);
        bubbleContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

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

    // ── Public append methods ─────────────────────────────────────────────────

    public void appendChatMessage(Message message) {
        boolean isSelf = message.getSender() != null && localUsername.equals(message.getSender());
        addBubble(new TextBubble(isSelf ? null : message.getSender(),
                message.getText(), formatTime(message.getTimestamp()), isSelf), isSelf);
    }

    public void appendFileBubble(String sender, String fileName, long fileSize,
                                 Consumer<Void> onDownload) {
        boolean isSelf = (sender == null);
        addBubble(new AttachBubble(sender, fileName, fileSize, true, onDownload), isSelf);
    }

    public void appendAudioBubble(String sender, Consumer<Void> onPlay) {
        boolean isSelf = (sender == null);
        addBubble(new AttachBubble(sender, null, -1, false, onPlay), isSelf);
    }

    public void appendNotification(String text) {
        addBubble(new SystemBubble(text), false);
    }

    /** Wrapping AI response bubble — teal-accented, centred, handles long text. */
    public void appendAiResponse(String text) {
        addBubble(new AiBubble(text), false);
    }

    public void clear() {
        bubbleContainer.removeAll();
        bubbleContainer.revalidate();
        bubbleContainer.repaint();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void addBubble(JPanel panel, boolean alignRight) {
        // Wrap every bubble in a FlowLayout row so it only takes natural width.
        // Right-align for self-messages so green bubbles appear on the right.
        int align = alignRight ? FlowLayout.RIGHT : FlowLayout.LEFT;
        JPanel row = new JPanel(new FlowLayout(align, 0, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        row.add(panel);

        bubbleContainer.add(row);
        bubbleContainer.add(spacer(3));
        bubbleContainer.revalidate();
        bubbleContainer.repaint();
        javax.swing.SwingUtilities.invokeLater(() -> {
            bubbleContainer.revalidate();
            bubbleContainer.repaint();
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            vsb.setValue(vsb.getMaximum());
        });
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setBackground(ChatTheme.BG_MAIN);
        bar.setPreferredSize(new Dimension(5, 0));
        bar.setUnitIncrement(20);
    }

    private static JPanel spacer(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(0, h));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        p.setMinimumSize(new Dimension(0, h));
        return p;
    }

    static String formatTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try { return TIME_FMT.format(Instant.parse(iso)); }
        catch (Exception e) { return ""; }
    }

    static FontMetrics metrics(Font font) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = tmp.createGraphics();
        FontMetrics fm = g2.getFontMetrics(font);
        g2.dispose();
        return fm;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Inner panels
    // ═════════════════════════════════════════════════════════════════════════

    // ── Text bubble ───────────────────────────────────────────────────────────

    private static final class TextBubble extends JPanel {

        private static final int MAX_W  = 460;
        private static final int H_PAD  = 14;
        private static final int V_PAD  = 10;
        private static final int RADIUS = 14;

        private final String  sender;
        private final String  text;
        private final String  time;
        private final boolean isSelf;

        TextBubble(String sender, String text, String time, boolean isSelf) {
            this.sender = sender;
            this.text   = text  != null ? text  : "";
            this.time   = time  != null ? time  : "";
            this.isSelf = isSelf;
            setOpaque(false);
        }

        @Override public Dimension getMinimumSize()   { return getPreferredSize(); }
        @Override public Dimension getMaximumSize()   {
            Dimension d = getPreferredSize();
            return new Dimension(d.width, d.height);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fmMsg  = metrics(ChatTheme.FONT_MESSAGE);
            FontMetrics fmTime = metrics(ChatTheme.FONT_TIMESTAMP);
            FontMetrics fmSend = metrics(ChatTheme.FONT_SENDER);

            int senderH = sender != null ? fmSend.getHeight() + 4 : 0;
            List<String> lines = wrap(text, MAX_W - H_PAD * 2, fmMsg);
            int maxW = 0;
            for (String l : lines) maxW = Math.max(maxW, fmMsg.stringWidth(l));
            int timeW   = fmTime.stringWidth(time) + H_PAD;
            int bubbleW = Math.min(Math.max(maxW, timeW) + H_PAD * 2, MAX_W);
            int bubbleH = senderH + lines.size() * fmMsg.getHeight()
                        + fmTime.getHeight() + V_PAD * 2 + 4;
            return new Dimension(bubbleW + 20, bubbleH + 4);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            FontMetrics fmMsg  = g2.getFontMetrics(ChatTheme.FONT_MESSAGE);
            FontMetrics fmTime = g2.getFontMetrics(ChatTheme.FONT_TIMESTAMP);
            FontMetrics fmSend = g2.getFontMetrics(ChatTheme.FONT_SENDER);

            List<String> lines = wrap(text, MAX_W - H_PAD * 2, fmMsg);
            int maxW = 0;
            for (String l : lines) maxW = Math.max(maxW, fmMsg.stringWidth(l));
            int senderH = sender != null ? fmSend.getHeight() + 4 : 0;
            int timeW   = fmTime.stringWidth(time) + H_PAD;
            int bubbleW = Math.min(Math.max(maxW, timeW) + H_PAD * 2, MAX_W);
            int bubbleH = senderH + lines.size() * fmMsg.getHeight()
                        + fmTime.getHeight() + V_PAD * 2 + 4;

            // Draw at x=8 (small left margin inside the FlowLayout row)
            int bx = 8, by = 2;

            g2.setColor(isSelf ? ChatTheme.BUBBLE_SENT : ChatTheme.BUBBLE_RECV);
            g2.fill(new RoundRectangle2D.Float(bx, by, bubbleW, bubbleH, RADIUS, RADIUS));

            // Tail nub
            if (isSelf) {
                int[] tx = {bx+bubbleW, bx+bubbleW, bx+bubbleW+7};
                int[] ty = {by+12, by+20, by+16};
                g2.fillPolygon(tx, ty, 3);
            } else {
                int[] tx = {bx, bx, bx-7};
                int[] ty = {by+12, by+20, by+16};
                g2.fillPolygon(tx, ty, 3);
            }

            int cx = bx + H_PAD;
            int cy = by + V_PAD;

            if (sender != null) {
                g2.setFont(ChatTheme.FONT_SENDER);
                g2.setColor(ChatTheme.TEXT_SENDER);
                g2.drawString(sender, cx, cy + fmSend.getAscent());
                cy += fmSend.getHeight() + 4;
            }

            g2.setFont(ChatTheme.FONT_MESSAGE);
            g2.setColor(ChatTheme.TEXT_PRIMARY);
            for (String line : lines) {
                g2.drawString(line, cx, cy + fmMsg.getAscent());
                cy += fmMsg.getHeight();
            }

            g2.setFont(ChatTheme.FONT_TIMESTAMP);
            g2.setColor(ChatTheme.TEXT_TIMESTAMP);
            g2.drawString(time, bx + bubbleW - fmTime.stringWidth(time) - H_PAD,
                    by + bubbleH - 4);

            g2.dispose();
        }

        private static List<String> wrap(String text, int maxW, FontMetrics fm) {
            List<String> r = new ArrayList<>();
            if (text == null || text.isEmpty()) { r.add(""); return r; }
            String[] words = text.split(" ", -1);
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                String c = sb.length() == 0 ? w : sb + " " + w;
                if (fm.stringWidth(c) <= maxW) { sb = new StringBuilder(c); }
                else { if (sb.length() > 0) r.add(sb.toString()); sb = new StringBuilder(w); }
            }
            if (sb.length() > 0) r.add(sb.toString());
            if (r.isEmpty()) r.add("");
            return r;
        }
    }

    // ── Attach bubble (file OR audio) ─────────────────────────────────────────

    /**
     * Unified bubble for file and audio messages.
     * isFile=true → shows document icon + fileName + size (+ Download button for received)
     * isFile=false → shows mic icon + "Voice message" (+ Play button for received)
     */
    private static final class AttachBubble extends JPanel {

        private static final int RADIUS = 14;
        private static final Color TEAL = new Color(0x00A884);

        private final boolean isFile;
        private final boolean isSelf;
        private final String  label;

        AttachBubble(String sender, String fileName, long fileSize,
                     boolean isFile, Consumer<Void> actionCallback) {
            this.isFile = isFile;
            this.isSelf = (sender == null);

            // Build display label
            if (isFile) {
                String sizeStr = fileSize > 0 ? "  " + humanSize(fileSize) : "";
                String who = sender != null ? sender + "  " : "";
                this.label = who + (fileName != null ? fileName : "file") + sizeStr;
            } else {
                String who = sender != null ? sender + "  " : "";
                this.label = who + "Voice message";
            }

            setOpaque(false);
            setLayout(new BorderLayout(0, 0));

            // Icon panel — drawn with Graphics2D, zero font dependency.
            final boolean iconIsFile = isFile;
            JPanel iconPanel = new JPanel() {
                @Override public Dimension getPreferredSize() { return new Dimension(24, 24); }
                @Override public Dimension getMinimumSize()   { return getPreferredSize(); }
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g2.setColor(ChatTheme.ACCENT);
                    if (iconIsFile) {
                        // Document icon: rect with folded top-right corner
                        int w = 10, h = 13, fold = 3;
                        int lx = cx - w / 2, ty = cy - h / 2;
                        GeneralPath doc = new GeneralPath();
                        doc.moveTo(lx, ty);
                        doc.lineTo(lx + w - fold, ty);
                        doc.lineTo(lx + w, ty + fold);
                        doc.lineTo(lx + w, ty + h);
                        doc.lineTo(lx, ty + h);
                        doc.closePath();
                        g2.fill(doc);
                        // Fold triangle (darker)
                        GeneralPath cr = new GeneralPath();
                        cr.moveTo(lx + w - fold, ty);
                        cr.lineTo(lx + w - fold, ty + fold);
                        cr.lineTo(lx + w, ty + fold);
                        cr.closePath();
                        g2.setColor(ChatTheme.BUBBLE_RECV);
                        g2.fill(cr);
                    } else {
                        // Mic icon: capsule + arc stand
                        int mw = 6, mh = 9;
                        g2.fill(new java.awt.geom.RoundRectangle2D.Float(cx - mw / 2f, cy - mh / 2f - 2, mw, mh, mw, mw));
                        g2.setStroke(new java.awt.BasicStroke(1.4f));
                        g2.draw(new java.awt.geom.Arc2D.Float(cx - 5, cy - 1, 10, 8, 0, -180, java.awt.geom.Arc2D.OPEN));
                        g2.drawLine(cx, cy + 7, cx, cy + 10);
                        g2.drawLine(cx - 3, cy + 10, cx + 3, cy + 10);
                    }
                    g2.dispose();
                }
            };
            iconPanel.setOpaque(false);
            iconPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

            // Text label
            JLabel textLabel = new JLabel(this.label);
            textLabel.setFont(ChatTheme.FONT_MESSAGE);
            textLabel.setForeground(ChatTheme.TEXT_PRIMARY);

            // Inner content panel (opaque=false so bubble background shows)
            JPanel content = new JPanel(new BorderLayout(8, 0));
            content.setOpaque(false);
            content.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            content.add(iconPanel,  BorderLayout.WEST);
            content.add(textLabel,  BorderLayout.CENTER);

            // Action button (Download or Play) only for received messages
            if (!isSelf && actionCallback != null) {
                JButton btn = actionButton(isFile ? "Download" : "Play");
                btn.addActionListener(e -> actionCallback.accept(null));
                content.add(btn, BorderLayout.EAST);
            }

            add(content, BorderLayout.CENTER);

            // Calculate preferred size
            FontMetrics fm = metrics(ChatTheme.FONT_MESSAGE);
            int textW = fm.stringWidth(this.label);
            int btnW  = (!isSelf && actionCallback != null) ? 90 : 0;
            int w = 36 + textW + btnW + 14 * 2 + 8 * 2 + 20; // icon+text+btn+pads
            int h = 52;
            setPreferredSize(new Dimension(Math.min(Math.max(w, 200), 440), h));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isSelf ? ChatTheme.BUBBLE_SENT : ChatTheme.BUBBLE_RECV);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));

            // Coloured left stripe
            g2.setColor(TEAL);
            g2.fill(new RoundRectangle2D.Float(0, 0, 4, getHeight(), 4, 4));

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ── System / notification pill ────────────────────────────────────────────

    private static final class SystemBubble extends JPanel {

        private static final int MAX_W  = 440;
        private static final int H_PAD  = 14;
        private static final int V_PAD  = 6;

        private final List<String> lines;

        SystemBubble(String text) {
            setOpaque(false);
            FontMetrics fm = metrics(ChatTheme.FONT_SYSTEM);
            this.lines = wrapLines(text != null ? text : "", MAX_W - H_PAD * 2, fm);
            int maxLw = 0;
            for (String l : lines) maxLw = Math.max(maxLw, fm.stringWidth(l));
            int w = Math.min(maxLw + H_PAD * 2, MAX_W);
            int h = lines.size() * fm.getHeight() + V_PAD * 2;
            setPreferredSize(new Dimension(w, h));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(0x2A3942));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            g2.setFont(ChatTheme.FONT_SYSTEM);
            g2.setColor(ChatTheme.TEXT_SYSTEM);
            FontMetrics fm = g2.getFontMetrics();
            int cy = V_PAD + fm.getAscent();
            for (String line : lines) {
                g2.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, cy);
                cy += fm.getHeight();
            }
            g2.dispose();
        }

        private static List<String> wrapLines(String text, int maxW, FontMetrics fm) {
            List<String> r = new ArrayList<>();
            if (text.isEmpty()) { r.add(""); return r; }
            for (String para : text.split("\n", -1)) {
                if (para.isEmpty()) { r.add(""); continue; }
                String[] words = para.split(" ", -1);
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    String c = sb.length() == 0 ? w : sb + " " + w;
                    if (fm.stringWidth(c) <= maxW) { sb = new StringBuilder(c); }
                    else { if (sb.length() > 0) r.add(sb.toString()); sb = new StringBuilder(w); }
                }
                if (sb.length() > 0) r.add(sb.toString());
            }
            if (r.isEmpty()) r.add("");
            return r;
        }
    }

    // ── AI response bubble ────────────────────────────────────────────────────

    /**
     * Wide, fully-wrapping bubble for Gemini AI responses.
     * Teal left stripe, dark background, centred in the chat column.
     */
    private static final class AiBubble extends JPanel {

        private static final int MAX_W  = 520;
        private static final int H_PAD  = 14;
        private static final int V_PAD  = 10;
        private static final int RADIUS = 12;
        private static final Color TEAL = new Color(0x00A884);

        private final List<String> lines;

        AiBubble(String text) {
            setOpaque(false);
            FontMetrics fm = metrics(ChatTheme.FONT_MESSAGE);
            this.lines = wrapLines(text != null ? text : "", MAX_W - H_PAD * 2 - 6, fm);
            int maxLw = 0;
            for (String l : lines) maxLw = Math.max(maxLw, fm.stringWidth(l));
            int w = Math.min(maxLw + H_PAD * 2 + 6, MAX_W);
            int h = lines.size() * fm.getHeight() + V_PAD * 2;
            // At least 220px wide
            w = Math.max(w, 220);
            setPreferredSize(new Dimension(w, h));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // Background
            g2.setColor(new Color(0x1A2A32));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), RADIUS, RADIUS));
            // Teal left stripe
            g2.setColor(TEAL);
            g2.fill(new RoundRectangle2D.Float(0, 0, 4, getHeight(), 4, 4));
            // Text
            g2.setFont(ChatTheme.FONT_MESSAGE);
            g2.setColor(new Color(0xCCDDE6));
            FontMetrics fm = g2.getFontMetrics();
            int cy = V_PAD + fm.getAscent();
            for (String line : lines) {
                g2.drawString(line, H_PAD + 6, cy);
                cy += fm.getHeight();
            }
            g2.dispose();
        }

        private static List<String> wrapLines(String text, int maxW, FontMetrics fm) {
            List<String> r = new ArrayList<>();
            if (text.isEmpty()) { r.add(""); return r; }
            for (String para : text.split("\n", -1)) {
                if (para.isEmpty()) { r.add(""); continue; }
                String[] words = para.split(" ", -1);
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    String c = sb.length() == 0 ? w : sb + " " + w;
                    if (fm.stringWidth(c) <= maxW) { sb = new StringBuilder(c); }
                    else { if (sb.length() > 0) r.add(sb.toString()); sb = new StringBuilder(w); }
                }
                if (sb.length() > 0) r.add(sb.toString());
            }
            if (r.isEmpty()) r.add("");
            return r;
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    static JButton actionButton(String label) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(ChatTheme.ACCENT);
        btn.setBackground(new Color(0x0D1117, false));
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ChatTheme.ACCENT, 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1_048_576) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }
}
