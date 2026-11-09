package com.example.askai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class ClaudeClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** history is a list of {role, content} pairs, ending with the newest user message. Blocking. */
    public static String ask(AIConfig cfg, List<String[]> history) throws Exception {
        return ask(cfg, history, cfg.systemPrompt);
    }

    public static String ask(AIConfig cfg, List<String[]> history, String system) throws Exception {
        String key = cfg.resolveApiKey();
        if (key.isEmpty()) {
            throw new IllegalStateException("No API key set. Edit config/askai.json or set ANTHROPIC_API_KEY.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model);
        body.addProperty("max_tokens", cfg.maxTokens);
        body.addProperty("system", system);
        JsonArray messages = new JsonArray();
        for (String[] m : history) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m[0]);
            o.addProperty("content", m[1]);
            messages.add(o);
        }
        body.add("messages", messages);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            String detail = res.body();
            try {
                detail = JsonParser.parseString(res.body()).getAsJsonObject()
                        .getAsJsonObject("error").get("message").getAsString();
            } catch (Exception ignored) {}
            throw new RuntimeException("API error " + res.statusCode() + ": " + detail);
        }

        JsonArray content = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("content");
        StringBuilder out = new StringBuilder();
        for (JsonElement el : content) {
            JsonObject block = el.getAsJsonObject();
            if ("text".equals(block.get("type").getAsString())) out.append(block.get("text").getAsString());
        }
        return out.toString().trim();
    }
}
