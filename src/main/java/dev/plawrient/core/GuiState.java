package dev.plawrient.core;

import com.google.gson.*;
import java.util.*;

/** Pozice a stav panelu v menu (uklada se do configu). */
public final class GuiState {
    private GuiState() {}

    private static final Map<String, int[]> POS = new HashMap<>();
    private static final Set<String> OPEN = new HashSet<>();
    private static boolean initialized = false;

    public static int[] pos(String key, int defX, int defY) {
        return POS.computeIfAbsent(key, k -> new int[]{defX, defY});
    }

    public static boolean isOpen(String key) { return OPEN.contains(key); }
    public static void setOpen(String key, boolean open) { if (open) OPEN.add(key); else OPEN.remove(key); }

    /** Pri prvnim spusteni jsou vsechny panely otevrene. */
    public static void initDefaults() {
        if (initialized) return;
        initialized = true;
        for (Category c : Category.values()) OPEN.add(c.name());
    }

    public static JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonObject p = new JsonObject();
        POS.forEach((k, v) -> { JsonArray a = new JsonArray(); a.add(v[0]); a.add(v[1]); p.add(k, a); });
        o.add("positions", p);
        JsonArray op = new JsonArray();
        OPEN.forEach(s -> op.add(s));
        o.add("open", op);
        return o;
    }

    public static void fromJson(JsonObject o) {
        initialized = true;
        if (o.has("positions"))
            for (var e : o.getAsJsonObject("positions").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                POS.put(e.getKey(), new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()});
            }
        if (o.has("open"))
            for (JsonElement el : o.getAsJsonArray("open")) OPEN.add(el.getAsString());
    }
}
