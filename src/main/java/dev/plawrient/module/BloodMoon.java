package dev.plawrient.module;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Horror atmosphere: red tint + vignette, blood-red fog and a big red moon, optionally forced night, and a music box.
 * The music box plays YOUR recording if you drop it into the PLAWRIENT pack folder:
 *   resourcepacks/PLAWRIENT-fonts/assets/plawrient/sounds/puppet_music_box.ogg   (OGG Vorbis)
 * Without that file it falls back to a built-in lullaby played with note-block sounds (no copyrighted audio is bundled).
 */
public class BloodMoon extends Module {
    public static BloodMoon INSTANCE;

    public final NumberSetting intensity = add(new NumberSetting("intensity", 0.7, 0.1, 1.0));
    public final BoolSetting redMoon = add(new BoolSetting("redMoon", true));
    public final BoolSetting vignette = add(new BoolSetting("vignette", true));
    public final BoolSetting fog = add(new BoolSetting("fog", true));
    public final BoolSetting nightOnly = add(new BoolSetting("nightOnly", false));
    public final BoolSetting forceNight = add(new BoolSetting("forceNight", false));
    public final NumberSetting chance = add(new NumberSetting("chancePercent", 25, 0, 100)); // per night
    public final BoolSetting announce = add(new BoolSetting("announce", true));
    public final BoolSetting jingle = add(new BoolSetting("musicBox", true));
    public final NumberSetting jingleEvery = add(new NumberSetting("musicBoxEvery", 150, 30, 600));
    public final NumberSetting volume = add(new NumberSetting("volume", 0.5, 0.0, 1.0));
    public final BoolSetting useRecording = add(new BoolSetting("useRecording", true));

    static final String REC_KEY = "puppet_music_box";
    private SoundInstance rec;
    private boolean recSetupTried, hintShown;

    // lullaby: semitones relative to pitch 1.0, durations in units of 5 ticks
    private static final int[] N = {10, 8, 6, 5, 6, 8, 10, 10, 10, 8, 6, 5, 3, 5, 6, 8, 8, 8, 6, 5, 3, 2, 3, 5, 6, 3, 3,
            10, 8, 6, 5, 6, 8, 10, 6, 3, 3};
    private static final int[] B = {2, 2, 2, 2, 2, 2, 2, 2, 5, 2, 2, 2, 2, 2, 2, 2, 2, 5, 2, 2, 2, 2, 2, 2, 2, 2, 5,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 8};

    private final Random rnd = new Random();
    private long cacheIdx = Long.MIN_VALUE, announcedIdx = Long.MIN_VALUE;
    private double cacheChance = -1;
    private boolean cacheResult;
    private int idx = -1, wait, sinceLast;
    private float slow = 1f;

    public BloodMoon() {
        super("BloodMoon", "Red sky, fog, red moon and a music box", Category.FUN);
        INSTANCE = this;
    }

    @Override public void onEnable() {
        sinceLast = (int) (jingleEvery.get() * 20) - 100;
        recSetupTried = false; hintShown = false;
    }
    @Override public void onDisable() { idx = -1; stopRecording(); }

    // ---------- your own recording (puppet_music_box.ogg) ----------
    static Path recordingFile() {
        return Fonts.packRoot().resolve("assets").resolve(Fonts.NS).resolve("sounds").resolve(REC_KEY + ".ogg");
    }

    static boolean hasRecording() {
        try { return Files.isRegularFile(recordingFile()); } catch (Exception e) { return false; }
    }

    private void stopRecording() {
        if (rec != null) mc().getSoundManager().stop(rec);
        rec = null;
    }

    /** True when the recording is installed and loaded. Registers it (one resource reload) the first time. */
    private boolean recordingReady() {
        var sm = mc().getSoundManager();
        if (sm.getSoundEvent(new ResourceLocation(Fonts.NS, REC_KEY)) != null) return true;
        try {
            if (hasRecording()) {
                if (!recSetupTried) {
                    recSetupTried = true;
                    Fonts.ensureMeta();
                    Fonts.writeIfChanged("sounds.json", MusicSwitcher.soundsJson());
                    Chat.send("Loading the music box recording - resources will reload once...");
                    Fonts.enablePackAndReload();
                }
            } else if (!hintShown) {
                hintShown = true;
                Path dir = recordingFile().getParent();
                Files.createDirectories(dir);
                Chat.copyable("BloodMoon: put your puppet_music_box.ogg into " + dir + "  (click to copy the path), then toggle BloodMoon.",
                        dir.toString());
            }
        } catch (IOException e) {
            Chat.send("BloodMoon: could not set up the recording: " + e.getMessage());
        }
        return false;
    }

    /** 0..1 strength of the effect right now. */
    private long nightIndex() { return Math.floorDiv(mc().level.getDayTime() - 6000L, 24000L); }

    /** Deterministic roll per night: a given night always gives the same answer (changes at noon). */
    public boolean isBloodNight() {
        var level = mc().level;
        if (level == null) return false;
        if (forceNight.get()) return true;
        long n = nightIndex();
        double c = chance.get();
        if (n != cacheIdx || c != cacheChance) {
            Random r = new Random((Waypoints.worldKey().hashCode() * 31L + n * 0x9E3779B97F4A7C15L) ^ 0x5DEECE66DL);
            r.nextDouble(); r.nextDouble();
            cacheResult = r.nextDouble() * 100.0 < c;
            cacheIdx = n; cacheChance = c;
        }
        return cacheResult;
    }

    public float strength() {
        var level = mc().level;
        if (level == null || !isBloodNight()) return 0f;
        float night = Math.max(0f, -(float) Math.cos(level.getTimeOfDay(1f) * Math.PI * 2));
        float base = forceNight.get() ? 1f : nightOnly.get() ? night : 0.3f + 0.7f * night;
        return intensity.get().floatValue() * base;
    }

