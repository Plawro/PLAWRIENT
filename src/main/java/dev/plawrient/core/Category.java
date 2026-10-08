package dev.plawrient.core;

public enum Category {
    MOVEMENT("Movement"), RENDER("Render"), PLAYER("Player"), HUD("HUD"), FUN("Fun");

    public final String label;
    Category(String label) { this.label = label; }
}
