package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * Window with info about the item in your hand: name, durability (with a low-durability warning), for bows and
 * crossbows how many arrows you have left (with a low-ammo warning), how many of the item you carry in total, and
 * the normal inventory tooltip (enchantments, attributes, lore...).
 */
public class ItemInfo extends Module {
    public final NumberSetting x = add(new NumberSetting("x", 8, 0, 600));                 // from the left edge
    public final NumberSetting y = add(new NumberSetting("y", 0, -300, 300));              // from the vertical centre
    public final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.5));
    public final NumberSetting opacity = add(new NumberSetting("opacity", 0.75, 0.0, 1.0));
    public final ChoiceSetting show = add(new ChoiceSetting("show", "Tools & weapons", List.of("Tools & weapons", "Everything")));
    public final BoolSetting tooltip = add(new BoolSetting("tooltip", true));
    public final BoolSetting advanced = add(new BoolSetting("advancedTooltip", false));
    public final NumberSetting maxLines = add(new NumberSetting("maxLines", 10, 3, 24));
    public final NumberSetting lowArrows = add(new NumberSetting("lowArrows", 16, 1, 64));
    public final NumberSetting lowDurability = add(new NumberSetting("lowDurabilityPercent", 15, 1, 50));

    private static final int MAXW = 200;
    private static final int TEXT = 0, BAR = 1, WARN = 2;

    private static final class Row {
        final int kind;
        final String text;
        final FormattedCharSequence seq;
        final int color;
        final float ratio;
        Row(int kind, String text, FormattedCharSequence seq, int color, float ratio) {
            this.kind = kind; this.text = text; this.seq = seq; this.color = color; this.ratio = ratio;
        }
        int height() { return kind == BAR ? 6 : kind == WARN ? 13 : 10; }
    }

    public ItemInfo() { super("ItemInfo", "Info window for the item in your hand", Category.HUD); }

    private static int arrows(LocalPlayer p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = inv.getItem(i); if (s.getItem() instanceof ArrowItem) n += s.getCount(); }
        ItemStack off = p.getOffhandItem();
        if (off.getItem() instanceof ArrowItem) n += off.getCount();
        return n;
    }

    private static int total(LocalPlayer p, ItemStack ref) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = inv.getItem(i); if (ItemStack.isSameItemSameTags(s, ref)) n += s.getCount(); }
        ItemStack off = p.getOffhandItem();
        if (ItemStack.isSameItemSameTags(off, ref)) n += off.getCount();
        return n;
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.options.hideGui || mc.screen != null || mc.options.renderDebug) return;
        ItemStack st = p.getMainHandItem();
        if (st.isEmpty()) return;
        boolean damageable = st.isDamageableItem();
        if (show.get().startsWith("Tools") && !damageable) return;

        List<Component> tip = st.getTooltipLines(p, advanced.get() ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        Component name = tip.isEmpty() ? st.getHoverName() : tip.get(0);
        long ms = System.currentTimeMillis();
        int pulse = 0xC0 + (int) (0x3F * Math.abs(Math.sin(ms / 250.0)));
        List<Row> rows = new ArrayList<>();

        // durability
        if (damageable) {
            int max = st.getMaxDamage(), left = max - st.getDamageValue();
            float ratio = Mth.clamp(left / (float) Math.max(1, max), 0f, 1f);
            int col = Draw.lerp(0xFFFF4D4D, 0xFF55FF7A, ratio);
            rows.add(new Row(TEXT, String.format(Locale.ROOT, "Durability  %d / %d  (%d%%)", left, max, Math.round(ratio * 100)), null, col, 0));
            rows.add(new Row(BAR, "", null, col, ratio));
            if (ratio * 100f <= lowDurability.get() || left <= 5)
                rows.add(new Row(WARN, "LOW DURABILITY", null, (pulse << 24) | 0xFF3B3B, 0));
        }

        // bows / crossbows: arrows left
        boolean bow = st.is(Items.BOW), xbow = st.is(Items.CROSSBOW);
        if (bow || xbow) {
            boolean creative = p.getAbilities().instabuild;
            int n = arrows(p);
            ItemStack next = p.getProjectile(st);
            String ammo = creative ? "Arrows  unlimited" : "Arrows  " + n;
            if (!creative && !next.isEmpty()) ammo += "   (next: " + next.getHoverName().getString() + ")";
            rows.add(new Row(TEXT, ammo, null, n == 0 && !creative ? 0xFFFF4D4D : n <= lowArrows.get() && !creative ? 0xFFFF9F1A : 0xFFFFFFFF, 0));
            if (xbow) {
                boolean loaded = CrossbowItem.isCharged(st);
                rows.add(new Row(TEXT, loaded ? "Loaded" : "Not loaded", null, loaded ? 0xFF55FF7A : Draw.TEXT_DIM, 0));
            }
            if (!creative && n == 0) rows.add(new Row(WARN, "NO ARROWS!", null, (pulse << 24) | 0xFF3B3B, 0));
            else if (!creative && n <= lowArrows.get()) rows.add(new Row(WARN, "LOW ARROWS  (" + n + ")", null, (pulse << 24) | 0xFF9F1A, 0));
        } else if (st.getMaxStackSize() > 1) {
            rows.add(new Row(TEXT, "In inventory  " + total(p, st), null, 0xFFFFFFFF, 0));
        }

        // the normal inventory tooltip (without the name line)
        if (tooltip.get()) {
            int added = 0;
            for (int i = 1; i < tip.size() && added < maxLines.get(); i++) {
                List<FormattedCharSequence> parts = mc.font.split(tip.get(i), MAXW);
                if (parts.isEmpty()) continue;
                rows.add(new Row(TEXT, "", parts.get(0), 0xFFFFFFFF, 0));
                added++;
            }
        }

        // size
        int w = mc.font.width(name) + 26;
        int h = 22;
        for (Row r : rows) {
            h += r.height();
            int rw = r.seq != null ? mc.font.width(r.seq) : r.kind == BAR ? 0 : Draw.width(r.text) + (r.kind == WARN ? 12 : 0);
            w = Math.max(w, rw);
        }
        w = Math.min(Math.max(w, 110), MAXW + 20) + 12;
        h += 6;

        float s = scale.get().floatValue();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x.get().floatValue(), g.guiHeight() / 2f + y.get().floatValue() - h * s / 2f, 0);
        ps.scale(s, s, 1f);

        Draw.rrect(g, 0, 0, w, h, 6, Draw.bg(opacity.get()));
        Draw.rrect(g, 4, 20, w - 8, 1, 0, 0x55FFFFFF);
        g.renderItem(st, 5, 3);
        g.renderItemDecorations(mc.font, st, 5, 3);
        g.drawString(mc.font, name, 26, 7, 0xFFFFFFFF, true);

        int yy = 25;
        for (Row r : rows) {
            switch (r.kind) {
                case BAR -> {
                    Draw.rrect(g, 6, yy, w - 12, 4, 2, 0xFF23262F);
                    int fill = (int) ((w - 12) * r.ratio);
                    if (fill > 0) Draw.rrect(g, 6, yy, fill, 4, 2, r.color);
                }
                case WARN -> {
                    int cw = Draw.width(r.text) + 12;
                    Draw.rrect(g, 6, yy, cw, 11, 4, (0x55 << 24) | (r.color & 0xFFFFFF));
                    Draw.rrect(g, 6, yy + 2, 2, 7, 1, r.color);
                    Draw.text(g, r.text, 12, yy + 2, r.color, true);
                }
                default -> {
                    if (r.seq != null) g.drawString(mc.font, r.seq, 6, yy, r.color, true);
                    else Draw.text(g, r.text, 6, yy, r.color, true);
                }
            }
            yy += r.height();
        }
        ps.popPose();
    }
}
