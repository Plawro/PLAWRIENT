package dev.plawrient.module;

import com.google.gson.*;
import dev.plawrient.core.Chat;
import dev.plawrient.core.ModuleManager;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Waypointy (per server / svet / dimenze). Prikazy: &wp add [name] | here | list | remove <n> | clear | go <n|off> | reset */
public final class Waypoints {
    private Waypoints() {}

    public record Waypoint(String name, int x, int y, int z, String dim, String world) {
        public BlockPos pos() { return new BlockPos(x, y, z); }
    }

    private static final List<Waypoint> ALL = new ArrayList<>();

    public static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        var s = mc.getSingleplayerServer();
        if (s != null) return "sp:" + s.getWorldData().getLevelName();
        return "unknown";
    }

    public static String dimKey() {
        var l = Minecraft.getInstance().level;
        return l == null ? "?" : l.dimension().location().toString();
    }

    public static List<Waypoint> current() {
        String w = worldKey(), d = dimKey();
        return ALL.stream().filter(x -> x.world().equals(w) && x.dim().equals(d)).toList();
    }

    public static Waypoint add(String name, BlockPos pos) {
        String n = (name == null || name.isBlank()) ? "WP" + (current().size() + 1) : name.trim();
        Waypoint wp = new Waypoint(n, pos.getX(), pos.getY(), pos.getZ(), dimKey(), worldKey());
        ALL.add(wp);
        ModuleManager.INSTANCE.save();
        Chat.send("Waypoint added: " + n + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
        return wp;
    }

    private static Waypoint find(String key) {
        List<Waypoint> cur = current();
        try {
            int i = Integer.parseInt(key) - 1;
            if (i >= 0 && i < cur.size()) return cur.get(i);
        } catch (NumberFormatException ignored) {}
        for (Waypoint w : cur) if (w.name().equalsIgnoreCase(key)) return w;
        return null;
    }

    public static void removeByName(String name) {
        String wk = worldKey(), dk = dimKey();
        ALL.removeIf(w -> w.world().equals(wk) && w.dim().equals(dk) && w.name().equalsIgnoreCase(name));
    }

    public static JsonArray toJson() {
        JsonArray arr = new JsonArray();
        for (Waypoint w : ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name()); o.addProperty("x", w.x()); o.addProperty("y", w.y()); o.addProperty("z", w.z());
            o.addProperty("dim", w.dim()); o.addProperty("world", w.world());
            arr.add(o);
        }
        return arr;
    }

    public static void fromJson(JsonArray arr) {
        ALL.clear();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            ALL.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(),
                    o.get("z").getAsInt(), o.get("dim").getAsString(), o.get("world").getAsString()));
        }
    }

    public static void handle(String[] a) {
        Minecraft mc = Minecraft.getInstance();
        String sub = a.length > 0 ? a[0].toLowerCase() : "list";
        String rest = a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : null;
        switch (sub) {
            case "add" -> {
                BlockPos p = Pathfinder.lookTarget(mc, 256); // works in freecam too (aims from the camera)
                if (p == null && mc.player != null) p = mc.player.blockPosition();
                if (p == null) { Chat.send("No position."); return; }
                add(rest, p);
            }
            case "here" -> { if (mc.player != null) add(rest, mc.player.blockPosition()); }
            case "list" -> {
                List<Waypoint> cur = current();
                if (cur.isEmpty()) Chat.send("No waypoints here. Use &wp add [name] while looking at a block.");
                for (int i = 0; i < cur.size(); i++) {
                    Waypoint w = cur.get(i);
                    Chat.send((i + 1) + ". " + w.name() + " (" + w.x() + ", " + w.y() + ", " + w.z() + ")");
                }
            }
            case "remove", "rm", "del" -> {
                Waypoint w = rest == null ? null : find(rest);
                if (w == null) { Chat.send("Usage: &wp remove <number|name>"); return; }
                ALL.remove(w);
                Route.INSTANCE.resetChain();
                ModuleManager.INSTANCE.save();
                Chat.send("Removed " + w.name());
            }
            case "clear" -> {
                String wk = worldKey(), dk = dimKey();
                ALL.removeIf(x -> x.world().equals(wk) && x.dim().equals(dk));
                Route.INSTANCE.resetChain();
                ModuleManager.INSTANCE.save();
                Chat.send("Waypoints cleared.");
            }
            case "go" -> {
                if (rest == null || rest.equalsIgnoreCase("off")) { Route.INSTANCE.clearTarget(); Chat.send("Route target cleared."); return; }
                Waypoint w = find(rest);
                if (w == null) { Chat.send("Unknown waypoint."); return; }
                Route.INSTANCE.setTarget(w);
                Chat.send("Routing to " + w.name() + " (enable the Route module to see it).");
            }
            case "reset" -> { Route.INSTANCE.resetChain(); Chat.send("Waypoint route restarted."); }
            default -> Chat.send("&wp add [name] | here [name] | list | remove <n> | clear | go <n|off> | reset");
        }
    }
}
