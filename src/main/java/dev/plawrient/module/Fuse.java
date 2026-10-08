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
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.phys.Vec3;

/** Countdown over primed TNT (and TNT minecarts) and over creepers that are about to blow up. */
public class Fuse extends Module {
    public final BoolSetting tnt = add(new BoolSetting("tnt", true));
    public final BoolSetting creepers = add(new BoolSetting("creepers", true));
    public final BoolSetting bar = add(new BoolSetting("bar", true));
    public final NumberSetting range = add(new NumberSetting("range", 48, 8, 128));
    public final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.5));

    public Fuse() { super("Fuse", "Countdown over TNT and hissing creepers", Category.RENDER); }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        float pt = mc.getPartialTick();
        Vec3 cam = Projection.camPos();
        double r2 = range.get() * range.get();
        float s = scale.get().floatValue();

        for (Entity e : mc.level.entitiesForRendering()) {
            float seconds, total, danger;     // danger = blast radius + margin (blocks)
            String label;
            if (tnt.get() && e instanceof PrimedTnt t) {
                seconds = Math.max(0f, t.getFuse() - pt) / 20f; total = 4f; danger = 7f; label = "TNT";
            } else if (tnt.get() && e instanceof MinecartTNT m && m.isPrimed()) {
                seconds = Math.max(0f, m.getFuse() - pt) / 20f; total = 4f; danger = 7f; label = "TNT Minecart";
            } else if (creepers.get() && e instanceof Creeper c && (c.getSwelling(pt) > 0.01f || c.isIgnited())) {
                // swell goes 0..1 over ~28 ticks, the explosion comes about 2 ticks after it reaches 1
                seconds = Math.max(0f, (1f - c.getSwelling(pt)) * 28f + 2f) / 20f; total = 1.5f;
                danger = c.isPowered() ? 9f : 6f; label = c.isPowered() ? "Charged Creeper" : "Creeper";
            } else continue;

            double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
            double d2 = cam.distanceToSqr(x, y, z);
            if (d2 > r2) continue;
            float[] p = Projection.toScreen(x, y + e.getBbHeight() + 0.55, z);
            if (p == null) continue;

            float frac = Mth.clamp(seconds / total, 0f, 1f);
            boolean close = Math.sqrt(mc.player.distanceToSqr(e)) < danger;
            boolean blink = seconds < 0.6f && (System.currentTimeMillis() / 100) % 2 == 0;
            int color = blink ? 0xFFFFFFFF : Draw.lerp(0xFFFF3B3B, 0xFFFFD84A, frac);
            String txt = (close ? "! " : "") + label + "  " + String.format(Locale.ROOT, "%.1fs", seconds);
            int w = Draw.width(txt) + 12;

            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(p[0], p[1], 0);
            ps.scale(s, s, 1f);
            Draw.rrect(g, -w / 2, -17, w, bar.get() ? 17 : 13, 4, Draw.bg(0.8));
            Draw.centered(g, txt, 0, -14, color, true);
            if (bar.get()) {
                Draw.rrect(g, -w / 2 + 4, -4, w - 8, 3, 1, 0xCC1B1E27);
                int fw = (int) ((w - 8) * frac);
                if (fw > 0) Draw.rrect(g, -w / 2 + 4, -4, fw, 3, 1, color);
            }
            if (close) Draw.rrect(g, -w / 2, -17, 2, bar.get() ? 17 : 13, 1, 0xFFFF3B3B);
            ps.popPose();
        }
    }
}
