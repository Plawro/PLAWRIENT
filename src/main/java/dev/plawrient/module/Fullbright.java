package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/** Client-side night vision efekt (server o nem nevi). */
public class Fullbright extends Module {
    public Fullbright() { super("Fullbright", "Always full brightness", Category.RENDER); }

    static void keep(Player p) {
        MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
        if (cur == null || cur.getDuration() < 400)
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1000, 0, false, false, false));
    }

    static void clear(Player p) { p.removeEffect(MobEffects.NIGHT_VISION); }

    @Override public void onTick() {
        var p = mc().player;
        if (p != null) keep(p);
    }

    @Override public void onDisable() {
        var p = mc().player;
        if (p != null) clear(p);
    }
}
