package dev.plawrient.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Smooth zoom (Lunar-style): bind a key to this module in the menu. Hold mode = zoom while the key is held,
 * Toggle mode = press to zoom in / press again to zoom out. The magnification changes linearly over
 * animationMs. Mouse wheel changes the zoom amount while zooming.
 */
public class Zoom extends Module {
    public static Zoom INSTANCE;

    public final NumberSetting zoom = add(new NumberSetting("zoom", 4.0, 1.5, 30.0));
    public final NumberSetting animationMs = add(new NumberSetting("animationMs", 120, 0, 600));
    public final ChoiceSetting mode = add(new ChoiceSetting("mode", "Hold", List.of("Hold", "Toggle")));
    public final BoolSetting scrollAdjust = add(new BoolSetting("scrollAdjust", true));

    private double cur = 1.0;
    private long lastNs;
    private boolean wasDown, toggled;

    public Zoom() {
        super("Zoom", "Smooth zoom - set a keybind", Category.RENDER);
        INSTANCE = this;
        this.enabled = true;          // master switch; the key does the actual zooming
    }

    @Override public boolean usesKeyDirectly() { return true; }
    @Override public boolean showInList() { return false; }
    @Override public void onDisable() { cur = 1.0; toggled = false; }

    private boolean keyDown() {
        Minecraft mc = mc();
        int key = getKey();
        return key >= 0 && mc.screen == null && InputConstants.isKeyDown(mc.getWindow().getWindow(), key);
    }

    /** Called once per frame from the FOV event. @return current magnification (1 = none). */
    public double factor() {
        long now = System.nanoTime();
        double dt = lastNs == 0 ? 0 : (now - lastNs) / 1e9;
        lastNs = now;
        if (dt > 0.25) dt = 0.25;

        boolean down = keyDown();
        if (mode.get().equals("Toggle")) {
            if (down && !wasDown) toggled = !toggled;
        } else {
            toggled = down;
        }
        wasDown = down;

        double target = toggled ? zoom.get() : 1.0;
        double speed = (zoom.get() - 1.0) / Math.max(0.001, animationMs.get() / 1000.0); // linear
        if (cur < target) cur = Math.min(target, cur + speed * dt);
        else if (cur > target) cur = Math.max(target, cur - speed * dt);
        return cur;
    }

    /** Real zoom: magnification factor -> narrower field of view. */
    public static double apply(double fov, double factor) {
        double half = Math.toRadians(fov / 2.0);
        return Math.toDegrees(2.0 * Math.atan(Math.tan(half) / factor));
    }

    /** @return true if the scroll was used to change the zoom amount. */
    public boolean consumeScroll(double delta) {
        if (!isEnabled() || !scrollAdjust.get() || cur < 1.05 || mc().screen != null || delta == 0) return false;
        double z = zoom.get() * (1.0 + 0.12 * Math.signum(delta));
        zoom.set(Math.max(zoom.min, Math.min(zoom.max, z)));
        ModuleManager.INSTANCE.save();
        return true;
    }
}
