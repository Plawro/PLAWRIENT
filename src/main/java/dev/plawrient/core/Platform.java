package dev.plawrient.core;

import java.util.function.Function;

/** Veci, ktere zavisi na loaderu (nastavuje je forge/ fabric balicek). */
public final class Platform {
    private Platform() {}
    /** namespace -> citelny nazev modu (pro BlockInfo). */
    public static Function<String, String> modName = ns -> ns;
}
