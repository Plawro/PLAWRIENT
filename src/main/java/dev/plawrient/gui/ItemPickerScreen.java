package dev.plawrient.gui;

import dev.plawrient.core.ItemListSetting;
import dev.plawrient.core.ModuleManager;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Item picker (includes modded items): type to search, click to add/remove, wheel to scroll, ESC to go back. */
public class ItemPickerScreen extends Screen {
    private record Entry(Item item, String name, String id) {}

    private static List<Entry> ALL;
    private static int allCount = -1;
    private static final int RH = 18, PW = 260;

    private final ItemListSetting setting;
    private final Screen parent;
    private final StringBuilder search = new StringBuilder();
    private List<Entry> view = List.of();
    private int scroll = 0;

    public ItemPickerScreen(ItemListSetting setting, Screen parent) {
        super(Component.literal("Select items"));
        this.setting = setting;
        this.parent = parent;
        filter();
    }

    private static List<Entry> all() {
        int count = BuiltInRegistries.ITEM.size();
        if (ALL == null || count != allCount) {
            allCount = count;
            ALL = BuiltInRegistries.ITEM.stream()
                    .filter(i -> i != Items.AIR)
                    .map(i -> new Entry(i, i.getDescription().getString(), BuiltInRegistries.ITEM.getKey(i).toString()))
                    .sorted(Comparator.comparing(Entry::id))
                    .toList();
        }
        return ALL;
    }

    private void filter() {
        String q = search.toString().toLowerCase(Locale.ROOT);
        view = all().stream()
                .filter(e -> q.isEmpty() || e.name.toLowerCase(Locale.ROOT).contains(q) || e.id.contains(q))
                .toList();
        scroll = 0;
    }

    private int top() { return 48; }
    private int rowsVisible() { return Math.max(1, (height - 24 - top()) / RH); }
    private int px() { return (width - PW) / 2; }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xAA000000, 0xDD0A0D18);
        int px = px();
        Draw.text(g, "Select items  (" + setting.size() + " selected, first match is used)", px, 10, Draw.TEXT);

        Draw.rrect(g, px, 24, PW, 18, 9, 0xE60A0C11);
        String shown = search.length() == 0 ? "Search..." : search + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Draw.text(g, shown, px + 10, 29, search.length() == 0 ? Draw.TEXT_DIM : Draw.TEXT);

        for (int i = scroll; i < Math.min(view.size(), scroll + rowsVisible()); i++) {
            Entry e = view.get(i);
            int y = top() + (i - scroll) * RH;
            boolean hover = mx >= px && mx < px + PW && my >= y && my < y + RH - 1;
            boolean sel = setting.contains(e.item);
            Draw.rrect(g, px, y, PW, RH - 1, 4, hover ? Draw.ROW_HOVER : Draw.ROW);
            if (sel) Draw.rrect(g, px, y + 3, 2, RH - 7, 1, Draw.ACCENT);
            g.renderItem(new ItemStack(e.item), px + 5, y + 1);
            Draw.text(g, Draw.fit(e.name, PW - 36), px + 28, y + 1, sel ? Draw.ACCENT : Draw.TEXT);
            Draw.text(g, Draw.fit(e.id, PW - 36), px + 28, y + 9, Draw.TEXT_DIM);
        }
        Draw.text(g, "click = add/remove   wheel = scroll   ESC = back", px, height - 14, Draw.TEXT_DIM);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int px = px();
        if (mx >= px && mx < px + PW && my >= top()) {
            int idx = scroll + (int) ((my - top()) / RH);
            if (idx >= 0 && idx < view.size() && idx < scroll + rowsVisible()) {
                setting.toggle(view.get(idx).item);
                ModuleManager.INSTANCE.save();
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
