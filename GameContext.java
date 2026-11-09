package com.example.askai;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds the system prompt with live information from the running game. */
public final class GameContext {
    private static final String RULES = String.join("\n",
        "HOW TO ACT IN THE GAME (follow exactly):",
        "- Commands: put every Minecraft command on its own line that starts with / and has nothing else on it. "
            + "The player sees a button that runs them. Only use commands that exist (see the list above). "
            + "Say when cheats or operator permission are needed.",
        "- Items with enchantments use item components in this version, for example: "
            + "/give @s minecraft:netherite_chestplate[enchantments={protection:255}] . The highest allowed level is 255. "
            + "For a full armor set give helmet, chestplate, leggings and boots as separate lines.",
        "- Structures: build them with commands relative to the player using ~ coordinates, for example "
            + "/fill ~2 ~ ~2 ~12 ~6 ~12 minecraft:stone_bricks hollow , /setblock , /clone , or /place structure / "
            + "/place template for vanilla structures. Use at most 40 command lines and keep every /fill under 32768 blocks. "
            + "Tell the player where the structure will appear.",
        "- Controls: to change a Minecraft key binding, output a line exactly like: "
            + "KEYBIND key.sprint key.keyboard.left.control (action id, then key id). "
            + "Action ids: key.forward key.left key.back key.right key.jump key.sneak key.sprint key.inventory "
            + "key.swapOffhand key.drop key.use key.attack key.pickItem key.chat key.command key.playerlist "
            + "key.screenshot key.togglePerspective key.smoothCamera key.fullscreen key.advancements "
            + "key.hotbar.1 to key.hotbar.9. Key ids look like key.keyboard.r, key.keyboard.left.shift, "
            + "key.keyboard.f5, key.mouse.left, key.mouse.right, key.mouse.middle. "
            + "If the player did not say which key, ask them first.",
        "- Never suggest /stop, /op, /deop, /ban, /kick, /pardon or /whitelist.",
        "- Mod commands: you only know a mod's command names from the list above. If you are not sure how a mod "
            + "command works, say so and suggest /help or Tab to autocomplete instead of guessing.",
        "- Keep answers short. One friendly sentence before and after the commands is plenty.");

    public static String build(AIConfig cfg) {
        StringBuilder sb = new StringBuilder(cfg.systemPrompt);
        sb.append("\n\nLIVE GAME CONTEXT\n");
        sb.append("- Game: Minecraft Java Edition 26.3 with the Fabric mod loader.\n");

        try {
            List<String> mods = new ArrayList<>();
            for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
                String id = m.getMetadata().getId();
                if (id.startsWith("fabric-") || id.equals("minecraft") || id.equals("java")
                        || id.equals("fabricloader") || id.equals("mixinextras") || id.equals("mixin")) continue;
                mods.add(m.getMetadata().getName() + " (" + id + ")");
                if (mods.size() >= 40) break;
            }
            if (!mods.isEmpty()) sb.append("- Installed mods: ").append(String.join(", ", mods)).append("\n");
        } catch (Throwable ignored) {}

        try {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                Object d = conn.getClass().getMethod("getCommands").invoke(conn);
                if (d instanceof CommandDispatcher<?> dispatcher) {
                    List<String> names = new ArrayList<>();
                    for (CommandNode<?> n : dispatcher.getRoot().getChildren()) names.add(n.getName());
                    Collections.sort(names);
                    sb.append("- Commands this player can use right now (includes mod commands): ")
                      .append(String.join(" ", names)).append("\n");
                }
            }
        } catch (Throwable ignored) {}

        sb.append("\n").append(RULES);
        return sb.toString();
    }

    private GameContext() {}
}
