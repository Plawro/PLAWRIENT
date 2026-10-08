package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.EspRenderTypes;
import dev.plawrient.gui.Lines;
import dev.plawrient.gui.Projection;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tesla-vision style overlay: every entity gets a box with ID, distance, speed and threat level, trees and
 * points of interest are labelled, the planner draws the best safe path in front of you, and a telemetry
 * block shows biome, time, light, speed and overall threat. Turning the module off removes everything.
 */
public class Vision extends Module {
    public final BoolSetting entities = add(new BoolSetting("entities", true));
    public final BoolSetting items = add(new BoolSetting("items", false));
    public final BoolSetting path = add(new BoolSetting("path", true));
    public final BoolSetting trees = add(new BoolSetting("trees", true));
    public final BoolSetting points = add(new BoolSetting("points", true));
    public final BoolSetting telemetry = add(new BoolSetting("telemetry", true));
    public final BoolSetting grayscale = add(new BoolSetting("grayscale", false));
    public final NumberSetting range = add(new NumberSetting("range", 40, 16, 64));
    public final NumberSetting labelScale = add(new NumberSetting("labelScale", 0.8, 0.5, 1.5));
    public final NumberSetting x = add(new NumberSetting("x", 6, 0, 400));
    public final NumberSetting y = add(new NumberSetting("y", 40, 0, 300));

    private record Obj(String[] lines, AABB box, int color) {}
    private record Cls(String tag, int color, int level) {}
    private record Ent(Entity e, double d) {}

    private static final int RED = 0xFFFF3B3B, ORANGE = 0xFFFF9F1A, YELLOW = 0xFFFFE14D,
            GREEN = 0xFF55E88A, CYAN = 0xFF4DD2FF, GRAY = 0xFFB0B6C4, TREE = 0xFF3FBF7F;

    private List<Obj> objects = List.of();
    private VisionPlanner.Result plan;
    private boolean grayLoaded;

    // telemetry (updated every tick)
    private int threat, hostileCount, objectCount;
    private String biome = "?", nearest = "";
    private double egoSpeed;
    private int light;
    private long dayTime;
    private boolean raining, thunder;

    public Vision() { super("Vision", "Tesla-style detection overlay", Category.RENDER); }

    @Override public void onEnable() { if (grayscale.get()) loadGray(); }

    @Override public void onDisable() {
        objects = List.of();
        plan = null;
        unloadGray();
    }

    private void loadGray() {
        mc().gameRenderer.loadEffect(new ResourceLocation("minecraft", "shaders/post/desaturate.json"));
        grayLoaded = true;
    }

    private void unloadGray() {
        if (grayLoaded) { mc().gameRenderer.shutdownEffect(); grayLoaded = false; }
    }

    // ---------- classification ----------
    private Cls classify(Entity e, double dist) {
        if (e instanceof Player) return new Cls("PLAYER", CYAN, 1);
        if (e instanceof ItemEntity) return new Cls("ITEM", GRAY, 0);
        if (e instanceof Enemy) {
            double eff = e instanceof Creeper ? dist / 1.8 : dist;
            if (eff < 6) return new Cls("HIGH THREAT", RED, 3);
            if (eff < 14) return new Cls("MEDIUM THREAT", ORANGE, 2);
            return new Cls("LOW THREAT", YELLOW, 1);
        }
        if (e instanceof NeutralMob nm) {
            if (nm.isAngry() && dist < 12) return new Cls("HIGH THREAT", RED, 3);
            return new Cls("NEUTRAL", 0xFFE6C84D, 1);
        }
        return new Cls("SAFE", GREEN, 0);
    }

