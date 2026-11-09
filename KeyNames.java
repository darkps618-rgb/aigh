package com.example.askai;

public final class KeyNames {
    /** GLFW key code -> Minecraft key id (e.g. 82 -> key.keyboard.r). Null if unsupported here. */
    public static String fromGlfw(int k) {
        if (k >= 65 && k <= 90) return "key.keyboard." + (char) (k + 32);
        if (k >= 48 && k <= 57) return "key.keyboard." + (char) k;
        if (k >= 290 && k <= 301) return "key.keyboard.f" + (k - 289);
        return switch (k) {
            case 32 -> "key.keyboard.space";
            case 39 -> "key.keyboard.apostrophe";
            case 44 -> "key.keyboard.comma";
            case 45 -> "key.keyboard.minus";
            case 46 -> "key.keyboard.period";
            case 47 -> "key.keyboard.slash";
            case 59 -> "key.keyboard.semicolon";
            case 61 -> "key.keyboard.equal";
            case 91 -> "key.keyboard.left.bracket";
            case 92 -> "key.keyboard.backslash";
            case 93 -> "key.keyboard.right.bracket";
            case 96 -> "key.keyboard.grave.accent";
            case 257 -> "key.keyboard.enter";
            case 258 -> "key.keyboard.tab";
            case 259 -> "key.keyboard.backspace";
            case 260 -> "key.keyboard.insert";
            case 261 -> "key.keyboard.delete";
            case 262 -> "key.keyboard.right";
            case 263 -> "key.keyboard.left";
            case 264 -> "key.keyboard.down";
            case 265 -> "key.keyboard.up";
            case 266 -> "key.keyboard.page.up";
            case 267 -> "key.keyboard.page.down";
            case 268 -> "key.keyboard.home";
            case 269 -> "key.keyboard.end";
            case 280 -> "key.keyboard.caps.lock";
            case 340 -> "key.keyboard.left.shift";
            case 341 -> "key.keyboard.left.control";
            case 342 -> "key.keyboard.left.alt";
            case 344 -> "key.keyboard.right.shift";
            case 345 -> "key.keyboard.right.control";
            case 346 -> "key.keyboard.right.alt";
            default -> null;
        };
    }

    /** key.keyboard.left.shift -> "left shift", key.sprint -> "sprint" */
    public static String pretty(String id) {
        return id.replace("key.keyboard.", "").replace("key.mouse.", "mouse ")
                 .replace("key.", "").replace('.', ' ');
    }

    public static String label(String id) {
        String p = pretty(id);
        return p.isEmpty() ? p : Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    private KeyNames() {}
}
