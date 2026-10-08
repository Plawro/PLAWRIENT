package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import dev.plawrient.gui.EspRenderTypes;
import dev.plawrient.gui.Lines;
import dev.plawrient.gui.Projection;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Baritone-style route display (it only SHOWS the path, it does not walk):
 *  - no waypoints: real-time path to the block you look at (long range, works from freecam too)
 *  - with waypoints: path through them in order (&wp add / AddWaypoint key); reached ones are skipped
 */
public class Route extends Module {
    public static Route INSTANCE;

    public final NumberSetting range = add(new NumberSetting("range", 128, 16, 256));
    public final BoolSetting useWaypoints = add(new BoolSetting("waypoints", true));
    public final BoolSetting throughWalls = add(new BoolSetting("throughWalls", true));
    public final BoolSetting markers = add(new BoolSetting("markers", true));
    public final BoolSetting info = add(new BoolSetting("info", true));
    public final NumberSetting maxNodes = add(new NumberSetting("maxNodes", 40000, 5000, 200000));
    public final NumberSetting budget = add(new NumberSetting("budgetMs", 4, 1, 15));

    private static final class Segment {
        final BlockPos start, goal;
        final Pathfinder pf;
        List<Vec3> pts = List.of();
        Segment(ClientLevel l, BlockPos s, BlockPos g, int maxNodes) {
            start = s; goal = g; pf = new Pathfinder(l, s, g, maxNodes);
        }
    }

    private final List<Segment> segments = new ArrayList<>();
    private final Map<String, Segment> cache = new HashMap<>();
    private ClientLevel lastLevel;
    private String signature = "";
    private BlockPos planStart, lookGoal;
    private int chainStart, ticks;
    private Waypoints.Waypoint target;

    public Route() {
        super("Route", "Long-range path to where you look / through waypoints", Category.PLAYER);
        INSTANCE = this;
    }

    public void setTarget(Waypoints.Waypoint w) { target = w; signature = ""; }
    public void clearTarget() { target = null; signature = ""; }
    public void resetChain() { chainStart = 0; signature = ""; }

    private void clearAll() { segments.clear(); cache.clear(); signature = ""; planStart = null; }

    @Override public void onDisable() { clearAll(); lookGoal = null; }

    private static boolean reached(LocalPlayer p, Waypoints.Waypoint w) {
        return Math.abs(p.getX() - (w.x() + 0.5)) < 2.5 && Math.abs(p.getZ() - (w.z() + 0.5)) < 2.5
                && Math.abs(p.getY() - w.y()) < 3;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) { clearAll(); lastLevel = null; return; }
        if (level != lastLevel) { clearAll(); lastLevel = level; chainStart = 0; target = null; lookGoal = null; }
        ticks++;

        List<BlockPos> goals = new ArrayList<>();
        String sig;
        List<Waypoints.Waypoint> wps = Waypoints.current();
        if (target != null) {
            goals.add(target.pos());
            sig = "T" + target.pos().asLong();
        } else if (useWaypoints.get() && !wps.isEmpty()) {
            while (chainStart < wps.size() && reached(p, wps.get(chainStart))) {
                Chat.send("Waypoint reached: " + wps.get(chainStart).name());
                chainStart++;
            }
            if (chainStart >= wps.size()) { clearAll(); return; }
            StringBuilder sb = new StringBuilder("W").append(chainStart);
            for (int i = chainStart; i < Math.min(wps.size(), chainStart + 8); i++) {
                goals.add(wps.get(i).pos());
                sb.append(':').append(wps.get(i).pos().asLong());
            }
            sig = sb.toString();
        } else {
            if (ticks % 5 == 0) {
                BlockPos g = Pathfinder.lookTarget(mc, range.get());
                if (g != null && (lookGoal == null || lookGoal.distSqr(g) > 4)) lookGoal = g;
            }
            if (lookGoal == null) { clearAll(); return; }
            goals.add(lookGoal);
            sig = "L" + lookGoal.asLong();
        }

