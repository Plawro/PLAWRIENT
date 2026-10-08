package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Low health (red pulsing edges + heartbeat) and low hunger (chip + one chime) warnings. */
public class VitalsWarning extends Module {
    public final NumberSetting health = add(new NumberSetting("health", 6, 1, 19));   // HP (2 = 1 heart)
    public final NumberSetting hunger = add(new NumberSetting("hunger", 6, 1, 19));   // food points
    public final BoolSetting flash = add(new BoolSetting("flash", true));
    public final BoolSetting sound = add(new BoolSetting("sound", true));
    public final NumberSetting volume = add(new NumberSetting("volume", 0.7, 0.0, 1.0));

    private boolean lowHp, lowFood, hungerWarned;
    private float hp;
    private int food, beat, second = -1;

    public VitalsWarning() { super("VitalsWarning", "Low health / low hunger warning", Category.HUD); }

    @Override public void onTick() {
        Minecraft mc = mc();
        var p = mc.player;
        if (p == null || p.getAbilities().instabuild || p.isSpectator()) { lowHp = lowFood = false; return; }
        hp = p.getHealth();
        food = p.getFoodData().getFoodLevel();
        lowHp = hp > 0 && hp <= health.get();
        lowFood = food <= hunger.get();

        float v = volume.get().floatValue();
        if (sound.get() && v > 0) {
            if (lowHp) {
                if (--beat <= 0) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(Snd.s(SoundEvents.NOTE_BLOCK_BASEDRUM), 0.6f, v));
                    second = 4;
                    beat = (int) (14 + hp * 4);          // the lower the health, the faster the heartbeat
                }
                if (second >= 0 && --second < 0)
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(Snd.s(SoundEvents.NOTE_BLOCK_BASEDRUM), 0.5f, v * 0.8f));
            }
            if (lowFood && !hungerWarned) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(Snd.s(SoundEvents.NOTE_BLOCK_BELL), 0.7f, v * 0.6f));
                hungerWarned = true;
            }
        }
        if (!lowFood) hungerWarned = false;
    }

    @Override public void onRenderPreHud(GuiGraphics g) {
        if (!flash.get() || !lowHp || mc().player == null) return;
        float sev = 1f - Math.min(1f, hp / Math.max(1f, health.get().floatValue()));
        float pulse = 0.55f + 0.45f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 350.0));
        Draw.vignette(g, 0xB00000, (0.35f + 0.65f * sev) * pulse);
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.player == null || mc.options.hideGui || (!lowHp && !lowFood)) return;
        int y = g.guiHeight() - 62;
        if (lowHp) y = chip(g, y, String.format(Locale.ROOT, "LOW HEALTH  %.1f HP", hp), 0xFFFF3B3B);
        if (lowFood) chip(g, y, "LOW HUNGER  " + food + "/20", 0xFFFF9F1A);
    }

    private int chip(GuiGraphics g, int y, String text, int color) {
        int w = Draw.width(text) + 16;
        int a = 150 + (int) (80 * Math.abs(Math.sin(System.currentTimeMillis() / 300.0)));
        Draw.rrect(g, 8, y, w, 15, 5, Draw.bg(0.8));
        Draw.rrect(g, 8, y + 3, 2, 9, 1, (a << 24) | (color & 0xFFFFFF));
        Draw.text(g, text, 15, y + 4, color, true);
        return y - 18;
    }
}
