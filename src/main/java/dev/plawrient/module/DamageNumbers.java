package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.Projection;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Plovouci cisla damage (napr. -4.5) nad entitou, kdyz ji zasahnes (nebo u vsech, pokud zapnes "all"). */
public class DamageNumbers extends Module {
    public final BoolSetting all = add(new BoolSetting("all", false));
    public final BoolSetting heals = add(new BoolSetting("heals", false));
    public final NumberSetting scale = add(new NumberSetting("scale", 1.2, 0.5, 3.0));
    public final NumberSetting duration = add(new NumberSetting("duration", 1.2, 0.4, 4.0));

    private record Pop(double x, double y, double z, float amount, long born) {}

    private final Map<Integer, Float> health = new HashMap<>();
    private final List<Pop> pops = new ArrayList<>();
    private final Random rnd = new Random();

    public DamageNumbers() { super("DamageNumbers", "Floating damage numbers", Category.HUD); }

    @Override public void onDisable() { health.clear(); pops.clear(); }

    @Override public void onTick() {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null) { health.clear(); pops.clear(); return; }
        Set<Integer> seen = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || mc.player.distanceToSqr(e) > 64 * 64) continue;
            seen.add(e.getId());
            float cur = le.getHealth() + le.getAbsorptionAmount();
            Float prev = health.put(e.getId(), cur);
            if (prev == null || Math.abs(cur - prev) < 0.05f) continue;
            boolean damage = cur < prev;
            if (!damage && !heals.get()) continue;
            boolean mine = all.get() || Targeting.recentlyAimed(e, 60);
            if (!mine || (e == mc.player && !all.get())) continue;
            pops.add(new Pop(e.getX() + (rnd.nextDouble() - 0.5) * 0.6, e.getY() + e.getBbHeight() * 0.9,
                    e.getZ() + (rnd.nextDouble() - 0.5) * 0.6, cur - prev, System.currentTimeMillis()));
        }
        health.keySet().retainAll(seen);
    }

    @Override public void onRender2D(GuiGraphics g) {
        if (pops.isEmpty()) return;
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        float s = scale.get().floatValue();
        pops.removeIf(p -> now - p.born > life);
        for (Pop p : pops) {
            float t = (now - p.born) / (float) life;
            float[] sp = Projection.toScreen(p.x, p.y + t * 0.9, p.z);
            if (sp == null) continue;
            boolean dmg = p.amount < 0;
            String txt = String.format(Locale.ROOT, "%s%.1f", dmg ? "-" : "+", Math.abs(p.amount));
            int alpha = Math.max(8, (int) (255 * (1f - t * t)));
            int color = (alpha << 24) | (dmg ? 0xFF5555 : 0x55FF7A);
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(sp[0], sp[1], 0);
            ps.scale(s, s, 1f);
            Draw.centered(g, txt, 0, -4, color, true);
            ps.popPose();
        }
    }
}