        BlockPos start = p.blockPosition();
        boolean drift = planStart == null || planStart.distSqr(start) > 36;
        if (!sig.equals(signature) || drift) {
            Map<String, Segment> old = new HashMap<>(cache);
            cache.clear();
            segments.clear();
            BlockPos s = start;
            for (BlockPos g : goals) {
                String key = s.asLong() + ">" + g.asLong();
                Segment seg = old.get(key);
                if (seg == null) seg = new Segment(level, s, g, maxNodes.get().intValue());
                cache.put(key, seg);
                segments.add(seg);
                s = g;
            }
            signature = sig;
            planStart = start;
        }

        for (Segment seg : segments) { // search one segment at a time within the per-tick budget
            if (seg.pf.isDone()) continue;
            seg.pf.run((long) (budget.get() * 1_000_000L));
            if (ticks % 8 == 0 || seg.pf.isDone()) seg.pts = seg.pf.path();
            break;
        }
    }

    @Override public void onRender3D(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float pt) {
        if (mc().level == null) return;
        if (!segments.isEmpty()) {
            var vc = buf.getBuffer(throughWalls.get() ? EspRenderTypes.LINES : EspRenderTypes.LINES_DEPTH);
            for (Segment seg : segments) {
                boolean ok = seg.pf.found();
                float r = ok ? 0.2f : 1f, g = ok ? 1f : 0.65f, b = ok ? 0.4f : 0.1f;
                List<Vec3> pts = seg.pts;
                for (int i = 0; i < pts.size() - 1; i++) {
                    Vec3 a = pts.get(i), c = pts.get(i + 1);
                    Lines.line(ps, vc, a.x, a.y, a.z, c.x, c.y, c.z, r, g, b, 1f);
                    Lines.line(ps, vc, a.x, a.y + 0.04, a.z, c.x, c.y + 0.04, c.z, r, g, b, 1f);
                }
                if (ok) {
                    BlockPos q = seg.goal;
                    LevelRenderer.renderLineBox(ps, vc, q.getX(), q.getY(), q.getZ(), q.getX() + 1, q.getY() + 1, q.getZ() + 1, r, g, b, 1f);
                }
            }
        }
        if (markers.get()) {
            var vc = buf.getBuffer(EspRenderTypes.LINES);
            for (Waypoints.Waypoint w : Waypoints.current()) {
                double x = w.x() + 0.5, z = w.z() + 0.5;
                Lines.line(ps, vc, x, w.y(), z, x, w.y() + 3, z, 0.29f, 0.48f, 1f, 1f);
                LevelRenderer.renderLineBox(ps, vc, x - 0.3, w.y(), z - 0.3, x + 0.3, w.y() + 0.6, z + 0.3, 0.29f, 0.48f, 1f, 1f);
            }
        }
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        Vec3 cam = Projection.camPos();

        if (markers.get()) {
            List<Waypoints.Waypoint> wps = Waypoints.current();
            for (int i = 0; i < wps.size(); i++) {
                Waypoints.Waypoint w = wps.get(i);
                float[] sp = Projection.toScreen(w.x() + 0.5, w.y() + 2.2, w.z() + 0.5);
                if (sp == null) continue;
                double d = cam.distanceTo(new Vec3(w.x() + 0.5, w.y(), w.z() + 0.5));
                boolean next = target == null && useWaypoints.get() && i == chainStart;
                String label = w.name() + "  " + (int) d + "m";
                int wd = Draw.width(label);
                int x = (int) sp[0] - wd / 2 - 4, y = (int) sp[1] - 14;
                Draw.rrect(g, x, y, wd + 8, 12, 4, 0xB00F1117);
                Draw.rrect(g, x, y + 3, 2, 6, 1, next ? 0xFF55E88A : Draw.ACCENT);
                Draw.text(g, label, x + 5, y + 2, Draw.TEXT);
            }
        }

        if (info.get() && !segments.isEmpty()) {
            int total = 0;
            boolean allDone = true, allFound = true;
            for (Segment s : segments) { total += s.pts.size(); allDone &= s.pf.isDone(); allFound &= s.pf.found(); }
            String status = !allDone ? "searching..." : allFound ? "complete" : "no full path (best partial)";
            int color = !allDone ? 0xFFFFE14D : allFound ? 0xFF55E88A : 0xFFFF9F1A;
            Draw.centered(g, "Route: " + total + " blocks - " + status, g.guiWidth() / 2, g.guiHeight() / 2 + 22, color, true);
        }
    }
}
