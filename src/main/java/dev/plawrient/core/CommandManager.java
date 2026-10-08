package dev.plawrient.core;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;

/** Commands start with & in chat. Fully client-side, nothing is sent to the server. */
public final class CommandManager {
    private CommandManager() {}

    /** @return true if the message was a command (and should not be sent). */
    public static boolean handle(String raw) {
        if (!raw.startsWith("&")) return false;
        String[] a = raw.substring(1).trim().split("\\s+");
        ModuleManager mm = ModuleManager.INSTANCE;
        if (a.length == 0 || a[0].isEmpty()) { help(); return true; }

        switch (a[0].toLowerCase()) {
            case "help" -> help();
            case "list" -> {
                for (Module m : mm.all())
                    Chat.send(m.name + " - " + (m.isEnabled() ? "ON" : "OFF") + " [" + keyName(m.getKey()) + "]");
            }
            case "toggle", "t" -> {
                Module m = a.length > 1 ? mm.get(a[1]) : null;
                if (m == null) Chat.send("Usage: &toggle <module>"); else toggle(m);
            }
            case "bind" -> {
                Module m = a.length > 2 ? mm.get(a[1]) : null;
                if (m == null) { Chat.send("Usage: &bind <module> <key|none>"); break; }
                if (a[2].equalsIgnoreCase("none")) { m.setKey(-1); }
                else {
                    try { m.setKey(InputConstants.getKey("key.keyboard." + a[2].toLowerCase()).getValue()); }
                    catch (IllegalArgumentException e) { Chat.send("Unknown key: " + a[2]); break; }
                }
                mm.save();
                Chat.send(m.name + " bind: " + keyName(m.getKey()));
            }
            case "ping" -> dev.plawrient.module.Targeting.ping(true);
            case "wp", "waypoint", "waypoints" -> dev.plawrient.module.Waypoints.handle(Arrays.copyOfRange(a, 1, a.length));
            case "settings" -> {
                Module m = a.length > 1 ? mm.get(a[1]) : null;
                if (m == null) { Chat.send("Usage: &settings <module>"); break; }
                if (m.settings().isEmpty()) Chat.send(m.name + " has no settings.");
                for (Setting<?> s : m.settings()) Chat.send(m.name + "." + s.name + " = " + s.display());
            }
            case "set" -> {
                Module m = a.length > 3 ? mm.get(a[1]) : null;
                Setting<?> s = m != null ? m.setting(a[2]) : null;
                if (s == null) { Chat.send("Usage: &set <module> <setting> <value>"); break; }
                String val = String.join(" ", Arrays.copyOfRange(a, 3, a.length));
                if (s.parse(val)) { mm.save(); Chat.send(m.name + "." + s.name + " = " + s.display()); }
                else Chat.send("Invalid value.");
            }
            default -> {
                Module m = mm.get(a[0]);
                if (m == null) Chat.send("Unknown command. Try &help"); else toggle(m);
            }
        }
        return true;
    }

    private static void toggle(Module m) {
        m.toggle();
        Chat.send(m.name + ": " + (m.isEnabled() ? "ON" : "OFF"));
    }

    private static void help() {
        Chat.send("&list | &<module> | &toggle <module> | &bind <module> <key|none>");
        Chat.send("&settings <module> | &set <module> <setting> <value>");
        Chat.send("Blocks: &set xray blocks add|remove <id> | clear | reset");
        Chat.send("Waypoints: &wp add [name] | here | list | remove <n> | clear | go <n|off> | reset");
        Chat.send("&ping = mark / unmark your target (key G by default, see TargetPing)");
        Chat.send("Menu: Right Shift");
    }

    public static String keyName(int key) {
        if (key < 0) return "none";
        return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
    }
}
