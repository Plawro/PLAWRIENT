package dev.plawrient.gui;

import dev.plawrient.module.MusicSwitcher;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The "Music" tab of the menu: a small Spotify-like player for the MusicSwitcher module.
 * Now-playing card with a disc, play/stop, previous / next, playlist mode, volume, a searchable song list with
 * category chips, and buttons to open the custom songs folder / reload songs.
 */
public final class MusicPanel {
    private static final int W = 340;
    private static final String[] CATS = {"All", "Game", "Creative", "Menu", "Nether", "End", "Discs", "Custom"};

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final StringBuilder filter = new StringBuilder();
    private String cat = "All";
    private int scroll;
    private int listX, listY, listW, listH, volX, volW, contentH;
    private boolean draggingVol;

    // ---------- helpers ----------
    private static String kindOf(String path) {
        if (path.startsWith("custom/")) return "Custom";
        if (path.startsWith("records/")) return "Discs";
        if (path.startsWith("music/game/creative")) return "Creative";
        if (path.startsWith("music/game/nether")) return "Nether";
        if (path.startsWith("music/game/end")) return "End";
        if (path.startsWith("music/menu")) return "Menu";
        return "Game";
    }

    private static ItemStack iconFor(String path) {
        String kind = kindOf(path);
        Item it = switch (kind) {
            case "Discs" -> {
                Item d = BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "music_disc_" + path.substring(8)));
                yield d == Items.AIR ? Items.MUSIC_DISC_CAT : d;
            }
            case "Creative" -> Items.PAINTING;
            case "Menu" -> Items.BOOK;
            case "Nether" -> Items.NETHERRACK;
            case "End" -> Items.END_STONE;
            case "Custom" -> Items.JUKEBOX;
            default -> Items.GRASS_BLOCK;
        };
        return new ItemStack(it);
    }

    private static String time(int s) { return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60); }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, boolean active, Runnable r) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        Draw.rrect(g, x, y, w, h, 5, active ? 0xFF2A4BB8 : hover ? 0xFF2B3042 : 0xFF1B1F2B);
        Draw.centered(g, label, x + w / 2, y + (h - 8) / 2, active ? Draw.TEXT : hover ? Draw.TEXT : Draw.TEXT_DIM, false);
        hits.add(new Hit(x, y, w, h, r));
    }

    private static void setVolume(double v) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(Mth.clamp(v, 0.0, 1.0));
    }

    // ---------- render ----------
    public void render(GuiGraphics g, int mx, int my, int sw, int sh) {
        hits.clear();
        MusicSwitcher ms = MusicSwitcher.get();
        Minecraft mc = Minecraft.getInstance();
        if (ms == null) return;

        int x0 = sw / 2 - W / 2, y0 = 50;
        int H = Math.max(200, Math.min(sh - 66, 300));
        Draw.rrect(g, x0, y0, W, H, 8, 0xE60F1117);

        // ----- now playing card -----
        String now = ms.nowLabel();
        String sel = ms.selected();
        String shown = now != null ? now : sel.equals("Silence") ? ms.lastTrack() : sel;
        boolean playing = now != null;
        String[] cur = null;
        for (String[] t : ms.trackList()) if (t[0].equals(shown)) cur = t;

        Draw.rrect(g, x0 + 8, y0 + 8, W - 16, 60, 7, 0xFF151924);
        Draw.rrect(g, x0 + 14, y0 + 14, 48, 48, 24, playing ? 0xFF0B0D13 : 0xFF11141C);
        Draw.rrect(g, x0 + 30, y0 + 30, 16, 16, 8, playing ? Draw.ACCENT : 0xFF2A3042);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x0 + 30, y0 + 30, 0);
        pose.scale(1f, 1f, 1f);
        g.renderItem(cur != null ? iconFor(cur[1]) : new ItemStack(Items.JUKEBOX), 0, 0);
        pose.popPose();

        String title = playing || cur != null ? shown : sel;
        Draw.text(g, Draw.fit(title, W - 70 - 120), x0 + 72, y0 + 14, Draw.TEXT, true);
        String sub = playing ? "Playing  -  " + time(ms.elapsedSeconds())
                : sel.startsWith("Vanilla") ? "Vanilla music (random)" : sel.equals("Silence") ? "Stopped" : "Waiting...";
        Draw.text(g, sub, x0 + 72, y0 + 26, Draw.TEXT_DIM);

        // equalizer
        long ms2 = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            int bh = playing ? 3 + (int) (Math.abs(Math.sin(ms2 / 170.0 + i * 1.4)) * 14) : 2;
            Draw.rrect(g, x0 + W - 62 + i * 8, y0 + 34 - bh, 5, bh, 2, Draw.lerp(Draw.ACCENT, Draw.ACCENT2, i / 5f));
        }

        // controls
        int cy = y0 + 44, cx = x0 + 72;
        button(g, mx, my, cx, cy, 24, 16, "|<", false, () -> ms.step(-1));
        button(g, mx, my, cx + 28, cy, 44, 16, playing || sel.equals("Random") ? "Stop" : "Play", playing, ms::playStop);
        button(g, mx, my, cx + 76, cy, 24, 16, ">|", false, () -> ms.step(1));
        button(g, mx, my, cx + 104, cy, 84, 16, ms.playlist.get(), false, ms::cyclePlaylist);

        // volume (the game's "Music" volume)
        double vol = mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get();
        volW = 64; volX = x0 + W - 16 - volW - 8;
        Draw.text(g, "Vol", volX - 22, cy + 4, Draw.TEXT_DIM);
        Draw.rrect(g, volX, cy + 6, volW, 4, 2, 0xFF23262F);
        Draw.rrect(g, volX, cy + 6, Math.max(2, (int) (volW * vol)), 4, 2, Draw.ACCENT);
        Draw.rrect(g, volX + (int) (volW * vol) - 2, cy + 3, 5, 10, 2, Draw.TEXT);
        hits.add(new Hit(volX - 3, cy, volW + 6, 16, () -> { draggingVol = true; }));

        // ----- search + chips -----
        int sy = y0 + 74;
        Draw.rrect(g, x0 + 8, sy, W - 16, 14, 7, 0xE60A0C11);
        String q = filter.length() == 0 ? "Type to search songs" : filter + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Draw.text(g, q, x0 + 16, sy + 3, filter.length() == 0 ? Draw.TEXT_DIM : Draw.TEXT);

        int chx = x0 + 8, chy = sy + 18;
        for (String c : CATS) {
            int cw = Draw.width(c) + 10;
            final String cc = c;
            button(g, mx, my, chx, chy, cw, 13, c, cat.equals(c), () -> { cat = cc; scroll = 0; });
            chx += cw + 3;
        }

        // ----- list -----
        listX = x0 + 8; listY = chy + 17; listW = W - 16; listH = y0 + H - 24 - listY;
        List<String[]> rows = new ArrayList<>();
        String fl = filter.toString().toLowerCase(Locale.ROOT);
        for (String[] t : ms.trackList()) {
            if (!cat.equals("All") && !kindOf(t[1]).equals(cat)) continue;
            if (!fl.isEmpty() && !t[0].toLowerCase(Locale.ROOT).contains(fl)) continue;
            rows.add(t);
        }
        final int RH = 18;
        contentH = rows.size() * RH;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - listH));
        Draw.rrect(g, listX, listY, listW, listH, 6, 0xB00B0D13);
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        if (rows.isEmpty()) {
            Draw.centered(g, cat.equals("Custom") ? "No custom songs yet - use \"Open folder\" below" : "Nothing found",
                    listX + listW / 2, listY + 8, Draw.TEXT_DIM, false);
        }
        for (int i = 0; i < rows.size(); i++) {
            String[] t = rows.get(i);
            int ry = listY + i * RH - scroll;
            if (ry + RH < listY || ry > listY + listH) continue;
            boolean isCur = t[0].equals(shown) && !sel.equals("Silence") && !sel.startsWith("Vanilla");
            boolean hover = mx >= listX && mx < listX + listW && my >= Math.max(ry, listY) && my < Math.min(ry + RH, listY + listH);
            Draw.rrect(g, listX + 2, ry + 1, listW - 8, RH - 2, 4, isCur ? 0xFF1E2B52 : hover ? 0xFF1B2030 : 0x00000000);
            if (isCur) Draw.rrect(g, listX + 2, ry + 4, 2, RH - 8, 1, Draw.ACCENT);
            pose.pushPose();
            pose.translate(listX + 7, ry + 3, 0);
            pose.scale(0.75f, 0.75f, 1f);
            g.renderItem(iconFor(t[1]), 0, 0);
            pose.popPose();
            String label = t[0];
            Draw.text(g, Draw.fit(label, listW - 70), listX + 24, ry + 5, isCur ? Draw.ACCENT : Draw.TEXT);
            String kind = kindOf(t[1]);
            Draw.text(g, kind, listX + listW - 10 - Draw.width(kind), ry + 5, Draw.TEXT_DIM);
            final String lab = t[0];
            if (hover) hits.add(new Hit(listX, Math.max(ry, listY), listW - 6, Math.min(RH, listY + listH - Math.max(ry, listY)),
                    () -> ms.play(lab)));
        }
        g.disableScissor();
        if (contentH > listH) {
            int bh = Math.max(10, listH * listH / contentH);
            int by = listY + (listH - bh) * scroll / Math.max(1, contentH - listH);
            Draw.rrect(g, listX + listW - 4, by, 2, bh, 1, 0x88FFFFFF);
        }

        // ----- footer -----
        int fy = y0 + H - 19, fx = x0 + 8;
        button(g, mx, my, fx, fy, 70, 14, "Open folder", false, MusicPanel::openFolder);
        fx += 74;
        button(g, mx, my, fx, fy, 74, 14, "Reload songs", false, ms::reloadSongs);
        fx += 78;
        Draw.text(g, ms.customCount() + " custom", fx, fy + 3, Draw.TEXT_DIM);
        int rx = x0 + W - 8;
        for (String s : new String[]{"Silence", "Random", "Vanilla"}) {
            int bw = Draw.width(s) + 12;
            rx -= bw;
            final String target = s.equals("Vanilla") ? "Vanilla (default)" : s;
            button(g, mx, my, rx, fy, bw, 14, s, sel.equals(target), () -> ms.play(target));
            rx -= 3;
        }
    }

    private static void openFolder() {
        try {
            var dir = MusicSwitcher.customDir();
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
        } catch (IOException | RuntimeException e) {
            dev.plawrient.core.Chat.send("Could not open the folder: " + MusicSwitcher.customDir());
        }
    }

    // ---------- input ----------
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) {
                h.action.run();
                if (draggingVol) drag(mx);
                return true;
            }
        }
        return false;
    }

    /** Volume slider drag. @return true while the slider is being dragged. */
    public boolean drag(double mx) {
        if (!draggingVol) return false;
        setVolume((mx - volX) / (double) volW);
        return true;
    }

    public void release() {
        if (draggingVol) Minecraft.getInstance().options.save();
        draggingVol = false;
    }

    public boolean scrolled(double mx, double my, double delta) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            scroll = Mth.clamp(scroll + (int) (-delta * 18), 0, Math.max(0, contentH - listH));
            return true;
        }
        return false;
    }

    public void type(char c) { if (filter.length() < 40) { filter.append(c); scroll = 0; } }

    public void backspace() { if (filter.length() > 0) { filter.setLength(filter.length() - 1); scroll = 0; } }
}
