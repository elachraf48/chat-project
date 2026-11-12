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
    // ACCENT_HOVER reserved for future hover states — not currently used
    // public static final Color ACCENT_HOVER   = new Color(0x00CF9D);

    // ── Structural / component colors ─────────────────────────────────────────
    /** Separator lines and hover highlight in sidebars */
    public static final Color BORDER         = new Color(0x2A3942);
    /** System message bubble background (notification pills) */
    public static final Color BG_SYSTEM_BUBBLE = new Color(0x2A3942);
    /** AI response bubble background */
    public static final Color BG_AI_BUBBLE   = new Color(0x1A2A32);
    /** AI bubble gradient end shade */
    public static final Color BG_AI_STRIPE   = new Color(0x223340);
    /** Disabled/inactive button overlay */
    public static final Color BTN_DISABLED   = new Color(0x445566);

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
