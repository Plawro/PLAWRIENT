package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.Projection;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Panel o entite, na kterou miris: 3D model, HP bar po boku, jmeno, vzdalenost, vyzbroj a efekty. */
public class TargetHud extends Module {
    public final NumberSetting x = add(new NumberSetting("x", 40, -600, 600));
    public final NumberSetting y = add(new NumberSetting("y", 30, -400, 400));
    public final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.5));
    public final NumberSetting opacity = add(new NumberSetting("opacity", 0.75, 0.0, 1.0));
    public final NumberSetting range = add(new NumberSetting("range", 32, 5, 64));
    public final BoolSetting model = add(new BoolSetting("model", true));
    public final BoolSetting gear = add(new BoolSetting("gear", true));
    public final BoolSetting effects = add(new BoolSetting("effects", true));
    public final BoolSetting healthText = add(new BoolSetting("healthText", false));

    private static final int W = 156, H = 68;

    public TargetHud() { super("TargetHUD", "Info panel for the entity you aim at", Category.HUD); }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        LivingEntity t = Targeting.held(30);
        if (t == null) return;
        Vec3 cam = Projection.camPos();
        double dist = Math.sqrt(cam.distanceToSqr(t.getX(), t.getY(), t.getZ()));
        if (dist > range.get()) return;

        float s = scale.get().floatValue();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(g.guiWidth() / 2f + x.get().floatValue(), g.guiHeight() / 2f + y.get().floatValue(), 0);
        ps.scale(s, s, 1f);

        Draw.rrect(g, 0, 0, W, H, 7, Draw.bg(opacity.get()));

        // HP bar: horizontal along the bottom of the panel
        float hp = t.getHealth(), max = Math.max(1f, t.getMaxHealth());
        float ratio = Mth.clamp(hp / max, 0f, 1f);
        int barW = W - 16, barY = H - 8;
        Draw.rrect(g, 8, barY, barW, 4, 2, 0xFF23262F);
        int fill = (int) (barW * ratio);
        if (fill > 0) Draw.rrect(g, 8, barY, fill, 4, 2, Draw.lerp(0xFFFF4D4D, 0xFF55FF7A, ratio));
        float abs = t.getAbsorptionAmount();
        if (abs > 0) {
            int aw = Math.max(2, (int) (barW * Mth.clamp(abs / max, 0f, 1f)));
            Draw.rrect(g, 8, barY - 3, aw, 2, 1, 0xFFFFD84A);
        }

        int tx = 16;
        if (model.get()) {
            int sc = (int) Mth.clamp(40f / Math.max(t.getBbHeight(), t.getBbWidth()), 4f, 60f);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, 36, H - 14, sc, 0f, 0f, t);
            tx = 62;
        }

        String name = Draw.fit(t.getDisplayName().getString(), W - tx - 8);
        Draw.text(g, name, tx, 8, Draw.TEXT, true);
        StringBuilder info = new StringBuilder(String.format(Locale.ROOT, "%.1fm", dist));
        if (t.getArmorValue() > 0) info.append("   Armor ").append(t.getArmorValue());
        if (healthText.get()) info.append(String.format(Locale.ROOT, "   %.1f/%.0f", hp, max));
        Draw.text(g, info.toString(), tx, 19, Draw.TEXT_DIM);

        if (gear.get()) {
            ItemStack[] items = {t.getMainHandItem(), t.getItemBySlot(EquipmentSlot.HEAD),
                    t.getItemBySlot(EquipmentSlot.CHEST), t.getItemBySlot(EquipmentSlot.LEGS),
                    t.getItemBySlot(EquipmentSlot.FEET)};
            for (int i = 0; i < items.length; i++) {
                if (items[i].isEmpty()) continue;
                ps.pushPose();
                ps.translate(tx + i * 14, 31, 0);
                ps.scale(0.75f, 0.75f, 1f);
                g.renderItem(items[i], 0, 0);
                g.renderItemDecorations(mc.font, items[i], 0, 0);
                ps.popPose();
            }
        }

        if (effects.get()) {
            int ex = tx, n = 0;
            for (MobEffectInstance ei : t.getActiveEffects()) {
                if (n++ >= 7) break;
                TextureAtlasSprite sp = mc.getMobEffectTextures().get(ei.getEffect());
                g.blit(ex, 46, 0, 9, 9, sp);
                ex += 11;
            }
        }
        ps.popPose();
    }
}
