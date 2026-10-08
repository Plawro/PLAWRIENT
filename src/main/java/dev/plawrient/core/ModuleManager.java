package dev.plawrient.core;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.plawrient.module.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Module> modules = new ArrayList<>();
    private Path file;

    /** Sem pridavej dalsi moduly. */
    public void init(Path configDir) {
        register(new HudModule());
        register(new Fullbright());
        register(new InventoryMove());
        register(new Xray());
        register(new StorageEsp());
        register(new BlockEsp());
        register(new EntityEsp());
        register(new Nametags());
        register(new Fuse());
        register(new Vision());
        register(new DamageNumbers());
        register(new TargetHud());
        register(new BlockInfo());
        register(new Speed());
        register(new Freecam());
        register(new Route());
        register(new AddWaypoint());
        register(new AutoTool());
        register(new AutoArmor());
        register(new Awareness());
        register(new LowFire());
        register(new DeathInfo());
        register(new VitalsWarning());
        register(new Trajectory());
        register(new MusicSwitcher());
        register(new Zoom());
        register(new AutoWater());
        register(new AutoTotem());
        register(new AutoRefill());
        register(new ItemInfo());
        register(new TargetPing());
        register(new WaterClutch());
        register(new SwordBlock());
        register(new BloodMoon());
        register(new Haunting());
        register(new Herobrine());
        file = configDir.resolve("plawrient.json");
        load();
    }

    private void register(Module m) { modules.add(m); }
    public List<Module> all() { return modules; }

    public List<Module> byCategory(Category c) {
        return modules.stream().filter(m -> m.category == c).toList();
    }

    public Module get(String name) {
        for (Module m : modules) if (m.name.equalsIgnoreCase(name)) return m;
        return null;
    }

    public void tick() {
        Targeting.tick();
        for (Module m : modules) if (m.isEnabled()) m.onTick();
    }
    public void render2D(GuiGraphics g) { for (Module m : modules) if (m.isEnabled()) m.onRender2D(g); }
    public void render3D(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float pt) {
        for (Module m : modules) if (m.isEnabled()) m.onRender3D(ps, buf, cam, pt);
    }
    public void renderPreHud(GuiGraphics g) { for (Module m : modules) if (m.isEnabled()) m.onRenderPreHud(g); }
    public void renderSky(PoseStack ps, float pt) { for (Module m : modules) if (m.isEnabled()) m.onRenderSky(ps, pt); }
    public void input(Input in) { for (Module m : modules) if (m.isEnabled()) m.onInput(in); }

    public void onKey(int key) {
        if (key < 0) return;
        for (Module m : modules) {
            if (m.getKey() != key) continue;
            if (!m.usesKeyDirectly()) m.toggle();
            else if (m.isEnabled()) m.onKeyPress();
        }
    }

    public void save() {
        if (file == null) return;
        try {
            JsonObject root = new JsonObject();
            for (Module m : modules) {
                JsonObject o = new JsonObject();
                o.addProperty("enabled", m.isEnabled());
                o.addProperty("key", m.getKey());
                JsonObject s = new JsonObject();
                for (Setting<?> st : m.settings()) s.add(st.name, st.toJson());
                o.add("settings", s);
                root.add(m.name, o);
            }
            root.add("gui", GuiState.toJson());
            root.add("waypoints", Waypoints.toJson());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) { LOG.error("Nelze ulozit config", e); }
    }

    private void load() {
        if (!Files.exists(file)) { save(); return; }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.has("gui")) GuiState.fromJson(root.getAsJsonObject("gui"));
            if (root.has("waypoints")) Waypoints.fromJson(root.getAsJsonArray("waypoints"));
            for (Module m : modules) {
                if (!root.has(m.name)) continue;
                JsonObject o = root.getAsJsonObject(m.name);
                if (o.has("enabled")) m.setEnabledSilently(o.get("enabled").getAsBoolean());
                if (o.has("key")) m.setKey(o.get("key").getAsInt());
                if (o.has("settings")) {
                    JsonObject s = o.getAsJsonObject("settings");
                    for (Setting<?> st : m.settings()) {
                        try { if (s.has(st.name)) st.fromJson(s.get(st.name)); }
                        catch (Exception ex) { LOG.warn("Invalid setting {}.{}", m.name, st.name); }
                    }
                }
            }
        } catch (Exception e) { LOG.error("Nelze nacist config", e); }
    }
}
