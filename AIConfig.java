package com.example.askai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class AIConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String apiKey = "PUT_YOUR_ANTHROPIC_API_KEY_HERE";
    public String model = "claude-sonnet-5-5";
    public int maxTokens = 1500;
    public int historyMessages = 14;
    public int maxCommandsPerRun = 80;
    public boolean hotkeyAsked = false;
    public String hotkeyLabel = "Insert";
    public String systemPrompt =
        "You are a warm, gentle and patient AI assistant chatting with a player inside Minecraft "
      + "Java Edition. Answer any question on any topic accurately and kindly, the way a thoughtful "
      + "friend would. If the player seems frustrated or stuck, be reassuring. Replies show in a small "
      + "chat window: keep them short and clear and use plain text only (no markdown, no asterisks, "
      + "no backticks, no headings).";

    public static AIConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("askai.json");
        try {
            if (Files.exists(path)) {
                AIConfig cfg = GSON.fromJson(Files.readString(path), AIConfig.class);
                if (cfg != null) return cfg;
            }
            AIConfig cfg = new AIConfig();
            Files.writeString(path, GSON.toJson(cfg));
            return cfg;
        } catch (Exception e) {
            AskAIClient.LOGGER.error("Could not read askai.json, using defaults", e);
            return new AIConfig();
        }
    }

    public void save() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("askai.json");
            Files.writeString(path, GSON.toJson(this));
        } catch (Exception e) {
            AskAIClient.LOGGER.error("Could not save askai.json", e);
        }
    }

    /** Config value first, then the ANTHROPIC_API_KEY environment variable. */
    public String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("PUT_YOUR")) return apiKey.trim();
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env == null ? "" : env.trim();
    }
}
