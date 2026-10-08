package dev.plawrient.module;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;

/** Replaces the vanilla full-screen fire overlay with a low flame strip along the bottom of the screen. */
public class LowFire extends Module {
    public static LowFire INSTANCE;

    public final NumberSetting height = add(new NumberSetting("height", 0.18, 0.05, 0.6));
    public final NumberSetting opacity = add(new NumberSetting("opacity", 0.9, 0.1, 1.0));

    public LowFire() {
        super("LowFire", "Low fire overlay (does not cover the screen)", Category.HUD);
        INSTANCE = this;
    }

    /** True = the vanilla fire overlay is cancelled. */
    public static boolean hidesVanilla() { return INSTANCE != null && INSTANCE.isEnabled(); }

    @Override public void onRenderPreHud(GuiGraphics g) {
        Minecraft mc = mc();
        var p = mc.player;
        if (p == null || !p.isOnFire() || p.isSpectator() || mc.options.hideGui) return;

        int w = g.guiWidth(), h = g.guiHeight();
        int size = (int) (h * height.get());
        g.fillGradient(0, h - (int) (size * 1.5), w, h, 0x00FF6A00, 0x50FF4500);

        TextureAtlasSprite sprite = ModelBakery.FIRE_1.sprite();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, opacity.get().floatValue());
        for (int x = -size / 2; x < w; x += size) g.blit(x, h - size, 0, size, size, sprite);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}
