package com.example.askai;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Messenger-style chat window: AI bubbles on the left, yours on the right. */
public class AIChatScreen extends Screen {
    private static final int HEADER = 28, FOOTER = 32, PAD = 6, GAP = 6, AV = 18, SB = 5;
    private static final int A_COPY = 0, A_RUN = 1, A_APPLY = 2;

    // colours (ARGB)
    private static final int C_WINDOW = 0xFF161922, C_HEADER = 0xFF202533, C_LINE = 0xFF2F3650;
    private static final int C_AI = 0xFF2B3144, C_USER = 0xFF3A6FD8, C_ERR = 0xFF5A2A2F;
    private static final int C_CMD = 0xFF10131A, C_CMD_HOVER = 0xFF1C2232, C_CMD_TEXT = 0xFF8FE3A5;
    private static final int C_KEYROW = 0xFF2A2618, C_KEYTEXT = 0xFFFFD86B;
    private static final int C_RUN = 0xFF2E7D4F, C_RUN_HOVER = 0xFF3A9A63, C_RUN_DONE = 0xFF3B4254;
    private static final int C_PILL = 0xFF262B3B;
    private static final int C_TEXT = 0xFFEEF1F7, C_MUTED = 0xFF8FA0B8, C_ONLINE = 0xFF3DDC84;

    private record Hit(int x1, int y1, int x2, int y2, int action, String text, ChatState.Msg msg) {}

    private EditBox input;
    private Button sendButton;
    private int wx, wy, ww, wh, areaX, areaY, areaW, areaH;
    private int scroll = 0, lastKey = -1;
    private final List<Hit> hits = new ArrayList<>();
    private String toast;
    private long toastUntil;

    public AIChatScreen() {
        super(Component.literal("AI"));
    }

