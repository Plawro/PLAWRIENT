package dev.plawrient.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Systemove fonty v UI. Vanilla neumi nacist font ze systemu primo, takze vybrany .ttf/.otf zkopirujeme
 * do vlastniho resource packu (resourcepacks/PLAWRIENT-fonts), pack zapneme a prenacteme zdroje.
 */
public final class Fonts {
    private Fonts() {}

    public static final String NS = "plawrient";
    public static final String PACK_NAME = "PLAWRIENT-fonts";
    public static final List<String> VANILLA = List.of("minecraft:default", "minecraft:uniform", "minecraft:alt", "minecraft:illageralt");

    public record SystemFont(String name, Path file) {}

    /** Aktualne vybrany font (nastavuje FontSetting). */
    public static volatile String current = "minecraft:default";

    private static volatile Set<String> installed;
    private static List<SystemFont> systemCache;
    private static String cachedFor;
    private static Style cachedStyle = Style.EMPTY;

    public static Path packRoot() { return Minecraft.getInstance().getResourcePackDirectory().resolve(PACK_NAME); }
    private static Path fontDir() { return packRoot().resolve("assets").resolve(NS).resolve("font"); }

    public static Set<String> installed() {
        Set<String> s = installed;
        if (s != null) return s;
        Set<String> out = new TreeSet<>();
        Path d = fontDir();
        if (Files.isDirectory(d)) {
            try (var st = Files.list(d)) {
                st.forEach(p -> {
                    String n = p.getFileName().toString();
                    if (n.endsWith(".json")) out.add(NS + ":" + n.substring(0, n.length() - 5));
                });
            } catch (IOException ignored) {}
        }
        installed = out;
        return out;
    }

    public static boolean isValid(String id) { return VANILLA.contains(id) || installed().contains(id); }

    public static ResourceLocation resolve(String id) {
        ResourceLocation rl = isValid(id) ? ResourceLocation.tryParse(id) : null;
        return rl != null ? rl : new ResourceLocation("minecraft", "default");
    }

    /** Styl s vybranym fontem (pri neplatnem fontu spadne na vanilla default - zadne ctverecky). */
    public static Style style() {
        String c = current;
        if (!c.equals(cachedFor)) {
            cachedStyle = Style.EMPTY.withFont(resolve(c));
            cachedFor = c;
        }
        return cachedStyle;
    }

    public static String slug(String name) { return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"); }

    public static synchronized List<SystemFont> systemFonts() {
        if (systemCache != null) return systemCache;
        List<Path> roots = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", "");
        if (os.contains("win")) {
            String w = System.getenv("WINDIR");
            roots.add(Path.of(w != null ? w : "C:\\Windows", "Fonts"));
            String la = System.getenv("LOCALAPPDATA");
            if (la != null) roots.add(Path.of(la, "Microsoft", "Windows", "Fonts"));
        } else if (os.contains("mac")) {
            roots.add(Path.of("/Library/Fonts"));
            roots.add(Path.of("/System/Library/Fonts"));
            roots.add(Path.of(home, "Library", "Fonts"));
        } else {
            roots.add(Path.of("/usr/share/fonts"));
            roots.add(Path.of("/usr/local/share/fonts"));
            roots.add(Path.of(home, ".fonts"));
            roots.add(Path.of(home, ".local", "share", "fonts"));
        }
        List<SystemFont> out = new ArrayList<>();
        for (Path r : roots) {
            if (!Files.isDirectory(r)) continue;
            try (var st = Files.walk(r, 4)) {
                st.filter(Files::isRegularFile).forEach(p -> {
                    String n = p.getFileName().toString();
                    String l = n.toLowerCase(Locale.ROOT);
                    if (l.endsWith(".ttf") || l.endsWith(".otf")) out.add(new SystemFont(n.substring(0, n.lastIndexOf('.')), p));
                });
            } catch (IOException | UncheckedIOException ignored) {}
        }
        out.sort(Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));
        systemCache = out;
        return out;
    }

    /** Zkopiruje font do packu a napise definici. @return id fontu (plawrient:slug) */
    public static String install(SystemFont f) throws IOException {
        Path dir = fontDir();
        Files.createDirectories(dir);
        String slug = slug(f.name());
        Files.copy(f.file(), dir.resolve(slug + ".ttf"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(dir.resolve(slug + ".json"),
                "{\"providers\":[{\"type\":\"ttf\",\"file\":\"" + NS + ":" + slug + ".ttf\",\"shift\":[0.0,0.0],"
                        + "\"size\":9.0,\"oversample\":8.0},{\"type\":\"reference\",\"id\":\"minecraft:default\"}]}");
        Files.writeString(packRoot().resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":15,\"description\":\"PLAWRIENT fonts\"}}");
        installed = null;
        return NS + ":" + slug;
    }

    /** Zapne pack (pokud neni zapnuty) a prenacte zdroje. */
    public static void enablePackAndReload() {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        String id = "file/" + PACK_NAME;
        List<String> sel = new ArrayList<>(repo.getSelectedIds());
        if (!sel.contains(id) && repo.getAvailableIds().contains(id)) {
            sel.add(id);
            repo.setSelected(sel);
            mc.options.updateResourcePacks(repo); // sam prenacte zdroje, kdyz se neco zmenilo
        } else {
            mc.reloadResourcePacks();
        }
    }

    public static void ensureMeta() throws IOException {
        Files.createDirectories(packRoot());
        Path m = packRoot().resolve("pack.mcmeta");
        if (!Files.exists(m)) Files.writeString(m, "{\"pack\":{\"pack_format\":15,\"description\":\"PLAWRIENT resources\"}}");
    }

    /** Writes assets/plawrient/<rel> into the pack. @return true if the content changed */
    public static boolean writeIfChanged(String rel, String content) throws IOException {
        Path f = packRoot().resolve("assets").resolve(NS).resolve(rel);
        Files.createDirectories(f.getParent());
        if (Files.exists(f) && Files.readString(f).equals(content)) return false;
        Files.writeString(f, content);
        return true;
    }

    public static void useSystemFont(FontSetting setting, SystemFont f) {
        try {
            String id = install(f);
            setting.set(id);
            ModuleManager.INSTANCE.save();
            Chat.send("Font installed (" + f.name() + "), reloading resources...");
            enablePackAndReload();
        } catch (Exception e) {
            Chat.send("Could not install font: " + e.getMessage());
        }
    }
}
