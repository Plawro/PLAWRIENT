package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.EspRenderTypes;
import dev.plawrient.gui.Lines;
import dev.plawrient.gui.Projection;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shows where flying projectiles will land (arrows, tridents, potions, pearls, snowballs, fireballs...):
 * predicted flight path, landing marker, time to impact and a splash ring for potions.
 * Red = it will hit YOU, green = your own projectile, orange = somebody else's.
 */
public class Trajectory extends Module {
    public final BoolSetting arrows = add(new BoolSetting("arrows", true));
    public final BoolSetting potions = add(new BoolSetting("potions", true));
    public final BoolSetting pearls = add(new BoolSetting("pearls", true));
    public final BoolSetting others = add(new BoolSetting("others", true));
    public final BoolSetting entityHits = add(new BoolSetting("entityHits", true));
    public final BoolSetting labels = add(new BoolSetting("labels", true));
    public final BoolSetting rings = add(new BoolSetting("splashRing", true));
    public final BoolSetting warn = add(new BoolSetting("warnSound", true));
    public final NumberSetting maxSteps = add(new NumberSetting("maxSteps", 120, 30, 300));

    private record Pred(int id, String name, int kind, List<Vec3> pts, Vec3 land, int ticks,
                        boolean hitsPlayer, boolean mine) {}

    private List<Pred> preds = List.of();
    private final Set<Integer> warned = new HashSet<>();
    private int soundCd;

    public Trajectory() { super("Trajectory", "Shows where projectiles will land", Category.RENDER); }

    @Override public void onDisable() { preds = List.of(); warned.clear(); }

    private static int kind(Entity e) {
        if (e instanceof AbstractArrow) return 0;
        if (e instanceof ThrownPotion) return 1;
        if (e instanceof ThrownEnderpearl) return 2;
        if (e instanceof ThrownExperienceBottle || e instanceof ThrowableProjectile || e instanceof AbstractHurtingProjectile) return 3;
        return -1;
    }

    private boolean wants(int kind) {
        return switch (kind) { case 0 -> arrows.get(); case 1 -> potions.get(); case 2 -> pearls.get(); default -> others.get(); };
    }

    private static double gravity(Entity e) {
        if (e instanceof AbstractArrow || e instanceof ThrownPotion) return 0.05;
        if (e instanceof ThrownExperienceBottle) return 0.07;
        if (e instanceof AbstractHurtingProjectile) return 0.0;
        return 0.03;
    }

    private static double drag(Entity e) { return e instanceof AbstractHurtingProjectile ? 1.0 : 0.99; }

    @Override public void onTick() {
        Minecraft mc = mc();
        ClientLevel level = mc.level;
        var p = mc.player;
        if (level == null || p == null) { preds = List.of(); return; }
        if (soundCd > 0) soundCd--;
        if (p.tickCount % 2 != 0) return;

        List<LivingEntity> living = new ArrayList<>();
        if (entityHits.get())
            for (Entity e : level.entitiesForRendering())
                if (e instanceof LivingEntity le && !EntityFilter.isFake(e) && p.distanceToSqr(e) < 64 * 64) living.add(le);

        List<Pred> out = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            int k = kind(e);
            if (k < 0 || !wants(k) || p.distanceToSqr(e) > 160 * 160) continue;
            Vec3 vel = e.getDeltaMovement();
            if (vel.lengthSqr() < 0.0025) continue;               // stuck in the ground / not flying
            out.add(simulate(level, p, e, k, vel, living));
            if (out.size() >= 24) break;
        }
        preds = out;

