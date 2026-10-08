package dev.plawrient.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Seznam bloku. Uklada se jako id (retezce), na Block se prevadi az kdyz je potreba,
 * protoze bloky z modu se registruji az po nacteni modu.
 * Prikazy: &set <modul> blocks add <id> | remove <id> | clear | reset
 */
public class BlockListSetting extends Setting<Set<String>> {
    private final List<String> defaults;
    private volatile Set<Block> snap;
    private Runnable onChange = () -> {};

    public BlockListSetting(String name, String... defaultIds) {
        super(name, new LinkedHashSet<>(Arrays.stream(defaultIds).map(BlockListSetting::norm).toList()));
        this.defaults = Arrays.stream(defaultIds).map(BlockListSetting::norm).toList();
    }

    private static String norm(String id) { return id.contains(":") ? id : "minecraft:" + id; }

    public void setOnChange(Runnable r) { this.onChange = r; }

    /** Neměnná kopie, bezpečná pro čtení z jiných vláken (chunk meshing). */
    public Set<Block> snapshot() {
        Set<Block> s = snap;
        if (s == null) {
            Set<Block> out = new HashSet<>();
            for (String id : value) { Block b = lookup(id); if (b != null) out.add(b); }
            s = Set.copyOf(out);
            snap = s;
        }
        return s;
    }

    public boolean contains(Block b) { return snapshot().contains(b); }
    public int size() { return value.size(); }

    public void toggle(Block b) {
        String id = BuiltInRegistries.BLOCK.getKey(b).toString();
        if (!value.remove(id)) value.add(id);
        changed();
    }

    private void changed() { snap = null; onChange.run(); }

    public static Block lookup(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(norm(id));
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return null;
        Block b = BuiltInRegistries.BLOCK.get(rl);
        return b == Blocks.AIR ? null : b;
    }

    @Override public boolean parse(String s) {
        String[] a = s.trim().split("\\s+");
        switch (a[0].toLowerCase()) {
            case "add" -> {
                if (a.length < 2 || lookup(a[1]) == null) return false;
                value.add(norm(a[1]));
            }
            case "remove" -> {
                if (a.length < 2) return false;
                value.remove(norm(a[1]));
            }
            case "clear" -> value.clear();
            case "reset" -> { value.clear(); value.addAll(defaults); }
            default -> { return false; }
        }
        changed();
        return true;
    }

    @Override public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (String id : value) arr.add(new JsonPrimitive(id));
        return arr;
    }

    @Override public void fromJson(JsonElement e) {
        value.clear();
        for (JsonElement el : e.getAsJsonArray()) value.add(norm(el.getAsString()));
        snap = null;
    }

    @Override public String display() { return value.size() + " blocks"; }
}
