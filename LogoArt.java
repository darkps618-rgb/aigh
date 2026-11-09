package com.example.askai;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The 16x16 pixel logo, drawn with plain fills (no texture needed). */
public final class LogoArt {
    private static final String[] ROWS = {
        "BBBBBBBBBBBBBBBB",
        "B............y.B",
        "B...........sysB",
        "B............s.B",
        "B..hhhhhhhhh...B",
        "B.bbbbbbbbbbb..B",
        "B.bbbbbbbbbbb..B",
        "B.bbbwbwbwbbb..B",
        "B.bbbwbwbwbbb..B",
        "B.bbbbbbbbbbb..B",
        "B..ddddddddd...B",
        "B...bb.........B",
        "B...d..........B",
        "B.......ggggg..B",
        "B..............B",
        "BBBBBBBBBBBBBBBB",
    };

    private static int color(char c) {
        return switch (c) {
            case 'B' -> 0xFF3A3F55;
            case '.' -> 0xFF161922;
            case 'h' -> 0xFF6C9BF2;
            case 'b' -> 0xFF3A6FD8;
            case 'd' -> 0xFF2A52A6;
            case 'w' -> 0xFFFFFFFF;
            case 's' -> 0xFFD97757;
            case 'y' -> 0xFFFFD2A8;
            case 'g' -> 0xFF8FE3A5;
            default -> 0xFF000000;
        };
    }

    public static void draw(GuiGraphicsExtractor g, int x, int y, int scale) {
        for (int r = 0; r < 16; r++) {
            String row = ROWS[r];
            int c = 0;
            while (c < 16) {
                char ch = row.charAt(c);
                int e = c;
                while (e < 16 && row.charAt(e) == ch) e++;
                g.fill(x + c * scale, y + r * scale, x + e * scale, y + (r + 1) * scale, color(ch));
                c = e;
            }
        }
    }

    private LogoArt() {}
}
