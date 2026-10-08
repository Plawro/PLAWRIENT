package dev.plawrient.core;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
    public final String name;
    protected T value;

    protected Setting(String name, T def) { this.name = name; this.value = def; }

    public T get() { return value; }
    public void set(T v) { this.value = v; }

    /** Parsovani z prikazu (&set modul nastaveni hodnota). */
    public abstract boolean parse(String s);
    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement e);
    public String display() { return String.valueOf(value); }
    /** Klik v menu. */
    public void click(boolean right) {}
}
