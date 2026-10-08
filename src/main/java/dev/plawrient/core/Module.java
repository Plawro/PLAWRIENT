package dev.plawrient.core;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    protected volatile boolean enabled = false;
    private int key = -1; // GLFW kod, -1 = nic
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
    }

    protected static Minecraft mc() { return Minecraft.getInstance(); }

    protected <S extends Setting<?>> S add(S s) { settings.add(s); return s; }
    public List<Setting<?>> settings() { return settings; }
    public Setting<?> setting(String n) {
        for (Setting<?> s : settings) if (s.name.equalsIgnoreCase(n)) return s;
        return null;
    }

    public boolean isEnabled() { return enabled; }
    public int getKey() { return key; }
    public void setKey(int key) { this.key = key; }

    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
        ModuleManager.INSTANCE.save();
    }
    /** Pro nacitani configu - nevola onEnable. */
    void setEnabledSilently(boolean e) { this.enabled = e; }

    /** True = module handles its own keybind (e.g. hold-to-zoom); the manager will not toggle it. */
    public boolean usesKeyDirectly() { return false; }
    /** Called when this module's key is pressed, for modules with usesKeyDirectly() == true (screen closed, module enabled). */
    public void onKeyPress() {}
    /** False = hidden from the HUD module list. */
    public boolean showInList() { return true; }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender2D(GuiGraphics g) {}
    /** 3D render pres zdi. PoseStack je uz posunuty o -kamera, kresli primo ve svetovych souradnicich. */
    public void onRender3D(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float pt) {}
    /** Pred vykreslenim HUD (pod HUD): napr. cerveny nadech. */
    public void onRenderPreHud(GuiGraphics g) {}
    /** Po vykresleni oblohy (PoseStack = jen rotace kamery): napr. cerveny mesic. */
    public void onRenderSky(PoseStack ps, float pt) {}
    /** Po tom, co hra spocita vstup hrace (WASD...), pred pohybem. */
    public void onInput(Input input) {}
}
