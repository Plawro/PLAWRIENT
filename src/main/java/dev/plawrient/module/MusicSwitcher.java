package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;

/**
 * Pick the Minecraft music track that plays (or random / silence). Vanilla can only pick tracks at random,
 * so the module writes a tiny sounds.json into the PLAWRIENT resource pack (resourcepacks/PLAWRIENT-fonts) that
 * gives every track its own sound event; the first time it is used, resources reload once.
 * Your own songs: drop .ogg files (OGG Vorbis) into resourcepacks/PLAWRIENT-fonts/assets/plawrient/sounds/custom/
 * and press "Reload songs" in the Music tab of the menu (Right Shift). The Music tab is the player UI for all this.
 */
public class MusicSwitcher extends Module {
    private static final String[][] TRACKS = {
            {"Game - Minecraft", "music/game/calm1"}, {"Game - Clark", "music/game/calm2"},
            {"Game - Sweden", "music/game/calm3"}, {"Game - Subwoofer Lullaby", "music/game/hal1"},
            {"Game - Living Mice", "music/game/hal2"}, {"Game - Haggstrom", "music/game/hal3"},
            {"Game - Danny", "music/game/hal4"}, {"Game - Key", "music/game/nuance1"},
            {"Game - Oxygene", "music/game/nuance2"}, {"Game - Dry Hands", "music/game/piano1"},
            {"Game - Wet Hands", "music/game/piano2"}, {"Game - Mice on Venus", "music/game/piano3"},
            {"Creative - Biome Fest", "music/game/creative/creative1"}, {"Creative - Blind Spots", "music/game/creative/creative2"},
            {"Creative - Haunt Muskie", "music/game/creative/creative3"}, {"Creative - Aria Math", "music/game/creative/creative4"},
            {"Creative - Dreiton", "music/game/creative/creative5"}, {"Creative - Taswell", "music/game/creative/creative6"},
            {"Menu - Mutation", "music/menu/menu1"}, {"Menu - Moog City 2", "music/menu/menu2"},
            {"Menu - Beginning 2", "music/menu/menu3"}, {"Menu - Floating Trees", "music/menu/menu4"},
            {"Nether - Concrete Halls", "music/game/nether/nether1"}, {"Nether - Dead Voxel", "music/game/nether/nether2"},
            {"Nether - Warmth", "music/game/nether/nether3"}, {"Nether - Ballad of the Cats", "music/game/nether/nether4"},
            {"End - The End", "music/game/end/end"}, {"End - Boss", "music/game/end/boss"},
            {"Disc - 13", "records/13"}, {"Disc - Cat", "records/cat"}, {"Disc - Blocks", "records/blocks"},
            {"Disc - Chirp", "records/chirp"}, {"Disc - Far", "records/far"}, {"Disc - Mall", "records/mall"},
            {"Disc - Mellohi", "records/mellohi"}, {"Disc - Stal", "records/stal"}, {"Disc - Strad", "records/strad"},
            {"Disc - Ward", "records/ward"}, {"Disc - 11", "records/11"}, {"Disc - Wait", "records/wait"},
            {"Disc - Pigstep", "records/pigstep"}, {"Disc - Otherside", "records/otherside"}
    };

    /** Songs found in the custom folder: {label, "custom/<slug>"}. */
    private static final List<String[]> CUSTOM = new ArrayList<>();

    public static Path customDir() {
        return Fonts.packRoot().resolve("assets").resolve(Fonts.NS).resolve("sounds").resolve("custom");
    }