        Set<Integer> present = new HashSet<>();
        for (Pred pr : out) {
            present.add(pr.id());
            if (pr.hitsPlayer() && warn.get() && soundCd == 0 && warned.add(pr.id())) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(Snd.s(SoundEvents.NOTE_BLOCK_PLING), 1.4f, 0.7f));
                soundCd = 15;
            }
        }
        warned.retainAll(present);
    }

    private Pred simulate(ClientLevel level, net.minecraft.client.player.LocalPlayer p, Entity e, int kind,
                          Vec3 vel, List<LivingEntity> living) {
        double g = gravity(e), drag = drag(e);
        Entity owner = e instanceof Projectile pr ? pr.getOwner() : null;
        Vec3 pos = e.position();
        List<Vec3> pts = new ArrayList<>();
        pts.add(pos);
        Vec3 land = null;
        Entity hit = null;
        int ticks = 0;

        for (int i = 1; i <= maxSteps.get(); i++) {
            Vec3 next = pos.add(vel);
            BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
            Vec3 hitPos = null;
            double best = Double.MAX_VALUE;
            if (bh.getType() != HitResult.Type.MISS) { hitPos = bh.getLocation(); best = hitPos.distanceToSqr(pos); }

            for (LivingEntity le : living) {
                if (le == owner && i < 8) continue;               // the arrow spawns inside its shooter
                Optional<Vec3> c = le.getBoundingBox().inflate(0.3).clip(pos, next);
                if (c.isPresent()) {
                    double d = c.get().distanceToSqr(pos);
                    if (d < best) { best = d; hitPos = c.get(); hit = le; }
                }
            }
            if (hitPos != null) { pts.add(hitPos); land = hitPos; ticks = i; break; }
            pos = next;
            pts.add(pos);
            vel = vel.scale(drag).add(0, -g, 0);
            if (pos.y < level.getMinBuildHeight() - 10) break;
        }
        return new Pred(e.getId(), e.getName().getString(), kind, pts, land, ticks, hit == p, owner == p);
    }

    private static float[] color(Pred pr) {
        if (pr.hitsPlayer()) return new float[]{1f, 0.23f, 0.23f};
        if (pr.mine()) return new float[]{0.33f, 0.91f, 0.54f};
        return new float[]{1f, 0.62f, 0.1f};
    }

    @Override public void onRender3D(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float pt) {
        if (preds.isEmpty()) return;
        var vc = buf.getBuffer(EspRenderTypes.LINES);
        for (Pred pr : preds) {
            float[] c = color(pr);
            List<Vec3> pts = pr.pts();
            int n = pts.size();
            for (int i = 0; i < n - 1; i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                float al = 1f - 0.6f * i / Math.max(1, n);
                Lines.line(ps, vc, a.x, a.y, a.z, b.x, b.y, b.z, c[0], c[1], c[2], al);
            }
            Vec3 l = pr.land();
            if (l == null) continue;
            LevelRenderer.renderLineBox(ps, vc, l.x - 0.25, l.y - 0.25, l.z - 0.25, l.x + 0.25, l.y + 0.25, l.z + 0.25, c[0], c[1], c[2], 1f);
            if (rings.get() && pr.kind() == 1) {                    // splash radius of a potion
                for (int s = 0; s < 24; s++) {
                    double a0 = Math.PI * 2 * s / 24, a1 = Math.PI * 2 * (s + 1) / 24;
                    Lines.line(ps, vc, l.x + Math.cos(a0) * 4, l.y + 0.05, l.z + Math.sin(a0) * 4,
                            l.x + Math.cos(a1) * 4, l.y + 0.05, l.z + Math.sin(a1) * 4, c[0], c[1], c[2], 0.8f);
                }
            }
        }
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (!labels.get() || preds.isEmpty() || mc.level == null || mc.options.hideGui) return;
        Vec3 cam = Projection.camPos();
        for (Pred pr : preds) {
            Vec3 l = pr.land();
            if (l == null) continue;
            float[] sp = Projection.toScreen(l.x, l.y + 0.5, l.z);
            if (sp == null) continue;
            float[] c = color(pr);
            int color = 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
            String txt = (pr.hitsPlayer() ? "INCOMING  " : "") + pr.name()
                    + String.format(Locale.ROOT, "  %.1fs  %.0fm", pr.ticks() / 20.0, cam.distanceTo(l));
            int w = Draw.width(txt) + 10;
            int x = (int) sp[0] - w / 2, y = (int) sp[1] - 14;
            Draw.rrect(g, x, y, w, 12, 4, 0xB00F1117);
            Draw.rrect(g, x, y + 3, 2, 6, 1, color);
            Draw.text(g, txt, x + 6, y + 2, Draw.TEXT);
        }
    }
}