    // ---------- fog ----------
    public float[] fogColor(float r, float g, float b) {
        float s = strength();
        if (!fog.get() || s <= 0.01f) return new float[]{r, g, b};
        float k = s * 0.85f;
        return new float[]{r + (0.38f - r) * k, g + (0.02f - g) * k, b + (0.02f - b) * k};
    }

    public float fogFar(float far) {
        float s = strength();
        if (!fog.get() || s <= 0.01f) return far;
        return Mth.lerp(s * 0.8f, far, Math.min(far, 64f));
    }

    // ---------- screen overlay ----------
    @Override public void onRenderPreHud(GuiGraphics g) {
        float s = strength();
        if (s <= 0.02f || mc().player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, ((int) Mth.clamp(s * 70, 0, 110) << 24) | 0x8A0000);
        if (vignette.get()) {
            int t = Math.max(2, (int) (Math.min(w, h) * 0.012));
            int n = 22;
            for (int i = 0; i < n; i++) {
                float k = 1f - i / (float) n;
                int al = (int) (k * k * 170 * s);
                if (al < 4) continue;
                int c = (al << 24) | 0x300000;
                int o = i * t;
                g.fill(o, o, w - o, o + t, c);                       // top
                g.fill(o, h - o - t, w - o, h - o, c);               // bottom
                g.fill(o, o + t, o + t, h - o - t, c);               // left
                g.fill(w - o - t, o + t, w - o, h - o - t, c);       // right
            }
        }
    }

    // ---------- red moon ----------
    @Override public void onRenderSky(PoseStack ps, float pt) {
        var level = mc().level;
        float s = strength();
        if (level == null || !redMoon.get() || s <= 0.05f) return;

        float a = level.getTimeOfDay(pt) * (float) (Math.PI * 2);
        float dx = (float) Math.sin(a), dy = -(float) Math.cos(a);   // moon direction (see vanilla renderSky)
        if (dy < -0.12f) return;                                      // below the horizon

        Matrix4f m = ps.last().pose();
        float dist = 95f;
        float cx = dx * dist, cy = dy * dist;
        float rx = dy, ry = -dx;                                      // "right" axis (d x up), up axis = (0,0,1)

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        disc(m, cx, cy, rx, ry, 30f, 0.9f, 0.1f, 0.05f, 0.40f * s, 0.9f, 0.1f, 0.05f, 0f);   // glow
        disc(m, cx, cy, rx, ry, 17f, 0.95f, 0.08f, 0.05f, 1f, 0.55f, 0f, 0f, 1f);            // moon
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private void disc(Matrix4f m, float cx, float cy, float rx, float ry, float radius,
                      float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, cx, cy, 0f).color(r0, g0, b0, a0).endVertex();
        int seg = 32;
        for (int i = 0; i <= seg; i++) {
            double t = Math.PI * 2 * i / seg;
            float c = (float) Math.cos(t) * radius, sn = (float) Math.sin(t) * radius;
            bb.vertex(m, cx + rx * c, cy + ry * c, sn).color(r1, g1, b1, a1).endVertex();
        }
        BufferUploader.drawWithShader(bb.end());
    }

    // ---------- music box ----------
    private void playNote() {
        var mc = mc();
        float pitch = (float) Math.pow(2.0, N[idx] / 12.0) * (1f - 0.05f * (slow - 1f));
        pitch = Mth.clamp(pitch, 0.5f, 2f);
        float v = volume.get().floatValue();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Snd.s(SoundEvents.NOTE_BLOCK_CHIME), pitch, v));
        if (idx % 3 == 0)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(Snd.s(SoundEvents.NOTE_BLOCK_BELL), Mth.clamp(pitch * 0.5f, 0.5f, 2f), v * 0.3f));
    }

    @Override public void onTick() {
        var mc = mc();
        var level = mc.level;
        if (level == null || mc.player == null) return;

        if (!forceNight.get() && announce.get() && strength() > 0.25f) {
            long n = nightIndex();
            if (n != announcedIdx) { announcedIdx = n; Chat.send("The Blood Moon rises..."); }
        }
        if (forceNight.get()) level.setDayTime(18000L); // client side only; the server re-syncs every second

        if (!jingle.get() || volume.get() <= 0.0) { idx = -1; stopRecording(); return; }

        if (useRecording.get() && recordingReady()) {
            idx = -1;
            var sm = mc.getSoundManager();
            boolean playing = rec != null && sm.isActive(rec);
            if (strength() <= 0.2f) { if (playing) stopRecording(); return; }   // the blood moon is over
            if (playing) return;
            if (rec != null) { rec = null; sinceLast = 0; }                       // it just finished
            if (++sinceLast > jingleEvery.get() * 20 * (0.7 + rnd.nextDouble() * 0.6)) {
                sinceLast = 0;
                SoundEvent ev = SoundEvent.createVariableRangeEvent(new ResourceLocation(Fonts.NS, REC_KEY));
                rec = SimpleSoundInstance.forUI(ev, 1f, volume.get().floatValue());
                sm.play(rec);
            }
            return;
        }

        if (idx < 0) {
            if (strength() > 0.2f && ++sinceLast > jingleEvery.get() * 20 * (0.7 + rnd.nextDouble() * 0.6)) {
                idx = 0; wait = 0; slow = 1f; sinceLast = 0;
            }
            return;
        }
        if (--wait > 0) return;
        playNote();
        wait = Math.max(1, Math.round(B[idx] * 5 * slow));
        slow *= 1.015f; // the box winds down
        if (++idx >= N.length) idx = -1;
    }
}
