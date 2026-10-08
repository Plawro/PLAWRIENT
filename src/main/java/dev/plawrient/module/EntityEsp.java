package dev.plawrient.module;

import dev.plawrient.core.*;
import java.util.List;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.Projection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lock-on boxes around entities, HP bar on the side of the box, and a highlighted "locked" box on your target. */
public class EntityEsp extends Module {
    public final BoolSetting players = add(new BoolSetting("players", true));
    public final BoolSetting hostiles = add(new BoolSetting("hostiles", true));
    public final BoolSetting animals = add(new BoolSetting("animals", false));
    public final BoolSetting items = add(new BoolSetting("items", false));
    public final BoolSetting hpBar = add(new BoolSetting("hpBar", true));
    public final ChoiceSetting hpBarStyle = add(new ChoiceSetting("hpBarStyle", "Vertical", List.of("Vertical", "Horizontal")));
    public final BoolSetting lockOn = add(new BoolSetting("lockOn", true));
    public final NumberSetting range = add(new NumberSetting("range", 64, 8, 256));

    public EntityEsp() { super("EntityESP", "Lock-on boxes around entities", Category.RENDER); }

    private boolean wants(int kind) {
        return switch (kind) {
            case EntityFilter.PLAYER -> players.get();
            case EntityFilter.HOSTILE -> hostiles.get();
            case EntityFilter.ANIMAL -> animals.get();
            case EntityFilter.ITEM -> items.get();
            default -> false;
        };
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        float pt = mc.getPartialTick();
        Vec3 cam = Projection.camPos();
        double r2 = range.get() * range.get();
        int sw = g.guiWidth(), sh = g.guiHeight();
        Entity target = lockOn.get() ? Targeting.held(10) : null;

        for (Entity e : mc.level.entitiesForRendering()) {
            int kind = EntityFilter.kind(e);
            if (!wants(kind)) continue;
            if (e == mc.player && !Freecam.active()) continue;
            double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
            if (cam.distanceToSqr(x, y, z) > r2) continue;

            AABB bb = e.getBoundingBox().move(x - e.getX(), y - e.getY(), z - e.getZ());
            double[] xs = {bb.minX, bb.maxX}, ys = {bb.minY, bb.maxY}, zs = {bb.minZ, bb.maxZ};
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean ok = true;
            for (int i = 0; i < 2 && ok; i++)
                for (int j = 0; j < 2 && ok; j++)
                    for (int k = 0; k < 2; k++) {
                        float[] p = Projection.toScreen(xs[i], ys[j], zs[k]);
                        if (p == null) { ok = false; break; }
                        minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                        minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                    }
            if (!ok || maxX < 0 || maxY < 0 || minX > sw || minY > sh || (maxX - minX) > sw * 2) continue;

            int x1 = (int) minX - 1, y1 = (int) minY - 1, x2 = (int) maxX + 1, y2 = (int) maxY + 1;
            int color = EntityFilter.color(kind);
            Draw.brackets(g, x1, y1, x2, y2, color);

            // locked target: pulsing outer brackets
            if (e == target) {
                int pulse = (int) (2 + 2 * Math.sin(System.currentTimeMillis() / 140.0));
                Draw.corners(g, x1 - pulse, y1 - pulse, x2 + pulse, y2 + pulse, Math.max(5, (x2 - x1) / 3), 0xFFFFFFFF);
            }

            // HP bar: horizontal under the box (or vertical on the left side)
            if (hpBar.get() && e instanceof LivingEntity le) {
                float ratio = Mth.clamp(le.getHealth() / Math.max(1f, le.getMaxHealth()), 0f, 1f);
                int col = Draw.lerp(0xFFFF4D4D, 0xFF55FF7A, ratio);
                if (hpBarStyle.get().equals("Horizontal")) {
                    if (x2 - x1 > 6) {
                        int by = y2 + 3, bw = x2 - x1;
                        g.fill(x1 - 1, by - 1, x2 + 1, by + 3, 0xAA000000);
                        g.fill(x1, by, x1 + (int) (bw * ratio), by + 2, col);
                    }
                } else if (y2 - y1 > 6) {
                    int bx = x1 - 5, bh = y2 - y1;
                    g.fill(bx - 1, y1 - 1, bx + 3, y2 + 1, 0xAA000000);
                    g.fill(bx, y2 - (int) (bh * ratio), bx + 2, y2, col);
                }
            }
        }
    }
}
