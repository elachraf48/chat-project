package com.javachat.desktop.ui;

import com.javachat.desktop.util.ChatTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
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
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/**
 * WhatsApp-style online users sidebar.
 * Each user shown with a colored avatar circle (initial) and username.
 */
public class OnlineUsersPanel extends JPanel {

    private final List<String> users = new ArrayList<>();
    private final UsersListPanel listPanel;
    private final JLabel headerLabel;

    public OnlineUsersPanel() {
        super(new BorderLayout());
        setBackground(ChatTheme.BG_PANEL);
        setPreferredSize(new Dimension(180, 0));

        // Header — updated live with user count
        headerLabel = new JLabel("  Online  0", JLabel.LEFT);
        headerLabel.setFont(ChatTheme.FONT_HEADER);
        headerLabel.setForeground(ChatTheme.TEXT_PRIMARY);
        headerLabel.setOpaque(true);
        headerLabel.setBackground(ChatTheme.BG_HEADER);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        headerLabel.setPreferredSize(new Dimension(0, 42));
        add(headerLabel, BorderLayout.NORTH);

        listPanel = new UsersListPanel();
        JScrollPane sp = new JScrollPane(listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(ChatTheme.BG_PANEL);
        styleScrollBar(sp.getVerticalScrollBar());
        add(sp, BorderLayout.CENTER);
    }

    public void addUser(String username) {
        if (!users.contains(username)) {
            users.add(username);
            refresh();
        }
    }

    public void removeUser(String username) {
        users.remove(username);
        refresh();
    }

    public void setUsers(List<String> usernames) {
        users.clear();
        users.addAll(usernames);
        refresh();
    }

    public void clear() {
        users.clear();
        refresh();
    }

    private void refresh() {
        headerLabel.setText("  Online  " + users.size());
        listPanel.repaint();
        listPanel.revalidate();
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setBackground(ChatTheme.BG_PANEL);
        bar.setPreferredSize(new Dimension(3, 0));
    }

    // ── Inner: custom list renderer ───────────────────────────────────────────

    private final class UsersListPanel extends JPanel {

        private static final int ROW_H    = 48;
        private static final int AVATAR_R = 32;

        UsersListPanel() {
            setBackground(ChatTheme.BG_PANEL);
            setOpaque(true);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, Math.max(users.size() * ROW_H, ROW_H));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int y = 0;
            for (String user : users) {
                // Separator
                g2.setColor(new Color(0x2A3942));
                g2.drawLine(0, y + ROW_H - 1, getWidth(), y + ROW_H - 1);

                // Avatar circle
                Color avatarColor = avatarColor(user);
                int ax = 12, ay = y + (ROW_H - AVATAR_R) / 2;
                g2.setColor(avatarColor);
                g2.fill(new Ellipse2D.Float(ax, ay, AVATAR_R, AVATAR_R));

                // Initial letter
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String initial = (user == null || user.isEmpty()) ? "?" : user.substring(0, 1).toUpperCase();
                int ix = ax + (AVATAR_R - fm.stringWidth(initial)) / 2;
                int iy = ay + (AVATAR_R + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(initial, ix, iy);

                // Username
                g2.setFont(ChatTheme.FONT_MESSAGE);
                g2.setColor(ChatTheme.TEXT_PRIMARY);
                g2.drawString(user, ax + AVATAR_R + 8,
                        y + (ROW_H + g2.getFontMetrics().getAscent()) / 2 - 2);

                y += ROW_H;
            }
            g2.dispose();
        }

        /** Deterministic color derived from username hash. */
        private Color avatarColor(String name) {
            int hash = name.hashCode() & 0x7FFFFFFF;
            Color[] palette = {
                ChatTheme.ACCENT, new Color(0x53BDEB), new Color(0xFC644D),
                new Color(0xFFA600), new Color(0x7C5DF9), new Color(0x37AEBE)
            };
            return palette[hash % palette.length];
        }
    }
}
