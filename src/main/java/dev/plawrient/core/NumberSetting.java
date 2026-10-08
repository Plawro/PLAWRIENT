package dev.plawrient.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public class NumberSetting extends Setting<Double> {
    public final double min, max;

    public NumberSetting(String name, double def, double min, double max) {
        super(name, def);
        this.min = min; this.max = max;
    }
    private double clamp(double v) { return Math.max(min, Math.min(max, v)); }

    @Override public boolean parse(String s) {
        try { set(clamp(Double.parseDouble(s))); return true; }
        catch (NumberFormatException e) { return false; }
    }
    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { set(clamp(e.getAsDouble())); }
    @Override public String display() { return String.format(Locale.ROOT, "%.2f", value); }
    @Override public void click(boolean right) {
        double step = (max - min) / 10.0;
        set(clamp(value + (right ? -step : step)));
    }
}