    private static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    // ---------- tick ----------
    @Override public void onTick() {
        Minecraft mc = mc();
        ClientLevel level = mc.level;
        var p = mc.player;
        if (level == null || p == null) { plan = null; objects = List.of(); return; }

        if (grayscale.get() && !grayLoaded) loadGray();
        else if (!grayscale.get() && grayLoaded) unloadGray();

        // threat + telemetry
        double sum = 0;
        int hostiles = 0;
        double nd = Double.MAX_VALUE;
        String nn = "";
        List<Entity> nearHostiles = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity) || e == p || EntityFilter.isFake(e)) continue;
            double d = Math.sqrt(p.distanceToSqr(e));
            if (d > 32) continue;
            Cls c = classify(e, d);
            boolean hostile = e instanceof Enemy || (e instanceof NeutralMob && c.level() >= 3);
            if (!hostile) continue;
            hostiles++;
            sum += c.level() == 3 ? 45 : c.level() == 2 ? 20 : 6;
            if (d < 12) nearHostiles.add(e);
            if (d < nd) { nd = d; nn = e.getDisplayName().getString(); }
        }
        if (p.getHealth() < 8) sum += 15;
        if (plan != null && plan.blocked()) sum += 25;
        threat = (int) Math.min(100, sum);
        hostileCount = hostiles;
        nearest = nd == Double.MAX_VALUE ? "" : String.format(Locale.ROOT, "%s %.1fm", nn, nd);

        BlockPos pos = p.blockPosition();
        biome = level.getBiome(pos).unwrapKey().map(k -> pretty(k.location().getPath())).orElse("?");
        light = level.getMaxLocalRawBrightness(pos);
        dayTime = level.getDayTime();
        raining = level.isRaining();
        thunder = level.isThundering();
        egoSpeed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20;

        if (path.get() && p.tickCount % 4 == 0) plan = VisionPlanner.plan(level, p, nearHostiles);
        if (!path.get()) plan = null;
        if (p.tickCount % 20 == 5) scan(level, mc.gameRenderer.getMainCamera().getPosition());
    }

    // ---------- world scan (trees, points of interest) ----------
    private void scan(ClientLevel level, Vec3 c) {
        boolean wantTrees = trees.get(), wantPts = points.get();
        if (!wantTrees && !wantPts) { objects = List.of(); return; }

        int r = range.get().intValue();
        double r2 = (double) r * r;
        int cx0 = (int) Math.floor((c.x - r) / 16.0), cx1 = (int) Math.floor((c.x + r) / 16.0);
        int cz0 = (int) Math.floor((c.z - r) / 16.0), cz1 = (int) Math.floor((c.z + r) / 16.0);

        Predicate<BlockState> interesting = st ->
                (wantTrees && st.is(BlockTags.LOGS))
                || (wantPts && (st.is(Blocks.SPAWNER) || st.is(Blocks.BELL) || st.is(Blocks.END_PORTAL_FRAME)
                        || st.is(Blocks.NETHER_PORTAL) || st.is(Blocks.LAVA)));

        List<Obj> out = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        outer:
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                LevelChunkSection[] secs = ch.getSections();
                int minSec = ch.getMinSection();
                for (int i = 0; i < secs.length; i++) {
                    LevelChunkSection sec = secs[i];
                    if (sec == null || sec.hasOnlyAir()) continue;
                    int baseY = (minSec + i) << 4;
                    if (baseY + 15 < c.y - r || baseY > c.y + r) continue;
                    if (!sec.maybeHas(interesting)) continue;
                    for (int yy = 0; yy < 16; yy++)
                        for (int zz = 0; zz < 16; zz++)
                            for (int xx = 0; xx < 16; xx++) {
                                BlockState st = sec.getBlockState(xx, yy, zz);
                                if (!interesting.test(st)) continue;
                                int wx = (cx << 4) + xx, wy = baseY + yy, wz = (cz << 4) + zz;
                                double dx = wx + 0.5 - c.x, dy = wy + 0.5 - c.y, dz = wz + 0.5 - c.z;
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                Obj o = make(level, m, st, wx, wy, wz);
                                if (o == null) continue;
                                String key = o.lines[0];
                                double spacing = key.equals("Lava") ? 6 : key.endsWith("Tree") ? 4.5 : 3;
                                Vec3 center = o.box.getCenter();
                                boolean dup = false;
                                for (Obj ex : out)
                                    if (ex.lines[0].equals(key) && ex.box.getCenter().distanceToSqr(center) < spacing * spacing) { dup = true; break; }
                                if (!dup) out.add(o);
                                if (out.size() >= 300) break outer;
                            }
                }
            }
        }

        // nearest first, with a cap per kind so the screen does not drown in labels
        out.sort(Comparator.comparingDouble(o -> o.box.getCenter().distanceToSqr(c)));
        Map<String, Integer> counts = new HashMap<>();
        List<Obj> keep = new ArrayList<>();
        for (Obj o : out) {
            String kind = o.lines[0].endsWith("Tree") ? "tree" : o.lines[0];
            int cap = kind.equals("tree") ? 10 : kind.equals("Lava") ? 5 : 12;
            int n = counts.merge(kind, 1, Integer::sum);
            if (n <= cap) keep.add(o);
        }
        objects = keep;
    }

    private Obj make(ClientLevel level, BlockPos.MutableBlockPos m, BlockState st, int wx, int wy, int wz) {
        AABB one = new AABB(wx, wy, wz, wx + 1, wy + 1, wz + 1);
        if (st.is(Blocks.SPAWNER)) return new Obj(new String[]{"Mob Spawner"}, one, ORANGE);
        if (st.is(Blocks.BELL)) return new Obj(new String[]{"Village Bell"}, one, 0xFF6EA8FF);
        if (st.is(Blocks.END_PORTAL_FRAME)) return new Obj(new String[]{"End Portal Frame"}, one, 0xFFB45CFF);
        if (st.is(Blocks.NETHER_PORTAL)) return new Obj(new String[]{"Nether Portal"}, one, 0xFFB45CFF);
        if (st.is(Blocks.LAVA)) return new Obj(new String[]{"Lava"}, one.inflate(1), RED);

        if (st.is(BlockTags.LOGS)) { // trunk base standing on dirt/grass, with leaves at the top
            m.set(wx, wy - 1, wz);
            if (!level.getBlockState(m).is(BlockTags.DIRT)) return null;
            int h = 1;
            while (h < 40) {
                m.set(wx, wy + h, wz);
                if (level.getBlockState(m).is(BlockTags.LOGS)) h++; else break;
            }
            if (h < 3) return null;
            int ty = wy + h - 1;
            boolean leaves = false;
            int[][] around = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] a : around) {
                m.set(wx + a[0], ty + a[1], wz + a[2]);
                if (level.getBlockState(m).is(BlockTags.LEAVES)) { leaves = true; break; }
            }
            if (!leaves) return null;
            String name = st.getBlock().getName().getString()
                    .replace(" Log", "").replace(" Stem", "").replace(" Wood", "").replace(" Hyphae", "");
            return new Obj(new String[]{name + " Tree"}, new AABB(wx - 2, wy, wz - 2, wx + 3, wy + h + 3, wz + 3), TREE);
        }
        return null;
    }

    // ---------- 2D overlay ----------
    private static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1, y1, x2, y1 + 1, c); g.fill(x1, y2 - 1, x2, y2, c);
        g.fill(x1, y1, x1 + 1, y2, c); g.fill(x2 - 1, y1, x2, y2, c);
    }

    private void drawBox(GuiGraphics g, AABB bb, String[] lines, int color) {
        float[] r = Projection.screenRect(bb);
        if (r == null) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        if (r[2] < 0 || r[3] < 0 || r[0] > sw || r[1] > sh || (r[2] - r[0]) > sw * 2) return;
        int x1 = (int) r[0], y1 = (int) r[1], x2 = Math.max((int) r[2], (int) r[0] + 4), y2 = Math.max((int) r[3], (int) r[1] + 4);

        g.fill(x1, y1, x2, y2, (color & 0x00FFFFFF) | 0x22000000);
        outline(g, x1, y1, x2, y2, color);

        float s = labelScale.get().floatValue();
        int w = 0;
        for (String l : lines) w = Math.max(w, Draw.width(l));
        int h = lines.length * 10 + 4;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x1, y1, 0);
        ps.scale(s, s, 1f);
        Draw.rrect(g, 0, -h - 1, w + 9, h, 2, 0xD0000000);
        g.fill(0, -h - 1, 2, -1, color);
        for (int i = 0; i < lines.length; i++)
            Draw.text(g, lines[i], 5, -h + 1 + i * 10, i == 0 ? Draw.TEXT : (i == lines.length - 1 ? color : Draw.TEXT_DIM));
        ps.popPose();
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        float pt = mc.getPartialTick();
        Vec3 cam = Projection.camPos();
        double r = range.get();
        int count = 0;

        if (entities.get()) {
            List<Ent> list = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (EntityFilter.isFake(e)) continue;
                if (e == mc.player && !Freecam.active()) continue;
                if (e instanceof ItemEntity && !items.get()) continue;
                if (!(e instanceof LivingEntity) && !(e instanceof ItemEntity)) continue;
                double d = Math.sqrt(cam.distanceToSqr(e.getX(), e.getY(), e.getZ()));
                if (d <= r) list.add(new Ent(e, d));
            }
            list.sort(Comparator.comparingDouble(Ent::d));
            for (int i = 0; i < Math.min(40, list.size()); i++) {
                Entity e = list.get(i).e();
                double d = list.get(i).d();
                Cls c = classify(e, d);
                double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
                AABB bb = e.getBoundingBox().move(ex - e.getX(), ey - e.getY(), ez - e.getZ());
                double sp = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo) * 20;
                String name = e instanceof ItemEntity ie ? ie.getItem().getHoverName().getString() : e.getDisplayName().getString();
                String[] lines = {
                        String.format(Locale.ROOT, "%s (%.1fm)  ID:%d", name, d, e.getId()),
                        sp > 0.4 ? String.format(Locale.ROOT, "Moving %.1f m/s", sp) : "Stationary",
                        c.tag()};
                drawBox(g, bb, lines, c.color());
                count++;
            }
        }
        for (Obj o : objects) {
            double d = Math.sqrt(cam.distanceToSqr(o.box.getCenter().x, o.box.getCenter().y, o.box.getCenter().z));
            if (d > r) continue;
            drawBox(g, o.box, new String[]{o.lines[0], String.format(Locale.ROOT, "%.1fm", d)}, o.color);
            count++;
        }
        objectCount = count;

        if (telemetry.get()) drawTelemetry(g);

        if ((plan != null && plan.blocked()) || threat >= 75) {
            int pulse = 210 + (int) (45 * Math.abs(Math.sin(System.currentTimeMillis() / 300.0)));
            int cx = g.guiWidth() / 2;
            Draw.rrect(g, cx - 28, 52, 56, 22, 7, (pulse << 24) | 0xE5262A);
            Draw.centered(g, "STOP", cx, 59, 0xFFFFFFFF, true);
        }
    }

    private void drawTelemetry(GuiGraphics g) {
        int hour = (int) ((dayTime % 24000 / 1000 + 6) % 24);
        int min = (int) (dayTime % 1000 * 60 / 1000);
        String weather = thunder ? "Thunderstorm" : raining ? "Rain" : "Clear";
        String word = threat < 25 ? "LOW" : threat < 60 ? "MEDIUM" : "HIGH";
        int threatColor = threat < 25 ? GREEN : threat < 60 ? ORANGE : RED;
        String pathState = plan == null ? "-" : plan.blocked() ? "HAZARD AHEAD" : "CLEAR";

        String[] lines = {
                "VISION ACTIVE",
                "Biome: " + biome,
                String.format(Locale.ROOT, "Time: %02d:%02d   Weather: %s", hour, min, weather),
                "Light: " + light + (light < 8 ? "  (mobs can spawn)" : ""),
                String.format(Locale.ROOT, "Ego speed: %.1f m/s", egoSpeed),
                "Objects: " + objectCount + "   Hostile: " + hostileCount,
                nearest.isEmpty() ? "Nearest threat: none" : "Nearest threat: " + nearest,
                "Threat: " + threat + "% " + word,
                "Path: " + pathState};
        int x0 = x.get().intValue(), y0 = y.get().intValue();
        for (int i = 0; i < lines.length; i++) {
            int color = Draw.TEXT;
            if (i == 0) color = Draw.ACCENT;
            else if (i == 7) color = threatColor;
            else if (i == 8) color = plan != null && plan.blocked() ? RED : GREEN;
            Draw.text(g, lines[i], x0, y0 + i * 10, color, true);
        }
    }

    // ---------- 3D: best path + predicted movement ----------
    @Override public void onRender3D(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float pt) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null) return;
        var vc = buf.getBuffer(EspRenderTypes.LINES_DEPTH);

        if (path.get() && plan != null) {
            List<Vec3> pts = plan.points();
            int n = pts.size();
            for (int i = 0; i < n - 1; i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                double dx = b.x - a.x, dz = b.z - a.z;
                double len = Math.hypot(dx, dz);
                if (len < 1e-4) continue;
                double px = -dz / len * 0.5, pz = dx / len * 0.5;
                float fade = 1f - 0.75f * i / (float) n;
                boolean bad = plan.danger()[i];
                float er = bad ? 1f : 0.95f, eg = bad ? 0.2f : 0.8f, eb = bad ? 0.2f : 0.25f;
                double ya = a.y + 0.06, yb = b.y + 0.06;
                Lines.line(ps, vc, a.x + px, ya, a.z + pz, b.x + px, yb, b.z + pz, er, eg, eb, fade);
                Lines.line(ps, vc, a.x - px, ya, a.z - pz, b.x - px, yb, b.z - pz, er, eg, eb, fade);
                Lines.line(ps, vc, a.x, ya, a.z, b.x, yb, b.z, 1f, bad ? 0.2f : 0.6f, 0.1f, fade);
                if (i % 2 == 0) Lines.line(ps, vc, a.x + px, ya, a.z + pz, a.x - px, ya, a.z - pz, er, eg, eb, fade * 0.4f);
            }
        }

        if (entities.get()) { // where each moving entity will be in 1 second
            double r2 = range.get() * range.get();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity) || (e == mc.player) || EntityFilter.isFake(e)) continue;
                double vx = e.getX() - e.xo, vz = e.getZ() - e.zo;
                if (Math.hypot(vx, vz) * 20 < 0.5) continue;
                double d2 = cam.distanceToSqr(e.getX(), e.getY(), e.getZ());
                if (d2 > r2) continue;
                Cls c = classify(e, Math.sqrt(d2));
                if (c.level() == 0) continue;
                double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()) + 0.1, ez = Mth.lerp(pt, e.zo, e.getZ());
                Lines.line(ps, vc, ex, ey, ez, ex + vx * 20, ey, ez + vz * 20,
                        Lines.red(c.color()), Lines.green(c.color()), Lines.blue(c.color()), 0.9f);
            }
        }
    }
}
