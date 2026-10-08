package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;

/**
 * Xray: vybrane bloky se vykresluji (i bez sousedu), vsechny ostatni jsou neviditelne.
 * Samotne skryvani deli forge/XrayModel (obaluje modely bloku). Prepinani prestavi chunky.
 * Seznam bloku: klik na "blocks" v menu, nebo &set xray blocks add diamond_ore
 */
public class Xray extends Module {
    public static Xray INSTANCE;

    public final BlockListSetting blocks = add(new BlockListSetting("blocks",
            "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
            "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
            "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
            "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
            "nether_gold_ore", "nether_quartz_ore", "ancient_debris"));
    public final BoolSetting brightness = add(new BoolSetting("brightness", true));

    public Xray() {
        super("Xray", "See only selected blocks", Category.RENDER);
        INSTANCE = this;
        blocks.setOnChange(this::reload);
    }

    private void reload() {
        var mc = mc();
        if (mc.level != null && mc.levelRenderer != null && isEnabled()) mc.levelRenderer.allChanged();
    }

    @Override public void onEnable() {
        var mc = mc();
        if (mc.level != null && mc.levelRenderer != null) mc.levelRenderer.allChanged();
    }

    @Override public void onDisable() {
        var mc = mc();
        if (mc.level != null && mc.levelRenderer != null) mc.levelRenderer.allChanged();
        Module fb = ModuleManager.INSTANCE.get("Fullbright");
        if (mc.player != null && (fb == null || !fb.isEnabled())) Fullbright.clear(mc.player);
    }

    @Override public void onTick() {
        var p = mc().player;
        if (p != null && brightness.get()) Fullbright.keep(p);
    }
}
