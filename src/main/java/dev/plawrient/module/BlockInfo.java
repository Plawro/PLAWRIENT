package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Waila-style info about the block/entity you look at (name, id, which mod it is from). */
public class BlockInfo extends Module {
    public final NumberSetting range = add(new NumberSetting("range", 20, 5, 64));
    public final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 3.0));
    public final NumberSetting opacity = add(new NumberSetting("opacity", 0.8, 0.0, 1.0));
    public final NumberSetting yOffset = add(new NumberSetting("y", 6, 0, 300));

    public BlockInfo() { super("BlockInfo", "Info about the block you look at", Category.HUD); }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui) return;
        Entity camEnt = mc.getCameraEntity();
        if (camEnt == null) return;

        String title, id;
        ItemStack icon = ItemStack.EMPTY;
        HitResult hr = camEnt.pick(range.get(), mc.getPartialTick(), false);

        if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK) {
            BlockState st = mc.level.getBlockState(bhr.getBlockPos());
            title = st.getBlock().getName().getString();
            id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
            icon = new ItemStack(st.getBlock());
        } else if (mc.crosshairPickEntity != null) {
            Entity e = mc.crosshairPickEntity;
            title = e.getDisplayName().getString();
            if (e instanceof LivingEntity le)
                title += String.format(Locale.ROOT, "  %.1f/%.0f", le.getHealth(), le.getMaxHealth());
            id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
            if (e instanceof ItemEntity ie) icon = ie.getItem();
            else {
                SpawnEggItem egg = SpawnEggItem.byId(e.getType());
                if (egg != null) icon = new ItemStack(egg);
            }
        } else return;

        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        String mod = Platform.modName.apply(ns);

        boolean hasIcon = !icon.isEmpty();
        int iconW = hasIcon ? 20 : 0;
        int tw = Math.max(Math.max(Draw.width(title), Draw.width(id)), Draw.width(mod));
        int w = tw + iconW + 16, h = 36;
        float s = scale.get().floatValue();

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(g.guiWidth() / 2f, yOffset.get().floatValue(), 0);
        ps.scale(s, s, 1f);
        int x = -w / 2;

        Draw.rrect(g, x, 0, w, h, 6, Draw.bg(opacity.get()));
        Draw.rrect(g, x + 6, h - 3, w - 12, 1, 0, Draw.ACCENT);
        if (hasIcon) g.renderItem(icon, x + 7, 10);
        int tx = x + 8 + iconW;
        Draw.text(g, title, tx, 5, Draw.TEXT);
        Draw.text(g, id, tx, 15, Draw.TEXT_DIM);
        g.drawString(mc.font, Component.literal(mod).setStyle(dev.plawrient.core.Fonts.style().withItalic(true)),
                tx, 25, 0xFF7AA2FF, false);
        ps.popPose();
    }
}
