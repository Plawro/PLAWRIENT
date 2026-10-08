package dev.plawrient.gui;

import dev.plawrient.core.FontSetting;
import dev.plawrient.core.Fonts;
import dev.plawrient.core.ModuleManager;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Font picker: Minecraft fonts, already installed fonts and every .ttf/.otf found on this computer.
 * Choosing a system font copies it into resourcepacks/PLAWRIENT-fonts and reloads resources (a few seconds).
 */
public class FontPickerScreen extends Screen {
    private record Entry(String label, String id, Fonts.SystemFont sys, boolean preview) {}

    private static final int RH = 16, PW = 280;

    private final FontSetting setting;
    private final Screen parent;
    private final List<Entry> all = new ArrayList<>();
    private final StringBuilder search = new StringBuilder();
    private List<Entry> view = List.of();
    private int scroll = 0;

    public FontPickerScreen(FontSetting setting, Screen parent) {
        super(Component.literal("Select font"));
        this.setting = setting;
        this.parent = parent;

        for (String id : Fonts.VANILLA) all.add(new Entry("Minecraft: " + id.substring(id.indexOf(':') + 1), id, null, true));
        for (String id : Fonts.installed()) all.add(new Entry(id.substring(id.indexOf(':') + 1) + " (installed)", id, null, true));
        for (Fonts.SystemFont f : Fonts.systemFonts()) {
            String id = Fonts.NS + ":" + Fonts.slug(f.name());
            if (Fonts.installed().contains(id)) continue;
            all.add(new Entry(f.name(), id, f, false));
        }
        filter();
    }

    private void filter() {
        String q = search.toString().toLowerCase(Locale.ROOT);
        view = all.stream().filter(e -> q.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(q)).toList();
        scroll = 0;
    }

    private int top() { return 48; }
    private int rowsVisible() { return Math.max(1, (height - 24 - top()) / RH); }
    private int px() { return (width - PW) / 2; }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xAA000000, 0xDD0A0D18);
        int px = px();
        Draw.text(g, "Select font  (current: " + setting.display() + ")", px, 10, Draw.TEXT);

        Draw.rrect(g, px, 24, PW, 18, 9, 0xE60A0C11);
        String shown = search.length() == 0 ? "Search..." : search + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Draw.text(g, shown, px + 10, 29, search.length() == 0 ? Draw.TEXT_DIM : Draw.TEXT);

        for (int i = scroll; i < Math.min(view.size(), scroll + rowsVisible()); i++) {
            Entry e = view.get(i);
            int y = top() + (i - scroll) * RH;
            boolean hover = mx >= px && mx < px + PW && my >= y && my < y + RH - 1;
            boolean sel = e.id.equals(setting.get());
            Draw.rrect(g, px, y, PW, RH - 1, 4, hover ? Draw.ROW_HOVER : Draw.ROW);
            if (sel) Draw.rrect(g, px, y + 3, 2, RH - 7, 1, Draw.ACCENT);
            Draw.text(g, Draw.fit(e.label, 130), px + 8, y + 4, sel ? Draw.ACCENT : Draw.TEXT);
            if (e.preview) {
                ResourceLocation rl = ResourceLocation.tryParse(e.id);
                if (rl != null)
                    g.drawString(font, Component.literal("The quick brown fox").setStyle(Style.EMPTY.withFont(rl)),
                            px + 146, y + 4, Draw.TEXT_DIM, false);
            } else {
                Draw.text(g, "click to install", px + 146, y + 4, Draw.TEXT_DIM);
            }
        }
        Draw.text(g, "click = select   wheel = scroll   ESC = back", px, height - 14, Draw.TEXT_DIM);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int px = px();
        if (mx >= px && mx < px + PW && my >= top()) {
            int idx = scroll + (int) ((my - top()) / RH);
            if (idx >= 0 && idx < view.size() && idx < scroll + rowsVisible()) {
                Entry e = view.get(idx);
                minecraft.setScreen(parent);
                if (e.sys != null) {
                    Fonts.useSystemFont(setting, e.sys);
                } else {
                    setting.set(e.id);
                    ModuleManager.INSTANCE.save();
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, view.size() - rowsVisible());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta) * 3));
        return true;
    }

    @Override public boolean charTyped(char c, int mods) {
        if (c < 32 || c == 127) return false;
        search.append(c);
        filter();
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_BACKSPACE && search.length() > 0) {
            search.setLength(search.length() - 1);
            filter();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
