package dev.plawrient.module;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.io.InputStream;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side "Herobrine": a fake player with white eyes that now and then appears in the distance, stares at you
 * and vanishes when you look at him for a moment or get too close. Exists only on your screen (never on the server).
 */
public class Herobrine extends Module {
    private static final int ID = -48213;

    /** The fake entity (other modules skip it via EntityFilter.isFake). */
    public static final class Fake extends RemotePlayer {
        public Fake(ClientLevel level) { super(level, new GameProfile(new UUID(0L, 2L), "Herobrine")); }
        @Override public ResourceLocation getSkinTextureLocation() { return skin(); }
        @Override public boolean shouldShowName() { return false; }
    }

    private static ResourceLocation skin;

    static ResourceLocation skin() {
        if (skin != null) return skin;
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation steve = DefaultPlayerSkin.getDefaultSkin();
        try (InputStream in = mc.getResourceManager().open(steve)) {
            NativeImage img = NativeImage.read(in);
            for (int x : new int[]{9, 10, 13, 14}) img.setPixelRGBA(x, 12, 0xFFFFFFFF); // blank white eyes
            ResourceLocation id = new ResourceLocation("plawrient", "herobrine_skin");
            mc.getTextureManager().register(id, new DynamicTexture(img));
            skin = id;
        } catch (Exception e) {
            skin = steve;
        }
        return skin;
    }

    public final NumberSetting frequency = add(new NumberSetting("frequency", 120, 20, 1200));
    public final NumberSetting minDistance = add(new NumberSetting("minDistance", 16, 8, 40));
    public final NumberSetting maxDistance = add(new NumberSetting("maxDistance", 30, 12, 64));
    public final NumberSetting lifetime = add(new NumberSetting("lifetime", 30, 5, 120));
    public final BoolSetting vanishWhenLooked = add(new BoolSetting("vanishWhenLooked", true));
    public final BoolSetting sound = add(new BoolSetting("sound", true));
    public final BoolSetting spawnNow = add(new BoolSetting("spawnNow", false));

    private final Random rnd = new Random();
    private Fake hb;
    private int cooldown, age, lookTicks;

    public Herobrine() { super("Herobrine", "Occasionally appears in the distance (client-side)", Category.FUN); }

    @Override public void onEnable() { cooldown = 20 * 10; }
    @Override public void onDisable() { remove(); }

    private void remove() {
        var level = mc().level;
        if (hb != null && level != null) level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
        hb = null; age = 0; lookTicks = 0;
    }

    private static boolean free(ClientLevel level, BlockPos pos) {
        var st = level.getBlockState(pos);
        return st.getFluidState().isEmpty() && st.getCollisionShape(level, pos).isEmpty();
    }

    /** Feet Y of the standing spot in column x/z closest to the player's level (caves, forests and hills work), or MIN_VALUE. */
    private static int findFeet(ClientLevel level, int x, int py, int z) {
        for (int k = 0; k < 20; k++) {
            int y = py - 1 + ((k & 1) == 1 ? -((k + 1) / 2) : k / 2);
            BlockPos pos = new BlockPos(x, y, z);
            var st = level.getBlockState(pos);
            if (!st.getFluidState().isEmpty() || st.getCollisionShape(level, pos).isEmpty()) continue; // need solid ground
            if (free(level, pos.above()) && free(level, pos.above(2))) return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    private boolean visible(ClientLevel level, Vec3 from, double x, int feetY, double z, Entity ctx) {
        for (double h : new double[]{1.6, 0.9})
            if (level.clip(new ClipContext(from, new Vec3(x, feetY + h, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx))
                    .getType() == HitResult.Type.MISS) return true;
        return false;
    }

    private boolean trySpawn() {
        Minecraft mc = mc();
        ClientLevel level = mc.level;
        var p = mc.player;
        if (level == null || p == null || p.isSleeping()) return false;
        Vec3 look = p.getLookAngle();
        double lh = Math.max(1e-4, Math.hypot(look.x, look.z));
        int py = Mth.floor(p.getY());

        for (int i = 0; i < 80; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = minDistance.get() + rnd.nextDouble() * Math.max(1, maxDistance.get() - minDistance.get());
            double dx = Math.cos(ang) * dist, dz = Math.sin(ang) * dist;
            double dot = (look.x * dx + look.z * dz) / (dist * lh);
            double deg = Math.toDegrees(Math.acos(Mth.clamp(dot, -1, 1)));
            // first try the sides (no pop-in right in front of you); later attempts accept almost any direction
            double lo = i < 40 ? 25 : 10, hi = i < 40 ? 130 : 180;
            if (deg < lo || deg > hi) continue;

            int bx = Mth.floor(p.getX() + dx), bz = Mth.floor(p.getZ() + dz);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int fy = findFeet(level, bx, py, bz);
            if (fy == Integer.MIN_VALUE) continue;
            if (!visible(level, p.getEyePosition(), bx + 0.5, fy, bz + 0.5, p)) continue;   // must be in line of sight

            Fake f = new Fake(level);
            f.setId(ID);
            f.moveTo(bx + 0.5, fy, bz + 0.5, 0f, 0f);
            f.setNoGravity(true);
            level.addPlayer(ID, f); // addEntity is private in 1.20.1; addPlayer is the public route for AbstractClientPlayer
            hb = f; age = 0; lookTicks = 0;
            if (sound.get())
                level.playLocalSound(p.getX(), p.getY(), p.getZ(), Snd.s(SoundEvents.AMBIENT_CAVE), SoundSource.AMBIENT, 1f, 0.6f, false);
            return true;
        }
        return false;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        ClientLevel level = mc.level;
        var p = mc.player;
        if (level == null || p == null) { hb = null; return; }

        if (hb != null) {
            if (hb.isRemoved() || hb.level() != level) { hb = null; return; }
            age++;

            // face the player
            double dx = p.getX() - hb.getX(), dz = p.getZ() - hb.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * 57.29578) - 90f;
            float pitch = (float) -(Mth.atan2(p.getEyeY() - hb.getEyeY(), Math.hypot(dx, dz)) * 57.29578);
            hb.setYRot(yaw); hb.yRotO = yaw; hb.yBodyRot = yaw; hb.yBodyRotO = yaw;
            hb.yHeadRot = yaw; hb.yHeadRotO = yaw; hb.setXRot(pitch); hb.xRotO = pitch;

            double dist = Math.sqrt(p.distanceToSqr(hb));
            Vec3 toHb = hb.getEyePosition().subtract(p.getEyePosition()).normalize();
            double angle = Math.toDegrees(Math.acos(Mth.clamp(p.getLookAngle().dot(toHb), -1, 1)));
            boolean looking = angle < 10 && p.hasLineOfSight(hb);
            lookTicks = looking ? lookTicks + 1 : Math.max(0, lookTicks - 1);

            if ((vanishWhenLooked.get() && lookTicks > 14) || dist < 7 || age > lifetime.get() * 20) remove();
            return;
        }

        if (spawnNow.get()) {
            spawnNow.set(false);
            Chat.send(trySpawn() ? "Herobrine spawned." : "Herobrine: no suitable spot nearby - move or look around and try again.");
            return;
        }
        if (--cooldown > 0) return;
        cooldown = (int) (frequency.get() * 20 * (0.5 + rnd.nextDouble()));
        trySpawn();
    }
}
