package dev.plawrient.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** 2D helpery: hladke zaoblene obdelniky (kresli se ve fyzickych pixelech), text ve vybranem UI fontu. */
public final class Draw {
    private Draw() {}

    public static final int ACCENT = 0xFF4A7BFF;
    public static final int ACCENT2 = 0xFFB45CFF;
    public static final int PANEL = 0xE60F1117;
    public static final int PANEL_HOVER = 0xE61B1F2A;
    public static final int ROW = 0xCC14171E;
    public static final int ROW_HOVER = 0xCC1E2330;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF8B93A5;

    private static int k() { return Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale()); }

    /** Pozadi s danou pruhlednosti (0 = pruhledne, 1 = plne). */
    public static int bg(double opacity) {
        return ((int) (Mth.clamp(opacity, 0.0, 1.0) * 255) << 24) | 0x0F1117;
    }

    public static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0 || w <= 0 || h <= 0) return;
        int k = k();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(1f / k, 1f / k, 1f);
        int X = x * k, Y = y * k, W = w * k, H = h * k;
        int R = Math.min(r * k, Math.min(W, H) / 2);
        if (R <= 0) {
            g.fill(X, Y, X + W, Y + H, color);
        } else {
            g.fill(X, Y + R, X + W, Y + H - R, color);
            for (int i = 0; i < R; i++) {
                double dy = R - i - 0.5;
                int inset = (int) Math.ceil(R - Math.sqrt(R * R - dy * dy));
                g.fill(X + inset, Y + i, X + W - inset, Y + i + 1, color);
                g.fill(X + inset, Y + H - i - 1, X + W - inset, Y + H - i, color);
            }
        }
        ps.popPose();
    }

    // ---------- text ----------
    public static Component component(String s) { return Component.literal(s).setStyle(Fonts.style()); }
    public static int width(String s) { return Minecraft.getInstance().font.width(component(s)); }

    public static void text(GuiGraphics g, String s, int x, int y, int color) { text(g, s, x, y, color, false); }
    public static void text(GuiGraphics g, String s, int x, int y, int color, boolean shadow) {
        g.drawString(Minecraft.getInstance().font, component(s), x, y, color, shadow);
    }
    public static void centered(GuiGraphics g, String s, int cx, int y, int color, boolean shadow) {
        text(g, s, cx - width(s) / 2, y, color, shadow);
    }

    public static String fit(String s, int maxW) {
        if (width(s) <= maxW) return s;
        while (s.length() > 1 && width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    // ---------- ESP ----------
    /** "Lock-on" ramecek: rohy + jemna vypln. */
    public static void brackets(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        int len = Math.max(3, Math.min(8, Math.min(x2 - x1, y2 - y1) / 3));
        g.fill(x1, y1, x2, y2, (c & 0x00FFFFFF) | 0x1A000000);
        corners(g, x1, y1, x2, y2, len, c);
    }

    public static void corners(GuiGraphics g, int x1, int y1, int x2, int y2, int len, int c) {
        g.fill(x1, y1, x1 + len, y1 + 1, c); g.fill(x1, y1, x1 + 1, y1 + len, c);
        g.fill(x2 - len, y1, x2, y1 + 1, c); g.fill(x2 - 1, y1, x2, y1 + len, c);
        g.fill(x1, y2 - 1, x1 + len, y2, c); g.fill(x1, y2 - len, x1 + 1, y2, c);
        g.fill(x2 - len, y2 - 1, x2, y2, c); g.fill(x2 - 1, y2 - len, x2, y2, c);
    }

    public static int lerp(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    /** Barevna vinetace kolem okraju obrazovky (s = 0..1). */
    public static void vignette(GuiGraphics g, int rgb, float s) {
        int w = g.guiWidth(), h = g.guiHeight();
        int t = Math.max(2, (int) (Math.min(w, h) * 0.012));
        int n = 22;
        for (int i = 0; i < n; i++) {
            float k = 1f - i / (float) n;
            int al = Math.min(255, (int) (k * k * 170 * s));
            if (al < 4) continue;
            int c = (al << 24) | (rgb & 0xFFFFFF);
            int o = i * t;
            g.fill(o, o, w - o, o + t, c);
            g.fill(o, h - o - t, w - o, h - o, c);
            g.fill(o, o + t, o + t, h - o - t, c);
            g.fill(w - o - t, o + t, w - o, h - o - t, c);
        }
    }
}