    @Override
    protected void init() {
        ww = Math.min(this.width - 20, 400);
        wh = Math.min(this.height - 20, 280);
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2;
        areaX = wx + 8;
        areaY = wy + HEADER + 4;
        areaW = ww - 16;
        areaH = wh - HEADER - FOOTER - 8;

        String keep = input == null ? "" : input.getValue();
        input = new EditBox(this.font, wx + 14, wy + wh - 22, ww - 82, 12, Component.literal("Message"));
        input.setBordered(false);
        input.setMaxLength(500);
        input.setValue(keep);
        this.addRenderableWidget(input);
        this.setInitialFocus(input);

        sendButton = Button.builder(Component.literal("Send"), b -> send())
                .bounds(wx + ww - 58, wy + wh - 26, 50, 20).build();
        this.addRenderableWidget(sendButton);

        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> {
            ChatState.reset();
            scroll = 0;
        }).bounds(wx + ww - 48, wy + 4, 40, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Copy cmd"), b -> {
            String c = ChatState.lastCommand();
            if (c == null) showToast("No command to copy yet");
            else copy(c);
        }).bounds(wx + ww - 110, wy + 4, 58, 20).build());

        if (ww >= 330) {
            this.addRenderableWidget(Button.builder(Component.literal("Hotkey"),
                    b -> Minecraft.getInstance().gui.setScreen(new HotkeyScreen()))
                    .bounds(wx + ww - 160, wy + 4, 46, 20).build());
        }
    }

    // ---------- sending ----------

    private void send() {
        String q = input.getValue().trim();
        if (q.isEmpty() || ChatState.waiting) return;
        input.setValue("");

        final int gen = ChatState.generation;
        final ChatState.Msg userMsg = ChatState.addUser(q);
        ChatState.waiting = true;
        final AIConfig cfg = AskAIClient.config;
        final List<String[]> history = ChatState.apiHistory(cfg.historyMessages);
        final String system = GameContext.build(cfg);      // live info: mods, commands
        final Minecraft mc = Minecraft.getInstance();

        CompletableFuture.supplyAsync(() -> {
            try {
                return ClaudeClient.ask(cfg, history, system);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((answer, err) -> mc.execute(() -> {
            boolean applied = ChatState.finish(gen, userMsg, answer, err);
            if (applied && mc.player != null && !(mc.gui.screen() instanceof AIChatScreen)) {
                mc.player.sendSystemMessage(Component.literal("[AI] I've replied - open the AI chat to read it.")
                        .withStyle(ChatFormatting.AQUA));
            }
        }));
    }

    private void copy(String cmd) {
        this.minecraft.keyboardHandler.setClipboard(cmd);
        showToast("Copied! Paste it into game chat");
    }

    private void showToast(String t) {
        toast = t;
        toastUntil = System.currentTimeMillis() + 1800;
    }

    private void runCommands(ChatState.Msg m) {
        if (m.ran) { showToast("Already sent"); return; }
        int n = AskAIClient.queueCommands(m.commands);
        m.ran = true;
        m.lw = -1;
        if (n > 0) this.onClose();            // close so you can watch it happen
        else showToast("Nothing was run");
    }

    private void applyKeys(ChatState.Msg m) {
        if (m.applied) { showToast("Already applied"); return; }
        String summary = AskAIClient.applyKeybinds(m.keybinds);
        m.applied = true;
        m.lw = -1;
        showToast(summary);
    }

    // ---------- input ----------

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) { send(); return true; }
        if (k == InputConstants.KEY_INSERT) { this.onClose(); return true; }
        if (k == InputConstants.KEY_PAGEUP) { scroll += Math.max(20, areaH - 20); return true; }
        if (k == InputConstants.KEY_PAGEDOWN) { scroll -= Math.max(20, areaH - 20); return true; }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll += (int) Math.round(scrollY * 14);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (my >= areaY && my <= areaY + areaH) {
            for (Hit h : hits) {
                if (mx >= h.x1 && mx <= h.x2 && my >= h.y1 && my <= h.y2) {
                    switch (h.action) {
                        case A_RUN -> runCommands(h.msg);
                        case A_APPLY -> applyKeys(h.msg);
                        default -> copy(h.text);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    // ---------- drawing ----------

    private static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1 + 1, y1, x2 - 1, y1 + 1, c);
        g.fill(x1, y1 + 1, x2, y2 - 1, c);
        g.fill(x1 + 1, y2 - 1, x2 - 1, y2, c);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        round(g, wx, wy, wx + ww, wy + wh, C_WINDOW);
        round(g, wx, wy, wx + ww, wy + HEADER, C_HEADER);
        g.fill(wx, wy + HEADER, wx + ww, wy + HEADER + 1, C_LINE);
        round(g, wx + 8, wy + wh - 26, wx + ww - 62, wy + wh - 6, C_PILL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        sendButton.active = !ChatState.waiting;

        LogoArt.draw(g, wx + 10, wy + 6, 1);
        g.text(this.font, "AI", wx + 34, wy + 6, C_TEXT, false);
        g.fill(wx + 34, wy + 18, wx + 38, wy + 22, C_ONLINE);
        g.text(this.font, ChatState.waiting ? "typing..." : "online", wx + 42, wy + 16, C_MUTED, false);

        if (input.getValue().isEmpty()) {
            g.text(this.font, "Ask me anything...", wx + 14, wy + wh - 20, 0xFF6B7488, false);
        }

        drawMessages(g, mouseX, mouseY);

        if (toast != null && System.currentTimeMillis() < toastUntil) {
            int tw = this.font.width(toast) + 10;
            int tx = wx + (ww - tw) / 2, ty = areaY + areaH - 15;
            round(g, tx, ty, tx + tw, ty + 13, 0xEE000000);
            g.text(this.font, toast, tx + 5, ty + 3, 0xFFFFFFFF, false);
        }
    }

    private void drawMessages(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        hits.clear();
        List<ChatState.Msg> msgs = ChatState.MESSAGES;
        int maxBubble = (int) ((areaW - AV - SB - 8) * 0.9);
        int lh = this.font.lineHeight;

        int total = 0;
        for (ChatState.Msg m : msgs) {
            layout(m, maxBubble);
            total += m.bh + GAP;
        }
        int typingH = lh + 2 * PAD;
        if (ChatState.waiting) total += typingH + GAP;
        total = Math.max(0, total - GAP);

        int key = msgs.size() * 2 + (ChatState.waiting ? 1 : 0);
        if (key != lastKey) { scroll = 0; lastKey = key; }
        int maxScroll = Math.max(0, total - areaH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int y = total <= areaH ? areaY : areaY + areaH - total + scroll;

        g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
        for (ChatState.Msg m : msgs) {
            if (y + m.bh >= areaY && y <= areaY + areaH) drawBubble(g, m, y, mouseX, mouseY);
            y += m.bh + GAP;
        }
        if (ChatState.waiting) {
            int dots = 1 + (int) ((System.currentTimeMillis() / 350) % 3);
            int bw = this.font.width("...") + 2 * PAD;
            int bx = areaX + AV + 4;
            LogoArt.draw(g, areaX + 1, y + 1, 1);
            round(g, bx, y, bx + bw, y + typingH, C_AI);
            g.text(this.font, ".".repeat(dots), bx + PAD, y + PAD, C_TEXT, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int thumbH = Math.max(12, areaH * areaH / total);
            int thumbY = areaY + (int) ((areaH - thumbH) * (1.0 - (double) scroll / maxScroll));
            g.fill(areaX + areaW - 3, thumbY, areaX + areaW, thumbY + thumbH, 0x66FFFFFF);
        }
    }

    private void drawBubble(GuiGraphicsExtractor g, ChatState.Msg m, int y, int mouseX, int mouseY) {
        int lh = this.font.lineHeight;
        int bx = m.ai ? areaX + AV + 4 : areaX + areaW - SB - m.bw;
        if (m.ai) LogoArt.draw(g, areaX + 1, y + 1, 1);
        round(g, bx, y, bx + m.bw, y + m.bh, m.error ? C_ERR : (m.ai ? C_AI : C_USER));

        int ty = y + PAD;
        for (ChatState.Row r : m.rows) {
            int x1 = bx + PAD - 2, x2 = bx + m.bw - PAD + 2;
            switch (r.kind()) {
                case ChatState.CMD -> {
                    int y2 = ty + lh + 3;
                    boolean hover = over(mouseX, mouseY, x1, ty, x2, y2);
                    round(g, x1, ty, x2, y2, hover ? C_CMD_HOVER : C_CMD);
                    g.text(this.font, r.text(), bx + PAD + 2, ty + 2, C_CMD_TEXT, false);
                    hits.add(new Hit(x1, ty, x2, y2, A_COPY, r.full(), m));
                    ty += lh + 4;
                }
                case ChatState.KEY -> {
                    round(g, x1, ty, x2, ty + lh + 3, C_KEYROW);
                    g.text(this.font, r.text(), bx + PAD + 2, ty + 2, C_KEYTEXT, false);
                    ty += lh + 4;
                }
                case ChatState.RUN, ChatState.APPLY -> {
                    boolean run = r.kind() == ChatState.RUN;
                    boolean done = run ? m.ran : m.applied;
                    int y2 = ty + lh + 6;
                    boolean hover = !done && over(mouseX, mouseY, x1, ty, x2, y2);
                    round(g, x1, ty, x2, y2, done ? C_RUN_DONE : (hover ? C_RUN_HOVER : C_RUN));
                    g.text(this.font, r.text(), bx + PAD + 4, ty + 4, 0xFFFFFFFF, false);
                    hits.add(new Hit(x1, ty, x2, y2, run ? A_RUN : A_APPLY, null, m));
                    ty += lh + 8;
                }
                default -> {
                    if (r.text().isEmpty()) {
                        ty += 4;
                    } else {
                        g.text(this.font, r.text(), bx + PAD, ty, C_TEXT, false);
                        ty += lh + 1;
                    }
                }
            }
        }
    }

    private boolean over(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2 && my >= areaY && my <= areaY + areaH;
    }

    // ---------- layout / wrapping ----------

    private void layout(ChatState.Msg m, int maxBubble) {
        if (m.lw == maxBubble) return;
        int lh = this.font.lineHeight;
        int textMax = maxBubble - 2 * PAD;
        List<ChatState.Row> rows = new ArrayList<>();
        boolean aiText = m.ai && !m.error;
        for (String raw : m.text.split("\n", -1)) {
            String t = raw.strip();
            String[] kb = aiText ? ChatState.parseKeybind(t) : null;
            if (t.isEmpty()) {
                if (!rows.isEmpty() && !rows.get(rows.size() - 1).text().isEmpty())
                    rows.add(new ChatState.Row("", ChatState.TEXT, null));
            } else if (aiText && t.startsWith("/") && t.length() > 1) {
                for (String p : hardWrap(t, textMax - 8)) rows.add(new ChatState.Row(p, ChatState.CMD, t));
            } else if (kb != null) {
                String label = "Set " + KeyNames.pretty(kb[0]) + " to " + KeyNames.pretty(kb[1]);
                for (String p : wrapWords(label, textMax - 8)) rows.add(new ChatState.Row(p, ChatState.KEY, null));
            } else {
                for (String p : wrapWords(t, textMax)) rows.add(new ChatState.Row(p, ChatState.TEXT, null));
            }
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).text().isEmpty()) rows.remove(rows.size() - 1);

        int nc = m.commands.size(), nk = m.keybinds.size();
        if (nc > 0 || nk > 0) rows.add(new ChatState.Row("", ChatState.TEXT, null));
        if (nc > 0) {
            String label = m.ran ? "Sent " + nc + " command" + (nc == 1 ? "" : "s")
                                 : "Run " + nc + " command" + (nc == 1 ? "" : "s");
            rows.add(new ChatState.Row(label, ChatState.RUN, null));
        }
        if (nk > 0) {
            String label = m.applied ? "Key changes applied"
                                     : "Apply " + nk + " key change" + (nk == 1 ? "" : "s");
            rows.add(new ChatState.Row(label, ChatState.APPLY, null));
        }

        int w = 0, h = 0;
        for (ChatState.Row r : rows) {
            int extra = switch (r.kind()) {
                case ChatState.CMD, ChatState.KEY -> 8;
                case ChatState.RUN, ChatState.APPLY -> 12;
                default -> 0;
            };
            w = Math.max(w, this.font.width(r.text()) + extra);
            h += switch (r.kind()) {
                case ChatState.CMD, ChatState.KEY -> lh + 4;
                case ChatState.RUN, ChatState.APPLY -> lh + 8;
                default -> r.text().isEmpty() ? 4 : lh + 1;
            };
        }
        m.rows = rows;
        m.bw = Math.max(w + 2 * PAD, 28);
        m.bh = Math.max(h + 2 * PAD - 1, lh + 2 * PAD);
        m.lw = maxBubble;
    }

    private List<String> wrapWords(String line, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : line.split(" ")) {
            if (word.isEmpty()) continue;
            String cand = cur.length() == 0 ? word : cur + " " + word;
            if (this.font.width(cand) <= max) {
                cur.setLength(0);
                cur.append(cand);
                continue;
            }
            if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            if (this.font.width(word) <= max) {
                cur.append(word);
            } else {
                List<String> parts = hardWrap(word, max);
                for (int i = 0; i < parts.size() - 1; i++) out.add(parts.get(i));
                cur.append(parts.get(parts.size() - 1));
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private List<String> hardWrap(String s, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            cur.append(s.charAt(i));
            if (this.font.width(cur.toString()) > max && cur.length() > 1) {
                char last = cur.charAt(cur.length() - 1);
                cur.setLength(cur.length() - 1);
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(last);
            }
        }
        if (cur.length() > 0 || out.isEmpty()) out.add(cur.toString());
        return out;
    }
}
