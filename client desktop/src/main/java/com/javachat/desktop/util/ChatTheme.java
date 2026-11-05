package com.javachat.desktop.util;

import java.awt.Color;
import java.awt.Font;

/**
 * Centralized WhatsApp-style color and font palette.
 * All UI classes use these constants — change once, updates everywhere.
 */
public final class ChatTheme {

    // ── Background colors ─────────────────────────────────────────────────────
    /** Main chat area background */
    public static final Color BG_MAIN        = new Color(0x0D1117);
    /** Sidebar / panels background */
    public static final Color BG_PANEL       = new Color(0x161B22);
    /** Header strip */
    public static final Color BG_HEADER      = new Color(0x1C2526);
    /** Input bar background */
    public static final Color BG_INPUT       = new Color(0x1C2526);
    /** Login screen background */
    public static final Color BG_LOGIN       = new Color(0x111B21);

    // ── Bubble colors ─────────────────────────────────────────────────────────
    /** Sent message bubble (own messages) */
    public static final Color BUBBLE_SENT    = new Color(0x005C4B);
    /** Received message bubble */
    public static final Color BUBBLE_RECV    = new Color(0x202C33);

    // ── Text colors ───────────────────────────────────────────────────────────
    public static final Color TEXT_PRIMARY   = new Color(0xE9EDEF);
    public static final Color TEXT_SECONDARY = new Color(0x8696A0);
    public static final Color TEXT_TIMESTAMP = new Color(0x667781);
    public static final Color TEXT_SENDER    = new Color(0x53BDEB);
    public static final Color TEXT_SYSTEM    = new Color(0x8696A0);

    // ── Accent ────────────────────────────────────────────────────────────────
    /** WhatsApp brand green */
    public static final Color ACCENT         = new Color(0x00A884);
    public static final Color ACCENT_HOVER   = new Color(0x00CF9D);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    public static final Font FONT_MESSAGE    = new Font("Segoe UI", Font.PLAIN,  13);
    public static final Font FONT_SENDER     = new Font("Segoe UI", Font.BOLD,   12);
    public static final Font FONT_TIMESTAMP  = new Font("Segoe UI", Font.PLAIN,  10);
    public static final Font FONT_SYSTEM     = new Font("Segoe UI", Font.ITALIC, 11);
    public static final Font FONT_HEADER     = new Font("Segoe UI", Font.BOLD,   14);
    public static final Font FONT_INPUT      = new Font("Segoe UI", Font.PLAIN,  13);
    public static final Font FONT_TITLE      = new Font("Segoe UI", Font.BOLD,   22);

    private ChatTheme() {}
}
