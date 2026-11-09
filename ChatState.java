package com.example.askai;

import java.util.ArrayList;
import java.util.List;

/** Chat history lives here (not in the screen) so it survives closing and reopening the window. */
public final class ChatState {
    public static final int TEXT = 0, CMD = 1, KEY = 2, RUN = 3, APPLY = 4;

    public record Row(String text, int kind, String full) {}

    public static final class Msg {
        public final boolean ai;
        public final String text;
        public boolean api;                 // included when talking to the API
        public final boolean error;
        public final List<String> commands = new ArrayList<>();   // without the leading /
        public final List<String[]> keybinds = new ArrayList<>(); // {action id, key id}
        public boolean ran, applied;
        // layout cache, filled by the screen
        public int lw = -1, bw, bh;
        public List<Row> rows = List.of();

        Msg(boolean ai, String text, boolean api, boolean error) {
            this.ai = ai; this.text = text; this.api = api; this.error = error;
            if (ai && !error) {
                for (String line : text.split("\n")) {
                    String t = line.strip();
                    if (t.startsWith("/") && t.length() > 1) commands.add(t.substring(1).strip());
                    else {
                        String[] kb = parseKeybind(t);
                        if (kb != null) keybinds.add(kb);
                    }
                }
            }
        }
    }

    public static final List<Msg> MESSAGES = new ArrayList<>();
    public static volatile boolean waiting = false;
    public static int generation = 0;

    private static final String GREETING =
        "Hi there! I'm your AI helper. Ask me anything, from Minecraft questions to everyday ones. "
      + "I can give you commands, build structures, and change your controls. You choose when to run them.\n"
      + "Try: give me a netherite helmet with protection 255";

    static { reset(); }

    public static String[] parseKeybind(String t) {
        if (!t.regionMatches(true, 0, "KEYBIND ", 0, 8)) return null;
        String[] p = t.substring(8).trim().split("\\s+");
        return p.length >= 2 ? new String[]{p[0], p[1]} : null;
    }

    public static void reset() {
        generation++;
        waiting = false;
        MESSAGES.clear();
        MESSAGES.add(new Msg(true, GREETING, false, false));
    }

    public static Msg addUser(String text) {
        Msg m = new Msg(false, text, true, false);
        MESSAGES.add(m);
        return m;
    }

    /** Returns true if the result was applied (chat was not cleared meanwhile). */
    public static boolean finish(int gen, Msg userMsg, String answer, Throwable err) {
        if (gen != generation) return false;
        waiting = false;
        if (err != null) {
            userMsg.api = false;
            Throwable c = err.getCause() != null ? err.getCause() : err;
            String msg = c.getMessage() == null ? c.toString() : c.getMessage();
            MESSAGES.add(new Msg(true, "Sorry, something went wrong: " + msg, false, true));
        } else {
            String cleaned = clean(answer);
            MESSAGES.add(new Msg(true, cleaned.isBlank() ? "(no answer)" : cleaned, true, false));
        }
        return true;
    }

    public static List<String[]> apiHistory(int max) {
        List<String[]> out = new ArrayList<>();
        for (Msg m : MESSAGES) if (m.api) out.add(new String[]{m.ai ? "assistant" : "user", m.text});
        while (out.size() > max) out.remove(0);
        while (!out.isEmpty() && out.get(0)[0].equals("assistant")) out.remove(0);
        return out;
    }

    /** The most recent command line the AI suggested, or null. */
    public static String lastCommand() {
        for (int i = MESSAGES.size() - 1; i >= 0; i--) {
            Msg m = MESSAGES.get(i);
            if (!m.ai || m.error || m.commands.isEmpty()) continue;
            return "/" + m.commands.get(m.commands.size() - 1);
        }
        return null;
    }

    static String clean(String s) {
        String t = s.replace("\r", "").replace("**", "").replace("__", "").replace("`", "");
        t = t.replaceAll("(?m)^#+\\s*", "");
        t = t.replaceAll("(?m)^\\s*[-*]\\s+", "\u2022 ");
        return t.strip();
    }

    private ChatState() {}
}
