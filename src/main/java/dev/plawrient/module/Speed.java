package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Bhop + strafe: automaticky skace a drzi konstantni rychlost ve smeru WASD (i ve vzduchu). */
public class Speed extends Module {
    public final NumberSetting speed = add(new NumberSetting("speed", 0.32, 0.1, 1.5));
    public final BoolSetting bhop = add(new BoolSetting("bhop", true));

    public Speed() { super("Speed", "Bhop / strafe", Category.MOVEMENT); }

    @Override public void onInput(Input in) {
        LocalPlayer p = mc().player;
        if (p == null || Freecam.active()) return;
        if (p.isInWater() || p.isInLava() || p.onClimbable() || p.isFallFlying()
                || p.getAbilities().flying || p.isPassenger() || p.isSpectator() || p.isShiftKeyDown()) return;

        float f = in.forwardImpulse, s = in.leftImpulse;
        if (f == 0 && s == 0) return;
        if (bhop.get() && p.verticalCollisionBelow) in.jumping = true;

        double len = Math.sqrt(f * f + s * s);
        f /= len; s /= len;
        double yaw = Math.toRadians(p.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double sp = speed.get();
        Vec3 d = p.getDeltaMovement();
        p.setDeltaMovement((s * cos - f * sin) * sp, d.y, (f * cos + s * sin) * sp);
    }
}
