package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.Projection;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Nametag: name, HP bar, distance, armor and gear (players). Replaces the vanilla nametag. */
public class Nametags extends Module {
    public static Nametags INSTANCE;

    public final BoolSetting players = add(new BoolSetting("players", true));
    public final BoolSetting hostiles = add(new BoolSetting("hostiles", true));
    public final BoolSetting animals = add(new BoolSetting("animals", false));
    public final BoolSetting gear = add(new BoolSetting("gear", true));
    public final BoolSetting hpText = add(new BoolSetting("hpText", true));
    public final BoolSetting background = add(new BoolSetting("background", true));
    public final NumberSetting opacity = add(new NumberSetting("opacity", 0.7, 0.0, 1.0));
    public final NumberSetting range = add(new NumberSetting("range", 64, 8, 256));
    public final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.5));

    public Nametags() {
        super("Nametags", "Nametags with HP and distance", Category.RENDER);
        INSTANCE = this;
    }

    private boolean matches(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand) return false;
        return switch (EntityFilter.kind(e)) {
            case EntityFilter.PLAYER -> players.get();
            case EntityFilter.HOSTILE -> hostiles.get();
            case EntityFilter.ANIMAL -> animals.get();
            default -> false;
        };
    }

    public static boolean hides(Entity e) {
        Nametags n = INSTANCE;
        return n != null && n.isEnabled() && n.matches(e);
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        float pt = mc.getPartialTick();
        Vec3 cam = Projection.camPos();
        double r2 = range.get() * range.get();
        float s = scale.get().floatValue();
        boolean bg = background.get();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !matches(e)) continue;
            if (e == mc.player && !Freecam.active()) continue;
            double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
            double d2 = cam.distanceToSqr(x, y, z);
            if (d2 > r2) continue;
            float[] p = Projection.toScreen(x, y + e.getBbHeight() + 0.35, z);
            if (p == null) continue;

            String name = e.getDisplayName().getString();
            float hp = le.getHealth(), max = Math.max(1f, le.getMaxHealth());
            StringBuilder info = new StringBuilder().append((int) Math.sqrt(d2)).append("m");
            if (le.getArmorValue() > 0) info.append("  Armor ").append(le.getArmorValue());
            if (hpText.get()) {
                info.append(String.format(Locale.ROOT, "  HP %.1f/%.0f", hp, max));
                float abs = le.getAbsorptionAmount();
                if (abs > 0) info.append(String.format(Locale.ROOT, " +%.0f", abs));
            }
            String infoS = info.toString();

            int w = Math.max(Draw.width(name), Draw.width(infoS)) + 12;
            int boxH = 31;
            boolean showGear = gear.get() && e instanceof Player;

            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(p[0], p[1], 0);
            ps.scale(s, s, 1f);

            int x0 = -w / 2, top = -2 - boxH;
            if (bg) Draw.rrect(g, x0, top, w, boxH, 5, Draw.bg(opacity.get()));
            Draw.centered(g, name, 0, top + 3, Draw.TEXT, !bg);
            Draw.centered(g, infoS, 0, top + 14, Draw.TEXT_DIM, !bg);
            // HP bar: horizontal, below the other info
            float ratio = Mth.clamp(hp / max, 0f, 1f);
            int bx = x0 + 5, by = top + 25, bw = w - 10;
            Draw.rrect(g, bx, by, bw, 3, 1, 0xCC1B1E27);
            int fw = (int) (bw * ratio);
            if (fw > 0) Draw.rrect(g, bx, by, fw, 3, 1, Draw.lerp(0xFFFF4D4D, 0xFF55FF7A, ratio));

            if (showGear) {
                Player pl = (Player) e;
                ItemStack[] items = {pl.getMainHandItem(), pl.getItemBySlot(EquipmentSlot.HEAD),
                        pl.getItemBySlot(EquipmentSlot.CHEST), pl.getItemBySlot(EquipmentSlot.LEGS),
                        pl.getItemBySlot(EquipmentSlot.FEET)};
                int cell = 13, gx = -(items.length * cell) / 2, gy = top - 14;
                for (int i = 0; i < items.length; i++) {
                    if (items[i].isEmpty()) continue;
                    ps.pushPose();
                    ps.translate(gx + i * cell, gy, 0);
                    ps.scale(0.75f, 0.75f, 1f);
                    g.renderItem(items[i], 0, 0);
                    ps.popPose();
                }
            }
            ps.popPose();
        }
    }
}
