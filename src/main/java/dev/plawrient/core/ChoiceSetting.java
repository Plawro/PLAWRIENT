package dev.plawrient.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Locale;

/** Vyber z pevneho seznamu. V menu: levy klik otevre vyber, pravy klik prepne na predchozi. */
public class ChoiceSetting extends Setting<String> {
    private final List<String> options;

    public ChoiceSetting(String name, String def, List<String> options) {
        super(name, def);
        this.options = options;
    }

    public List<String> options() { return options; }

    @Override public boolean parse(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (String o : options) if (o.toLowerCase(Locale.ROOT).equals(t)) { set(o); return true; }
        for (String o : options) if (o.toLowerCase(Locale.ROOT).contains(t)) { set(o); return true; }
        return false;
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (options.contains(e.getAsString())) set(e.getAsString()); }

    @Override public void click(boolean right) {
        int i = options.indexOf(value);
        int n = options.size();
        set(options.get(((i + (right ? -1 : 1)) % n + n) % n));
    }
}
