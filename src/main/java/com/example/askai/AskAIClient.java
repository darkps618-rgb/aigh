package com.example.askai;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.List;
import java.util.regex.Pattern;

public class AskAIClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("askai");
    public static AIConfig config;

    private static KeyMapping openKey;
    private static final ArrayDeque<String> QUEUE = new ArrayDeque<>();
    private static int gap, joinTicks, batchTotal;
    private static boolean building;

    // commands the AI may never run for you
    private static final Pattern BLOCKED = Pattern.compile(
        "(^|\\s)(stop|op|deop|ban|ban-ip|pardon|pardon-ip|kick|whitelist)(\\s|$)", Pattern.CASE_INSENSITIVE);

    @Override
    public void onInitializeClient() {
        config = AIConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("askai", "chat"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.askai.open", InputConstants.KEY_INSERT, category));

        ClientTickEvents.END_CLIENT_TICK.register(AskAIClient::tick);

        // logo + name in the top-right corner of the title screen
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> drawBadge(g, s.width));
            }
        });
        LOGGER.info("AI mod loaded.");
    }

    // ---------- per-tick work ----------

    private static void tick(Minecraft client) {
        while (openKey.consumeClick()) {
            if (client.player != null && client.gui.screen() == null) {
                client.gui.setScreen(new AIChatScreen());
            }
        }

        if (client.player == null) {
            joinTicks = 0;
            QUEUE.clear();
            building = false;
            return;
        }

        // first time in a world: ask which key to use
        if (!config.hotkeyAsked && client.gui.screen() == null) {
            if (++joinTicks > 60) {
                config.hotkeyAsked = true;
                config.save();
                client.gui.setScreen(new HotkeyScreen());
            }
        }

        // run queued commands slowly so servers don't see spam
        if (!QUEUE.isEmpty()) {
            if (++gap >= 2) {
                gap = 0;
                sendCommand(client, QUEUE.poll());
            }
            if (QUEUE.isEmpty() && building) {
                building = false;
                say(client, "[AI] Finished running " + batchTotal + " command(s).", ChatFormatting.AQUA);
            }
        }
    }

    private static void say(Minecraft mc, String text, ChatFormatting color) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static void sendCommand(Minecraft mc, String cmd) {
        var conn = mc.getConnection();
        if (conn == null) return;
        for (String name : new String[]{"sendCommand", "sendUnsignedCommand"}) {
            try {
                conn.getClass().getMethod(name, String.class).invoke(conn, cmd);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                LOGGER.warn("Command failed: {}", e.toString());
                return;
            }
        }
        say(mc, "[AI] Couldn't send commands in this game version.", ChatFormatting.RED);
        QUEUE.clear();
    }

    // ---------- called from the chat window ----------

    /** Queues the AI's commands. Returns how many were accepted. */
    public static int queueCommands(List<String> cmds) {
        Minecraft mc = Minecraft.getInstance();
        int added = 0, blocked = 0;
        for (String c : cmds) {
            if (added >= config.maxCommandsPerRun) break;
            if (BLOCKED.matcher(c).find()) { blocked++; continue; }
            QUEUE.add(c);
            added++;
        }
        if (blocked > 0) {
            say(mc, "[AI] Skipped " + blocked + " command(s) that could affect the server (stop/op/ban/kick/whitelist).",
                    ChatFormatting.YELLOW);
        }
        if (added > 0) {
            building = true;
            batchTotal = added;
            say(mc, "[AI] Running " + added + " command(s)...", ChatFormatting.AQUA);
        }
        return added;
    }

    /** Changes Minecraft key bindings. Returns a short summary. */
    public static String applyKeybinds(List<String[]> list) {
        int ok = 0, bad = 0;
        for (String[] kb : list) {
            try {
                Object km = KeyMapping.class.getMethod("get", String.class).invoke(null, kb[0]);
                if (km instanceof KeyMapping mapping) {
                    mapping.setKey(InputConstants.getKey(kb[1]));
                    ok++;
                } else {
                    bad++;
                }
            } catch (Exception e) {
                bad++;
            }
        }
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        return "Changed " + ok + " key" + (ok == 1 ? "" : "s") + (bad > 0 ? ", " + bad + " failed" : "");
    }

    /** Rebinds the "open AI chat" key. Returns the label shown to the player. */
    public static String rebind(String keyId) {
        openKey.setKey(InputConstants.getKey(keyId));
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        config.hotkeyLabel = KeyNames.label(keyId);
        config.save();
        return config.hotkeyLabel;
    }

    // ---------- title screen badge ----------

    private static void drawBadge(GuiGraphicsExtractor g, int screenW) {
        Minecraft mc = Minecraft.getInstance();
        int x = screenW - 38, y = 6;
        LogoArt.draw(g, x, y, 2);
        String name = "AI";
        g.text(mc.font, name, screenW - 6 - mc.font.width(name), y + 35, 0xFFFFFFFF, true);
        String hint = config.hotkeyLabel + " = AI chat";
        g.text(mc.font, hint, screenW - 6 - mc.font.width(hint), y + 46, 0xFFAAB4C8, true);
    }
}
