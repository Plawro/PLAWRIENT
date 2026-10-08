package dev.plawrient.module;

import dev.plawrient.core.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Na koho ses zamerene (do 32 bloku, zdi blokuji). Sdili EntityESP, TargetHUD a DamageNumbers. */
public final class Targeting {
    private Targeting() {}

    private static LivingEntity aimed, last, pinned;
    private static int lastTick;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        var p = mc.player;
        Entity cam = mc.getCameraEntity();
        if (level == null || p == null || cam == null) { aimed = null; last = null; pinned = null; return; }
        if (pinned != null && (!pinned.isAlive() || pinned.isRemoved() || pinned.level() != level
                || p.distanceToSqr(pinned) > 128 * 128)) pinned = null;

        double range = 32;
        Vec3 eye = cam.getEyePosition();
        Vec3 look = cam.getViewVector(1f);
        Vec3 end = eye.add(look.scale(range));
        AABB box = cam.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult er = ProjectileUtil.getEntityHitResult(cam, eye, end, box,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable() && e != p && !EntityFilter.isFake(e), range * range);

        aimed = null;
        if (er != null && er.getEntity() instanceof LivingEntity le) {
            BlockHitResult br = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
            boolean blocked = br.getType() != HitResult.Type.MISS
                    && br.getLocation().distanceToSqr(eye) < er.getLocation().distanceToSqr(eye);
            if (!blocked) { aimed = le; last = le; lastTick = p.tickCount; }
        }
        if (last != null && (!last.isAlive() || p.tickCount - lastTick > 200)) last = null;
    }

    public static LivingEntity aimed() { return aimed; }

    /** Aktualni cil, nebo posledni cil pokud je to max `ticks` tiku. */
    public static LivingEntity held(int ticks) {
        var p = Minecraft.getInstance().player;
        if (pinned != null) return pinned;                 // a pinged target always wins
        if (aimed != null) return aimed;
        if (last != null && p != null && p.tickCount - lastTick <= ticks) return last;
        return null;
    }

    public static boolean recentlyAimed(Entity e, int ticks) { return e != null && e == held(ticks); }

    // ---------- ping: mark "my target" ----------
    /** The target you marked with the ping key (or &ping); null if none. */
    public static LivingEntity pinned() { return pinned; }

    public static void clearPin() { pinned = null; }

    /** Entity closest to the crosshair within maxDeg degrees and in line of sight (so a ping does not need pixel-perfect aim). */
    private static LivingEntity coneTarget(double maxDeg) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        var p = mc.player;
        Entity cam = mc.getCameraEntity();
        if (level == null || p == null || cam == null) return null;
        Vec3 eye = cam.getEyePosition();
        Vec3 look = cam.getViewVector(1f);
        LivingEntity best = null;
        double bestAng = maxDeg;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == p || EntityFilter.isFake(e) || !le.isAlive() || e.isSpectator()) continue;
            if (!(e instanceof Mob) && !(e instanceof Player)) continue;
            Vec3 to = e.getBoundingBox().getCenter();
            double dist = to.distanceTo(eye);
            if (dist > 64 || dist < 0.5) continue;
            double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(to.subtract(eye).scale(1.0 / dist))))));
            if (ang >= bestAng) continue;
            BlockHitResult br = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
            if (br.getType() != HitResult.Type.MISS) continue;
            best = le; bestAng = ang;
        }
        return best;
    }

    /** Ping: marks what you aim at (or the closest thing near the crosshair). Ping the same target again / nothing = unmark. */
    public static void ping(boolean message) {
        LivingEntity t = aimed != null ? aimed : coneTarget(8.0);
        if (t == null || t == pinned) {
            boolean had = pinned != null;
            pinned = null;
            if (message) Chat.send(had ? "Target cleared." : "No target in sight.");
            return;
        }
        pinned = t;
        if (message) Chat.send("Target: " + t.getDisplayName().getString());
    }
}
