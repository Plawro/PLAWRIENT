package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.Projection;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

/**
 * Mark "my target": press the key (default G, rebind in the menu or with &bind TargetPing <key>) while aiming at
 * something - or near it - and that entity becomes your target until it dies, gets too far, or you ping it again.
 * Every module that cares about a target (TargetHUD, EntityESP lock-on, AutoTool weapon choice, DamageNumbers)
 * then uses the pinged one instead of whatever is under the crosshair. Draws a marker on it, and an arrow at the
 * edge of the screen when it is off-screen / behind you.
 */
public class TargetPing extends Module {
    public final BoolSetting arrow = add(new BoolSetting("offscreenArrow", true));
    public final BoolSetting message = add(new BoolSetting("message", true));

    public TargetPing() {
        super("TargetPing", "Mark your target (default key G)", Category.HUD);
        this.enabled = true;               // master switch; the key does the pinging
        setKey(GLFW.GLFW_KEY_G);
    }

    @Override public boolean usesKeyDirectly() { return true; }
    @Override public boolean showInList() { return false; }
    @Override public void onKeyPress() { Targeting.ping(message.get()); }
    @Override public void onDisable() { Targeting.clearPin(); }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        LivingEntity t = Targeting.pinned();
        if (t == null || mc.level == null || mc.player == null || mc.options.hideGui) return;

        float pt = mc.getPartialTick();
        double x = Mth.lerp(pt, t.xo, t.getX()), y = Mth.lerp(pt, t.yo, t.getY()), z = Mth.lerp(pt, t.zo, t.getZ());
        double dist = Math.sqrt(Projection.camPos().distanceToSqr(x, y, z));
        int pulse = 0xE0 + (int) (0x1F * Math.abs(Math.sin(System.currentTimeMillis() / 220.0)));
        int color = (pulse << 24) | 0xFF2DAA;
        String label = String.format(Locale.ROOT, "TARGET  %s  %.0fm", Draw.fit(t.getDisplayName().getString(), 90), dist);

        int sw = g.guiWidth(), sh = g.guiHeight();
        AABB bb = t.getBoundingBox().move(x - t.getX(), y - t.getY(), z - t.getZ());
        double[] xs = {bb.minX, bb.maxX}, ys = {bb.minY, bb.maxY}, zs = {bb.minZ, bb.maxZ};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean ok = true;
        for (int i = 0; i < 2 && ok; i++)
            for (int j = 0; j < 2 && ok; j++)
                for (int k = 0; k < 2; k++) {
                    float[] sp = Projection.toScreen(xs[i], ys[j], zs[k]);
                    if (sp == null) { ok = false; break; }
                    minX = Math.min(minX, sp[0]); maxX = Math.max(maxX, sp[0]);
                    minY = Math.min(minY, sp[1]); maxY = Math.max(maxY, sp[1]);
                }

        if (ok && maxX >= 0 && maxY >= 0 && minX <= sw && minY <= sh && (maxX - minX) < sw * 2) {
            int x1 = (int) minX - 3, y1 = (int) minY - 3, x2 = (int) maxX + 3, y2 = (int) maxY + 3;
            Draw.corners(g, x1, y1, x2, y2, Math.max(6, (x2 - x1) / 3), color);
            Draw.centered(g, label, (x1 + x2) / 2, Math.max(2, y1 - 12), color, true);
            return;
        }

        if (!arrow.get()) return;
        // off-screen: chip at the screen edge pointing the way (rel > 0 = target is to your right)
        double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
        float rel = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * 57.29578) - 90f - mc.player.getYRot());
        String txt;
        int cx, cy;
        if (Math.abs(rel) < 25f) {                    // ahead, but above / below the screen
            boolean up = t.getY() > mc.player.getEyeY();
            txt = (up ? "^ " : "v ") + label.replace("TARGET  ", "");
            cx = sw / 2 - Draw.width(txt) / 2;
            cy = up ? 8 : sh - 40;
        } else if (rel > 0) {
            txt = label.replace("TARGET  ", "") + " >";
            cx = sw - Draw.width(txt) - 16;
            cy = sh / 2 - 7;
        } else {
            txt = "< " + label.replace("TARGET  ", "");
            cx = 8;
            cy = sh / 2 - 7;
        }
        int w = Draw.width(txt) + 8;
        Draw.rrect(g, cx - 4, cy - 3, w, 14, 4, Draw.bg(0.8));
        Draw.rrect(g, cx - 4, cy - 3, 2, 14, 1, color);
        Draw.text(g, txt, cx, cy, color, true);
    }
}
