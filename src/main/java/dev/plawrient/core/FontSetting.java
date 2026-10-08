package dev.plawrient.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Font UI. Klik v menu otevre vyber fontu (vanilla + systemove).
 * Prikaz: &set hud font <default|alt|illageralt|uniform|nazev systemoveho fontu>
 */
public class FontSetting extends Setting<String> {
    public FontSetting(String name, String def) {
        super(name, def);
        Fonts.current = def;
    }

    @Override public void set(String v) {
        super.set(v);
        Fonts.current = v;
    }

    @Override public boolean parse(String s) {
        String t = s.trim();
        if (!t.contains(":") && Fonts.VANILLA.contains("minecraft:" + t)) t = "minecraft:" + t;
        if (Fonts.isValid(t)) { set(t); return true; }
        for (Fonts.SystemFont f : Fonts.systemFonts()) {
            if (f.name().equalsIgnoreCase(t)) { Fonts.useSystemFont(this, f); return true; }
        }
        return false;
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(e.getAsString()); }
    @Override public String display() { return value.contains(":") ? value.substring(value.indexOf(':') + 1) : value; }
}