    private static String pretty(String slug) {
        StringBuilder sb = new StringBuilder();
        for (String w : slug.replace('_', ' ').replace('-', ' ').trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** Lists *.ogg in the custom folder. Files whose names Minecraft cannot use get a cleanly named copy. */
    static void scanCustom() {
        CUSTOM.clear();
        try {
            Path dir = customDir();
            if (!Files.isDirectory(dir)) return;
            List<Path> files;
            try (var st = Files.list(dir)) {
                files = st.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg")).sorted().toList();
            }
            for (Path f : files) {
                String fn = f.getFileName().toString();
                String slug = Fonts.slug(fn.substring(0, fn.length() - 4));
                if (!fn.equals(slug + ".ogg")) {
                    Path copy = dir.resolve(slug + ".ogg");
                    if (!Files.exists(copy)) Files.copy(f, copy);
                    continue;                                           // the clean copy is listed instead
                }
                CUSTOM.add(new String[]{"Custom - " + pretty(slug), "custom/" + slug});
            }
        } catch (Exception ignored) {}
    }

    static List<String[]> tracks() {
        List<String[]> l = new ArrayList<>(Arrays.asList(TRACKS));
        l.addAll(CUSTOM);
        return l;
    }

    private static List<String> options() {
        scanCustom();
        List<String> l = new ArrayList<>(List.of("Vanilla (default)", "Random", "Silence"));
        for (String[] t : tracks()) l.add(t[0]);
        return l;
    }

    public final ChoiceSetting track = add(new ChoiceSetting("track", "Vanilla (default)", options()));
    public final ChoiceSetting playlist = add(new ChoiceSetting("playlist", "Repeat one",
            List.of("Repeat one", "Next track", "Shuffle", "Play once")));
    public final BoolSetting loop = add(new BoolSetting("loop", true));
    public final NumberSetting pause = add(new NumberSetting("pauseSeconds", 0, 0, 300));
    public final BoolSetting toast = add(new BoolSetting("toast", true));

    private final Random rnd = new Random();
    private final Set<String> failed = new HashSet<>();
    private String lastSel = "";
    private String lastTrack = TRACKS[0][0];                 // what the Play button resumes
    private Music current;
    private String currentLabel;
    private boolean started, finishedOnce, attempted, customTried;
    private int startTick, waitTicks;
    private long toastUntil;

    public MusicSwitcher() { super("MusicSwitcher", "Choose which Minecraft music track plays", Category.FUN); }

    private static String key(String path) { return "t_" + path.replace('/', '_'); }

    private static Music music(String path) {
        SoundEvent ev = SoundEvent.createVariableRangeEvent(new ResourceLocation("plawrient", key(path)));
        return new Music(Holder.direct(ev), 0, 0, false);
    }

    /** Whole sounds.json of the PLAWRIENT pack (shared with BloodMoon so neither overwrites the other). */
    static String soundsJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String[] t : tracks()) {
            String path = t[1];
            String ns = path.startsWith("custom/") ? "plawrient" : "minecraft";
            sb.append(first ? "" : ",").append("\"").append(key(path)).append("\":{\"sounds\":[{\"name\":\"").append(ns).append(":")
              .append(path).append("\",\"stream\":true}]}");
            first = false;
        }
        if (BloodMoon.hasRecording())
            sb.append(",\"").append(BloodMoon.REC_KEY).append("\":{\"sounds\":[{\"name\":\"plawrient:")
              .append(BloodMoon.REC_KEY).append("\",\"stream\":true}]}");
        return sb.append("}").toString();
    }

    private boolean registered(String path) {
        return mc().getSoundManager().getSoundEvent(new ResourceLocation("plawrient", key(path))) != null;
    }

    private boolean ready() { return registered(TRACKS[0][1]); }

    private void setup() {
        attempted = true;
        try {
            Fonts.ensureMeta();
            Files.createDirectories(customDir());
            Fonts.writeIfChanged("sounds.json", soundsJson());
            Chat.send("Setting up music tracks - resources will reload once...");
            Fonts.enablePackAndReload();
        } catch (Exception e) {
            Chat.send("Could not set up music: " + e.getMessage());
        }
    }

    // ---------- API for the player UI (Music tab) ----------
    public static MusicSwitcher get() { return (MusicSwitcher) ModuleManager.INSTANCE.get("MusicSwitcher"); }

    public List<String[]> trackList() { return tracks(); }
    public String selected() { return track.get(); }
    public String lastTrack() { return lastTrack; }

    private static boolean isReal(String label) {
        return !(label.startsWith("Vanilla") || label.equals("Random") || label.equals("Silence"));
    }

    public boolean playingNow() {
        return current != null && mc().getMusicManager().isPlayingMusic(current);
    }

    /** Label of the track that is audible right now, or null. */
    public String nowLabel() { return playingNow() ? currentLabel : null; }

    public int elapsedSeconds() {
        var p = mc().player;
        return p != null && started ? Math.max(0, (p.tickCount - startTick) / 20) : 0;
    }

    public void play(String label) {
        if (!isEnabled()) setEnabled(true);
        if (isReal(label)) lastTrack = label;
        track.set(label);
        ModuleManager.INSTANCE.save();
    }

    /** Play button: stops (silence) when something plays, otherwise resumes the last track. */
    public void playStop() {
        String sel = track.get();
        if (isReal(sel) || sel.equals("Random")) play("Silence");
        else play(lastTrack);
    }

    public void step(int dir) {
        List<String[]> l = tracks();
        if (l.isEmpty()) return;
        String base = isReal(track.get()) ? track.get() : lastTrack;
        int idx = 0;
        for (int i = 0; i < l.size(); i++) if (l.get(i)[0].equals(base)) idx = i;
        int next = playlist.get().equals("Shuffle") && dir > 0 && l.size() > 1
                ? (idx + 1 + rnd.nextInt(l.size() - 1)) % l.size()
                : Math.floorMod(idx + dir, l.size());
        play(l.get(next)[0]);
    }

