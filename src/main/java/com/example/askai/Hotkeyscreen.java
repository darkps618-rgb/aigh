package com.example.askai;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Asks the player which key should open the AI chat. Shown on first join and from the chat's Hotkey button. */
public class HotkeyScreen extends Screen {
    private int wx, wy, ww = 280, wh = 130;
    private String warning = "";

    public HotkeyScreen() {
        super(Component.literal("Set AI hotkey"));
    }

    @Override
    protected void init() {
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2;
        this.addRenderableWidget(Button.builder(
                Component.literal("Keep " + AskAIClient.config.hotkeyLabel), b -> this.onClose())
                .bounds(wx + (ww - 120) / 2, wy + wh - 30, 120, 20).build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k == 256) { this.onClose(); return true; }   // Esc keeps the current key
        String id = KeyNames.fromGlfw(k);
        if (id == null) {
            warning = "That key can't be used here. Try another one.";
            return true;
        }
        AskAIClient.rebind(id);
        this.onClose();
        return true;
    }

    private static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1 + 1, y1, x2 - 1, y1 + 1, c);
        g.fill(x1, y1 + 1, x2, y2 - 1, c);
        g.fill(x1 + 1, y2 - 1, x2 - 1, y2, c);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        round(g, wx, wy, wx + ww, wy + wh, 0xFF161922);
        round(g, wx, wy, wx + ww, wy + 30, 0xFF202533);
        g.fill(wx, wy + 30, wx + ww, wy + 31, 0xFF2F3650);
    }

    private void centered(GuiGraphicsExtractor g, String s, int y, int color) {
        g.text(this.font, s, wx + (ww - this.font.width(s)) / 2, y, color, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        LogoArt.draw(g, wx + 8, wy + 7, 1);
        g.text(this.font, "AI - choose your hotkey", wx + 30, wy + 11, 0xFFEEF1F7, false);
        centered(g, "Press the key you want to open the AI chat with.", wy + 44, 0xFFEEF1F7);
        centered(g, "Current key: " + AskAIClient.config.hotkeyLabel, wy + 58, 0xFF8FE3A5);
        centered(g, "Esc keeps the current key. Change it later with the Hotkey button.", wy + 72, 0xFF8FA0B8);
        if (!warning.isEmpty()) centered(g, warning, wy + 86, 0xFFFF8080);
    }
}
