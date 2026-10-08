package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Volna kamera: kamera se odpoji od hrace, hrac stoji na miste. Mys otaci kameru.
 * Pri zapnuti freecamu jsou zablokovane kliky (utok / pouziti), aby se neposilaly na server.
 * Pozor: otaceni mysi otaci i tvou postavu (po vypnuti se vrati puvodni smer).
 */
public class Freecam extends Module {
    public static Freecam INSTANCE;

    public final NumberSetting speed = add(new NumberSetting("speed", 0.8, 0.1, 5.0));
    public final NumberSetting sprintMultiplier = add(new NumberSetting("sprintMultiplier", 2.5, 1.0, 8.0));
    public final BoolSetting stopOnDamage = add(new BoolSetting("stopOnDamage", true));

    private RemotePlayer cam;
    private float savedYaw, savedPitch;
    private float lastHealth;

    public Freecam() {
        super("Freecam", "Free camera", Category.PLAYER);
        INSTANCE = this;
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.cam != null; }
    public static float yaw() { return Minecraft.getInstance().player.getYRot(); }
    public static float pitch() { return Minecraft.getInstance().player.getXRot(); }

    @Override public void onEnable() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) { enabled = false; return; }
        cam = new RemotePlayer(mc.level, mc.player.getGameProfile());
        cam.copyPosition(mc.player);
        cam.xo = cam.getX(); cam.yo = cam.getY(); cam.zo = cam.getZ();
        savedYaw = mc.player.getYRot();
        savedPitch = mc.player.getXRot();
        lastHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        mc.setCameraEntity(cam);
    }

    @Override public void onDisable() {
        Minecraft mc = mc();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
            mc.player.setYRot(savedYaw);
            mc.player.setXRot(savedPitch);
        }
        cam = null;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) { cam = null; enabled = false; return; }
        if (cam == null) return;

        // your real body is standing still while the camera flies - leave freecam as soon as it gets hurt
        float hpNow = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (stopOnDamage.get() && hpNow < lastHealth - 0.01f) {
            lastHealth = hpNow;
            setEnabled(false);
            Chat.send("Freecam disabled - you took damage!");
            return;
        }
        lastHealth = hpNow;

        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        cam.setYRot(yaw); cam.setXRot(pitch);
        cam.yRotO = yaw; cam.xRotO = pitch;

        var o = mc.options;
        double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double l = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double v = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 look = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3 left = new Vec3(Math.cos(yr), 0, Math.sin(yr));
        Vec3 d = look.scale(f).add(left.scale(l)).add(0, v, 0);
        double mult = o.keySprint.isDown() ? sprintMultiplier.get() : 1.0; // hold sprint to fly faster
        if (d.lengthSqr() > 1e-6) d = d.normalize().scale(speed.get() * mult); else d = Vec3.ZERO;

        cam.xo = cam.getX(); cam.yo = cam.getY(); cam.zo = cam.getZ();
        cam.setPos(cam.getX() + d.x, cam.getY() + d.y, cam.getZ() + d.z);
    }

    /** Hrac se nehybe (kamera se ovlada vlastni logikou). */
    @Override public void onInput(Input in) {
        if (!active()) return;
        in.up = false; in.down = false; in.left = false; in.right = false;
        in.forwardImpulse = 0; in.leftImpulse = 0;
        in.jumping = false; in.shiftKeyDown = false;
    }
}
