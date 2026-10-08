package dev.plawrient.gui;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Panels: drag the header to move, click the header to open/close (more panels can be open at once).
 * Module: LMB toggle, RMB settings, MMB keybind (DELETE clears, ESC cancels).
 * Settings: click changes bool/number, text settings are edited by typing (ENTER confirms).
 * Type anywhere to search. Mouse wheel scrolls a long panel.
 */
public class ClickGuiScreen extends Screen {
    private enum Kind { MOD, SET }

    private record Row(Kind kind, int x, int y, int w, int h, Module m, Setting<?> s, int clipTop, int clipBot) {
        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= Math.max(y, clipTop) && my < Math.min(y + h, clipBot);
        }
    }

    private record Panel(String key, String label, Category cat, int x, int y, boolean open,
                         int bodyTop, int bodyH, int contentH, List<Row> rows) {}

    private static final int PW = 120, HH = 18, MH = 15, SH = 14;

    private final Set<Module> expanded = new HashSet<>();
    private final Map<String, Integer> scrolls = new HashMap<>();
    private final StringBuilder search = new StringBuilder();
    private final StringBuilder buf = new StringBuilder();
    private Module binding;
    private Setting<?> editing;
    private int tab = 0; // 0 = Modules, 1 = Settings, 2 = Music
    private final MusicPanel music = new MusicPanel();

    private String dragKey;
    private int dragDX, dragDY, pressX, pressY;
    private boolean dragMoved;

    public ClickGuiScreen() {
        super(Component.literal("PLAWRIENT"));
        GuiState.initDefaults();
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---------- layout ----------
    private Panel makePanel(String key, String label, Category cat, int x, int y, boolean open, List<Module> mods) {
        int bodyTop = y + HH + 3;
        int contentH = 0;
        for (Module m : mods) {
            contentH += MH + 1;
            if (expanded.contains(m)) contentH += m.settings().size() * (SH + 1);
        }
        int maxBody = Math.max(40, height - bodyTop - 16);
        int bodyH = open ? Math.min(contentH, maxBody) : 0;
        int scroll = Mth.clamp(scrolls.getOrDefault(key, 0), 0, Math.max(0, contentH - bodyH));
        scrolls.put(key, scroll);

        List<Row> rows = new ArrayList<>();
        if (open) {
            int cy = -scroll;
            for (Module m : mods) {
                rows.add(new Row(Kind.MOD, x + 4, bodyTop + cy, PW - 8, MH, m, null, bodyTop, bodyTop + bodyH));
                cy += MH + 1;
                if (expanded.contains(m)) {
                    for (Setting<?> s : m.settings()) {
                        rows.add(new Row(Kind.SET, x + 9, bodyTop + cy, PW - 13, SH, m, s, bodyTop, bodyTop + bodyH));
                        cy += SH + 1;
                    }
                }
            }
        }
        return new Panel(key, label, cat, x, y, open, bodyTop, bodyH, contentH, rows);
    }

    private List<Panel> panels() {
        List<Panel> out = new ArrayList<>();
        if (tab == 2) return out;
        if (tab == 1) {
            Module hud = ModuleManager.INSTANCE.get("Hud");
            if (hud != null) {
                expanded.add(hud);
                out.add(makePanel("settings", "Interface", null, width / 2 - PW / 2, 52, true, List.of(hud)));
            }
            return out;
        }
        if (search.length() > 0) {
            String q = search.toString().toLowerCase(Locale.ROOT);
            List<Module> mods = ModuleManager.INSTANCE.all().stream()
                    .filter(m -> m.name.toLowerCase(Locale.ROOT).contains(q)).toList();
            out.add(makePanel("results", "Results", null, width / 2 - PW / 2, 52, true, mods));
            return out;
        }
        for (Category c : Category.values()) {
            int[] p = GuiState.pos(c.name(), 10 + c.ordinal() * (PW + 10), 52);
            out.add(makePanel(c.name(), c.label, c, Math.min(p[0], Math.max(0, width - PW)), p[1], GuiState.isOpen(c.name()),
                    ModuleManager.INSTANCE.byCategory(c)));
        }
        return out;
    }

    // ---------- render ----------
    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x99000000, 0xCC0A0D18);

        int tx = width / 2 - 80;
        Draw.rrect(g, tx - 2, 4, 164, 18, 9, 0xE60F1117);
        String[] tabs = {"Modules", "Settings", "Music"};
        for (int i = 0; i < 3; i++) {
            int x = tx + i * 54;
            if (tab == i) Draw.rrect(g, x, 6, 52, 14, 7, 0xFF232A3D);
            Draw.centered(g, tabs[i], x + 26, 9, tab == i ? Draw.TEXT : Draw.TEXT_DIM, false);
        }

        if (tab == 2) {
            music.render(g, mx, my, width, height);
            Draw.text(g, "Music player   click a song to play   type to search   wheel scrolls the list",
                    8, height - 12, Draw.TEXT_DIM);
            return;
        }

        int sx = width / 2 - 100;
        Draw.rrect(g, sx, 26, 200, 18, 9, 0xE60A0C11);
        String shown = search.length() == 0 ? "Search" : search + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Draw.text(g, shown, sx + 10, 31, search.length() == 0 ? Draw.TEXT_DIM : Draw.TEXT);

        for (Panel p : panels()) {
            boolean hh = mx >= p.x && mx < p.x + PW && my >= p.y && my < p.y + HH;
            Draw.rrect(g, p.x, p.y, PW, HH, 6, hh ? Draw.PANEL_HOVER : Draw.PANEL);
            Draw.rrect(g, p.x + 6, p.y + HH - 2, PW - 12, 1, 0, Draw.ACCENT);
            Draw.text(g, p.label, p.x + 8, p.y + 5, Draw.TEXT);
            if (p.cat != null) Draw.text(g, p.open ? "-" : "+", p.x + PW - 12, p.y + 5, Draw.TEXT_DIM);

            if (!p.open || p.bodyH <= 0) continue;
            Draw.rrect(g, p.x, p.bodyTop - 2, PW, p.bodyH + 4, 6, 0xB00B0D13);
            g.enableScissor(p.x, p.bodyTop, p.x + PW, p.bodyTop + p.bodyH);
            for (Row r : p.rows) {
                boolean hover = r.hit(mx, my);
                if (r.kind == Kind.MOD) {
                    Draw.rrect(g, r.x, r.y, r.w, r.h, 4, hover ? Draw.ROW_HOVER : Draw.ROW);
                    boolean on = r.m.isEnabled();
                    if (on) Draw.rrect(g, r.x, r.y + 3, 2, r.h - 6, 1, Draw.ACCENT);
                    Draw.text(g, r.m.name, r.x + 6, r.y + 4, on ? Draw.ACCENT : Draw.TEXT);
                    String hint = binding == r.m ? "..." : (r.m.getKey() >= 0 ? CommandManager.keyName(r.m.getKey()) : "");
                    if (!hint.isEmpty()) Draw.text(g, hint, r.x + r.w - Draw.width(hint) - 5, r.y + 4, Draw.TEXT_DIM);
                } else {
                    Draw.rrect(g, r.x, r.y, r.w, r.h, 4, hover ? 0xCC191D27 : 0xCC0D0F14);
                    String line = editing == r.s ? r.s.name + ": " + buf + "_" : r.s.name + ": " + r.s.display();
                    Draw.text(g, Draw.fit(line, r.w - 8), r.x + 4, r.y + 3, editing == r.s ? Draw.TEXT : Draw.TEXT_DIM);
                }
            }
            g.disableScissor();
            if (p.contentH > p.bodyH) {
                int bh = Math.max(10, p.bodyH * p.bodyH / p.contentH);
                int sc = scrolls.getOrDefault(p.key, 0);
                int by = p.bodyTop + (p.bodyH - bh) * sc / Math.max(1, p.contentH - p.bodyH);
                Draw.rrect(g, p.x + PW - 3, by, 2, bh, 1, 0x88FFFFFF);
            }
        }
        Draw.text(g, "LMB toggle   RMB settings   MMB keybind   drag headers to move   type to search",
                8, height - 12, Draw.TEXT_DIM);
    }

    // ---------- input ----------
    private void handleRow(Row r, int btn) {
        if (r.kind == Kind.MOD) {
            switch (btn) {
                case 0 -> r.m.toggle();
                case 1 -> { if (!expanded.remove(r.m)) expanded.add(r.m); }
                case 2 -> binding = r.m;
                default -> {}
            }
            return;
        }
        Setting<?> s = r.s;
        if (s instanceof BlockListSetting bl) minecraft.setScreen(new BlockPickerScreen(bl, this));
        else if (s instanceof ItemListSetting il) minecraft.setScreen(new ItemPickerScreen(il, this));
        else if (s instanceof FontSetting fs) minecraft.setScreen(new FontPickerScreen(fs, this));
        else if (s instanceof ChoiceSetting cs) {
            if (btn == 1) { cs.click(true); ModuleManager.INSTANCE.save(); }
            else minecraft.setScreen(new ChoicePickerScreen(cs, this));
        }
        else if (s instanceof StringSetting) {
            editing = s;
            buf.setLength(0);
            buf.append(String.valueOf(s.get()));
        } else {
            s.click(btn == 1);
            ModuleManager.INSTANCE.save();
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        commitEdit();
        int tx = width / 2 - 80;
        if (my >= 6 && my < 20) {
            for (int i = 0; i < 3; i++)
                if (mx >= tx + i * 54 && mx < tx + i * 54 + 52) { tab = i; return true; }
        }
        if (tab == 2) return music.mouseClicked(mx, my, btn) || super.mouseClicked(mx, my, btn);
        for (Panel p : panels()) {
            if (mx >= p.x && mx < p.x + PW && my >= p.y && my < p.y + HH) {
                if (p.cat != null && btn == 0) {
                    dragKey = p.key;
                    dragDX = (int) mx - p.x; dragDY = (int) my - p.y;
                    pressX = (int) mx; pressY = (int) my;
                    dragMoved = false;
                } else if (p.cat != null && btn == 1) {
                    GuiState.setOpen(p.key, !GuiState.isOpen(p.key));
                    ModuleManager.INSTANCE.save();
                }
                return true;
            }
            for (Row r : p.rows) {
                if (r.hit(mx, my)) { handleRow(r, btn); return true; }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (tab == 2 && btn == 0 && music.drag(mx)) return true;
        if (dragKey != null && btn == 0) {
            if (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3) dragMoved = true;
            if (dragMoved) {
                int[] pos = GuiState.pos(dragKey, 0, 0);
                pos[0] = Mth.clamp((int) mx - dragDX, 0, Math.max(0, width - PW));
                pos[1] = Mth.clamp((int) my - dragDY, 0, Math.max(0, height - HH));
            }
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (tab == 2) music.release();
        if (dragKey != null && btn == 0) {
            if (!dragMoved) GuiState.setOpen(dragKey, !GuiState.isOpen(dragKey));
            dragKey = null;
            ModuleManager.INSTANCE.save();
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 2) return music.scrolled(mx, my, delta) || super.mouseScrolled(mx, my, delta);
        for (Panel p : panels()) {
            if (p.open && mx >= p.x && mx < p.x + PW && my >= p.bodyTop && my < p.bodyTop + p.bodyH) {
                scrolls.merge(p.key, (int) (-delta * 14), Integer::sum);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void commitEdit() {
        if (editing != null) {
            editing.parse(buf.toString());
            ModuleManager.INSTANCE.save();
            editing = null;
        }
    }

    @Override public boolean charTyped(char c, int mods) {
        if (c < 32 || c == 127) return false;
        if (editing != null) { buf.append(c); return true; }
        if (tab == 2 && binding == null) { music.type(c); return true; }
        if (binding == null) {
            if (tab == 1) tab = 0;
            search.append(c);
            return true;
        }
        return false;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (binding != null) {
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) binding.setKey(-1);
            else if (key != GLFW.GLFW_KEY_ESCAPE) binding.setKey(key);
            binding = null;
            ModuleManager.INSTANCE.save();
            return true;
        }
        if (editing != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) commitEdit();
            else if (key == GLFW.GLFW_KEY_ESCAPE) editing = null;
            else if (key == GLFW.GLFW_KEY_BACKSPACE && buf.length() > 0) buf.setLength(buf.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true; }
        if (tab == 2 && key == GLFW.GLFW_KEY_BACKSPACE) { music.backspace(); return true; }
        if (key == GLFW.GLFW_KEY_BACKSPACE && search.length() > 0) { search.setLength(search.length() - 1); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() {
        ModuleManager.INSTANCE.save();
        super.onClose();
    }
}
