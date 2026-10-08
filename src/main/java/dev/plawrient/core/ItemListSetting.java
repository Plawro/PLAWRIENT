package dev.plawrient.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Serazeny seznam itemu (poradi = priorita). Ukladaji se id, takze funguji i itemy z modu.
 * Prikazy: &set <modul> <nastaveni> add <id> | remove <id> | clear | reset
 */
public class ItemListSetting extends Setting<Set<String>> {
    private final List<String> defaults;

    public ItemListSetting(String name, String... defaultIds) {
        super(name, new LinkedHashSet<>(Arrays.stream(defaultIds).map(ItemListSetting::norm).toList()));
        this.defaults = Arrays.stream(defaultIds).map(ItemListSetting::norm).toList();
    }

    private static String norm(String id) { return id.contains(":") ? id : "minecraft:" + id; }

    public static Item lookup(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(norm(id));
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        Item it = BuiltInRegistries.ITEM.get(rl);
        return it == Items.AIR ? null : it;
    }

    /** Resolved items in priority order. */
    public List<Item> items() {
        List<Item> out = new ArrayList<>();
        for (String id : value) { Item it = lookup(id); if (it != null) out.add(it); }
        return out;
    }

    public boolean contains(Item it) { return value.contains(BuiltInRegistries.ITEM.getKey(it).toString()); }
    public int size() { return value.size(); }

    public void toggle(Item it) {
        String id = BuiltInRegistries.ITEM.getKey(it).toString();
        if (!value.remove(id)) value.add(id);
    }

    @Override public boolean parse(String s) {
        String[] a = s.trim().split("\\s+");
        switch (a[0].toLowerCase()) {
            case "add" -> { if (a.length < 2 || lookup(a[1]) == null) return false; value.add(norm(a[1])); }
            case "remove" -> { if (a.length < 2) return false; value.remove(norm(a[1])); }
            case "clear" -> value.clear();
            case "reset" -> { value.clear(); value.addAll(defaults); }
            default -> { return false; }
        }
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
    }

    @Override public String display() { return value.size() + " items"; }
}
