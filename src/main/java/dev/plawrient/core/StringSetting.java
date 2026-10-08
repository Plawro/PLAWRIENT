package dev.plawrient.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {
    public StringSetting(String name, String def) { super(name, def); }

    @Override public boolean parse(String s) { set(s); return true; }
    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(e.getAsString()); }
}
