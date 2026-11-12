# Desktop Client UI Fixes Plan

## Overview

Deep scan + screenshot identified 4 issues to fix in the desktop client:

1. **Self-message bubbles left-aligned** — `addBubble()` always uses `FlowLayout.LEFT`; self-messages must be right-aligned.
2. **File download does not work** — `AttachBubble` uses `"Segoe UI Emoji"` font for icons which may not exist, causing layout corruption. Switch to safe Unicode symbols with `Dialog` font, and verify the Download button click path.
3. **Input bar buttons need icons** — Replace "File" / "Mic" / "Send" text labels with Unicode symbols that render reliably on any JVM.
4. **Online Users sidebar polish** — Add online count badge to the "Online" header; widen panel slightly (160→180px) so longer names are not clipped.

---

## Sub-Task 1 — Fix self-message right-alignment

**Status:** [ ] pending

**Intent:**  
`addBubble(JPanel)` wraps every bubble in a `FlowLayout(LEFT)` row, so own (green) messages appear on the left instead of the right. The wrapper row must use `FlowLayout.RIGHT` for self-messages.

**Root cause:**  
`MessagePanel.addBubble()` at line 106: `new FlowLayout(FlowLayout.LEFT, 0, 0)` — alignment is never changed based on `isSelf`.

**Expected Outcome:**  
- Own (green) bubbles appear right-aligned.
- Received (dark) bubbles remain left-aligned.
- File/audio bubbles sent by self are right-aligned; received are left-aligned.

**Todo List:**
1. Change `addBubble(JPanel panel)` signature to `addBubble(JPanel panel, boolean alignRight)`.
2. Inside `addBubble`, use `FlowLayout.RIGHT` when `alignRight=true`, else `FlowLayout.LEFT`.
3. Update `appendChatMessage()` to pass `isSelf` as the second arg.
4. Update `appendFileBubble()` to pass `sender == null` (self) as the second arg.
5. Update `appendAudioBubble()` to pass `sender == null` (self) as the second arg.
6. `appendNotification()` keeps center alignment — pass `false` (left is fine for system pills, or CENTER via a dedicated overload).

**Relevant Files:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MessagePanel.java` — `addBubble()`, `appendChatMessage()`, `appendFileBubble()`, `appendAudioBubble()`

---

## Sub-Task 2 — Fix file download / AttachBubble icon font

**Status:** [ ] pending

**Intent:**  
`AttachBubble` sets the icon `JLabel` font to `"Segoe UI Emoji"` which does not exist on most JVMs. When a font name is not found, Java silently falls back to `Dialog`, but the emoji codepoints `\uD83D\uDCC4` (📄) and `\uD83C\uDFA4` (🎤) still render as empty boxes on many systems, and the icon label takes wrong dimensions, pushing the Download/Play button out of the visible area.

**Fix:**
- Replace emoji chars with safe ASCII-range Unicode symbols: `\u2398` (⎘ document-like) is risky too. Best approach: use `▶` (`\u25B6`) for audio and `⬇` (`\u2B07`) for file (guaranteed in any font), or just use text-only labels.
- Actually safest: draw the icon as a custom `paintComponent` square icon with a dark background, or use simple ASCII glyphs `[F]` / `[♪]` that any font renders.
- Chosen approach: change icon to a styled `JLabel` with text `"📎"` rendered using `Font("Dialog", ...)` — Dialog font always exists. For file use `"≡"` (U+2261) and for audio use `"♪"` (U+266A) — both in every Unicode-capable font.

**Expected Outcome:**
- File and audio bubbles render with visible icon.
- Download button is fully visible and not obscured.
- Clicking Download opens a JFileChooser and saves the file.

**Todo List:**
1. In `AttachBubble`, change icon `JLabel` font from `new Font("Segoe UI Emoji", ...)` to `new Font("Dialog", Font.PLAIN, 18)`.
2. Change file icon char to `"\u2398"` or a safe fallback (`"[f]"`) — test: use `"\uD83D\uDCC4"` with `Dialog` font (Dialog includes basic emoji on modern JDKs via font fallback), OR use `"\u25A4"` (⊄ solid square with corner notch — guaranteed). Best safe choice: `"≡"` for file and `"♪"` for audio.
3. After icon fix, verify `AttachBubble` preferred-size calculation still fits content.

**Relevant Files:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MessagePanel.java` — `AttachBubble` inner class, lines 286–365

---

## Sub-Task 3 — Input bar button icons

**Status:** [ ] pending

**Intent:**  
Replace text labels "File", "Mic", "Send" with Unicode symbols that render reliably. Use `Dialog` font (always present). Chosen symbols:
- File button: `"\uD83D\uDCCE"` (📎 paperclip) — fall back to `"⊕"` if not rendered. Simplest safe choice: `"+"` is too generic. Use `"≡"` or a custom drawn icon. **Best approach**: draw mini SVG-style icons using `Graphics2D` in `paintComponent` — no font dependency at all.
- For simplicity and reliability: use plain ASCII alternatives that are styled to look like icons with the button background design.
  - File: `"\uD83D\uDCCE"` with Dialog font (paperclip, renders on Win10+ JDK17+)
  - Mic: `"\uD83C\uDFA4"` with Dialog font (microphone)
  - Send: draw an arrow `"▶"` (U+25B6)

**Expected Outcome:**
- All 3 buttons show meaningful icon-like symbols instead of abbreviated words.
- Symbols render without empty-box fallback.

**Todo List:**
1. In `createIconButton()`, change font to `new Font("Dialog", Font.PLAIN, 16)`.
2. For file button call: change label from `"File"` to `"\uD83D\uDCCE"` (📎).
3. For audio button call: change label from `"Mic"` to `"\uD83C\uDFA4"` (🎤).
4. In `setRecording()`, change `"Stop"` to `"\u23F9"` (⏹) and `"Mic"` back to `"\uD83C\uDFA4"`.
5. In `createSendButton()`, change label from `"Send"` to `"▶"` (U+25B6) — rendered via `drawString` already in `paintComponent`.
6. Widen buttons slightly (60→50px is fine for a single char icon).

**Relevant Files:**
- `client desktop/src/main/java/com/javachat/desktop/ui/MessageInputPanel.java`

---

## Sub-Task 4 — Online Users sidebar polish

**Status:** [ ] pending

**Intent:**  
- Widen sidebar from 160 to 180px so usernames are not clipped.
- Add a live user count next to the "Online" header (e.g. "Online  2").
- Make the "Online" count label update whenever `addUser`/`removeUser` is called.

**Expected Outcome:**
- Header shows "Online  N" where N is the current count.
- Names up to ~15 chars fit without clipping.

**Todo List:**
1. In `OnlineUsersPanel`, change `setPreferredSize` width from `160 → 180`.
2. Change `header` from a static `JLabel` to a field `headerLabel`.
3. In `refresh()`, update `headerLabel.setText("  Online  " + users.size())`.

**Relevant Files:**
- `client desktop/src/main/java/com/javachat/desktop/ui/OnlineUsersPanel.java`
