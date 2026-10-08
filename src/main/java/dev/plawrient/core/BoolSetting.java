package dev.plawrient.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, boolean def) { super(name, def); }

    @Override public boolean parse(String s) {
        switch (s.toLowerCase()) {
            case "true", "on", "1" -> set(true);
            case "false", "off", "0" -> set(false);
            default -> { return false; }
        }
        return true;
    }
    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(e.getAsBoolean()); }
    @Override public String display() { return value ? "ON" : "OFF"; }
    @Override public void click(boolean right) { set(!value); }
}