    public void cyclePlaylist() { playlist.click(false); ModuleManager.INSTANCE.save(); }

    /** Re-reads the custom folder, rewrites sounds.json and reloads the resources. */
    public void reloadSongs() {
        scanCustom();
        List<String> opts = track.options();
        opts.removeIf(o -> o.startsWith("Custom - "));
        for (String[] t : CUSTOM) opts.add(t[0]);
        customTried = true;
        setup();
    }

    public int customCount() { return CUSTOM.size(); }

    @Override public void onDisable() {
        var mm = mc().getMusicManager();
        if (current != null && mm.isPlayingMusic(current)) mm.stopPlaying(); // hand control back to vanilla
        current = null; started = false; lastSel = "";
    }

    /** Next song after the current one finished (playlist modes). */
    private void advance(boolean random) {
        List<String[]> l = tracks();
        if (l.isEmpty()) return;
        int idx = 0;
        for (int i = 0; i < l.size(); i++) if (l.get(i)[0].equals(track.get())) idx = i;
        int next = random && l.size() > 1 ? (idx + 1 + rnd.nextInt(l.size() - 1)) % l.size() : (idx + 1) % l.size();
        lastTrack = l.get(next)[0];
        track.set(l.get(next)[0]);
        ModuleManager.INSTANCE.save();
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        var p = mc.player;
        if (mc.level == null || p == null) return;
        var mm = mc.getMusicManager();
        String sel = track.get();

        if (!sel.equals(lastSel)) {
            if (sel.startsWith("Vanilla") && current != null && mm.isPlayingMusic(current)) mm.stopPlaying();
            lastSel = sel; current = null; started = false; waitTicks = 0; finishedOnce = false; failed.clear();
            if (isReal(sel)) lastTrack = sel;
            if (!sel.startsWith("Vanilla")) mm.stopPlaying();
        }
        if (sel.startsWith("Vanilla")) return;
        if (!ready()) { if (!attempted) setup(); return; }
        if (mc.getSituationalMusic().replaceCurrentMusic()) return;     // menu / end / credits keep their own music
        if (sel.equals("Silence")) { mm.stopPlaying(); return; }
        if (current != null && mm.isPlayingMusic(current)) return;

        if (started) {                                                  // our track has just ended
            started = false;
            if (p.tickCount - startTick < 30) {                          // ended instantly = the file is missing
                failed.add(currentLabel);
                Chat.send("Track unavailable (sound file missing): " + currentLabel);
                current = null;
                if (!sel.equals("Random")) finishedOnce = true;
            } else if (!sel.equals("Random")) {
                switch (playlist.get()) {
                    case "Next track" -> { advance(false); return; }
                    case "Shuffle" -> { advance(true); return; }
                    case "Play once" -> finishedOnce = true;
                    default -> { if (!loop.get()) finishedOnce = true; else waitTicks = (int) (pause.get() * 20); }
                }
            } else {
                waitTicks = (int) (pause.get() * 20);
            }
        }
        if (finishedOnce) return;
        if (waitTicks > 0) { waitTicks--; return; }

        String[] t = null;
        if (sel.equals("Random")) {
            List<String[]> pool = tracks().stream()
                    .filter(x -> !failed.contains(x[0]) && (!x[1].startsWith("custom/") || registered(x[1]))).toList();
            if (pool.isEmpty()) { finishedOnce = true; return; }
            t = pool.get(rnd.nextInt(pool.size()));
        } else {
            for (String[] x : tracks()) if (x[0].equals(sel)) t = x;
        }
        if (t == null) { finishedOnce = true; return; }

        if (t[1].startsWith("custom/") && !registered(t[1])) {          // song added after the pack was built
            if (!customTried) { customTried = true; setup(); }
            return;
        }

        currentLabel = t[0];
        current = music(t[1]);
        mm.stopPlaying();
        mm.startPlaying(current);
        started = true;
        startTick = p.tickCount;
        toastUntil = System.currentTimeMillis() + 5000;
    }

    @Override public void onRender2D(GuiGraphics g) {
        if (!toast.get() || currentLabel == null || System.currentTimeMillis() > toastUntil || mc().options.hideGui) return;
        String txt = "Now playing: " + currentLabel;
        int w = Draw.width(txt) + 16;
        int y = g.guiHeight() - 24;
        Draw.rrect(g, 8, y, w, 15, 5, Draw.bg(0.8));
        Draw.rrect(g, 8, y + 3, 2, 9, 1, Draw.ACCENT);
        Draw.text(g, txt, 15, y + 4, Draw.TEXT, true);
    }
}
