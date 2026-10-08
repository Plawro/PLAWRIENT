package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.Projection;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Threat awareness: direction arrows around the crosshair, a "BEHIND YOU" warning (with a sound), and an eye icon
 * over enemies: open red eye = it is chasing/attacking you, half-open orange eye = it has line of sight to you.
 */
public class Awareness extends Module {
    public final BoolSetting behind = add(new BoolSetting("behind", true));
    public final BoolSetting ring = add(new BoolSetting("ring", true));
    public final BoolSetting eyes = add(new BoolSetting("eyes", true));
    public final BoolSetting sound = add(new BoolSetting("sound", true));
    public final NumberSetting range = add(new NumberSetting("range", 24, 8, 48));
    public final NumberSetting behindRange = add(new NumberSetting("behindRange", 12, 4, 32));
    public final NumberSetting scale = add(new NumberSetting("scale", 1.0, 0.5, 2.0));

    private record Threat(Entity e, int state, double dist, float rel) {} // state: 0 near, 1 sees you, 2 aggressive

    private List<Threat> threats = List.of();
    private boolean behindNow, wasBehind;
    private int soundCooldown;

    public Awareness() { super("Awareness", "Behind-you warning + enemy eye indicator", Category.HUD); }

    @Override public void onDisable() { threats = List.of(); behindNow = false; wasBehind = false; }

    @Override public void onTick() {
        Minecraft mc = mc();
        var level = mc.level;
        var p = mc.player;
        if (level == null || p == null) { threats = List.of(); behindNow = false; return; }
        if (soundCooldown > 0) soundCooldown--;

        float camYaw = mc.gameRenderer.getMainCamera().getYRot();
        List<Threat> out = new ArrayList<>();
        boolean bh = false;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Mob m) || EntityFilter.isFake(e)) continue;
            boolean hostile = e instanceof Enemy || (e instanceof NeutralMob nm && nm.isAngry());
            if (!hostile) continue;
            double d = Math.sqrt(p.distanceToSqr(e));
            if (d > range.get()) continue;
            boolean aggro = m.isAggressive() || (m instanceof EnderMan em && em.isCreepy())
                    || (m instanceof Creeper c && c.getSwelling(1f) > 0.01f);
            boolean sees = !aggro && d < 20 && m.hasLineOfSight(p);
            float tyaw = (float) Math.toDegrees(Math.atan2(-(e.getX() - p.getX()), e.getZ() - p.getZ()));
            float rel = Mth.wrapDegrees(tyaw - camYaw);
            out.add(new Threat(e, aggro ? 2 : sees ? 1 : 0, d, rel));
            if (Math.abs(rel) > 110 && d <= behindRange.get()) bh = true;
        }
        out.sort(Comparator.comparingDouble(Threat::dist));
        threats = out;
        behindNow = bh && behind.get();

        if (behindNow && !wasBehind && sound.get() && soundCooldown == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Snd.s(SoundEvents.NOTE_BLOCK_PLING), 0.5f, 0.9f));
            soundCooldown = 60;
        }
        wasBehind = behindNow;
    }

    // ---------- drawing ----------
    private static int stateColor(int state) { return state == 2 ? 0xFFFF3B3B : state == 1 ? 0xFFFF9F1A : 0xFFFFE14D; }

    private void arrow(GuiGraphics g, float rel, int cx, int cy, int radius, int color, float s) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(rel));
        ps.translate(0, -radius, 0);
        ps.scale(s, s, 1f);
        for (int i = 0; i < 9; i++) { // triangle pointing away from the crosshair
            int hw = i / 2, y = i - 9;
            g.fill(-hw - 1, y, hw + 2, y + 1, 0xAA000000);
            g.fill(-hw, y, hw + 1, y + 1, color);
        }
        ps.popPose();
    }

    private void eye(GuiGraphics g, int cx, int cy, float open, int color) {
        int h = 4 + (int) (6 * open);
        Draw.rrect(g, cx - 10, cy - h / 2 - 1, 20, h + 2, (h + 2) / 2, 0xCC000000);
        Draw.rrect(g, cx - 9, cy - h / 2, 18, h, h / 2, 0xFFF2F2F2);
        int ir = Math.min(h - 1, 7);
        Draw.rrect(g, cx - ir / 2, cy - ir / 2, ir, ir, ir / 2, color);
        Draw.rrect(g, cx - 1, cy - 1, 3, 3, 1, 0xFF000000);
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        float s = scale.get().floatValue();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        long now = System.currentTimeMillis();

        if (ring.get()) {
            int shown = 0;
            for (Threat t : threats) {
                if (shown >= 5) break;
                if (t.state() == 0 && t.dist() > behindRange.get()) continue;
                int color = stateColor(t.state());
                arrow(g, t.rel(), cx, cy, (int) (64 * s), color, s);
                shown++;
            }
        }

        if (eyes.get()) {
            float pt = mc.getPartialTick();
            for (Threat t : threats) {
                if (t.state() < 1) continue;
                Entity e = t.e();
                double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
                float[] sp = Projection.toScreen(x, y + e.getBbHeight() + 0.5, z);
                if (sp == null) continue;
                boolean tag = Nametags.INSTANCE != null && Nametags.INSTANCE.isEnabled();
                float open = t.state() == 2 ? 1f : 0.5f + 0.1f * (float) Math.sin(now / 250.0);
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(sp[0], sp[1] - (tag ? 38 : 8), 0);
                ps.scale(s, s, 1f);
                eye(g, 0, 0, open, stateColor(t.state()));
                ps.popPose();
            }
        }

        if (behindNow) {
            int a = 90 + (int) (60 * Math.abs(Math.sin(now / 200.0)));
            g.fillGradient(0, g.guiHeight() - 70, g.guiWidth(), g.guiHeight(), 0x00FF0000, (a << 24) | 0xFF0000);
            String txt = "BEHIND YOU";
            int w = Draw.width(txt) + 16;
            Draw.rrect(g, cx - w / 2, cy + 36, w, 16, 6, 0xDDB01212);
            Draw.centered(g, txt, cx, cy + 40, 0xFFFFFFFF, true);
        }
    }
}
