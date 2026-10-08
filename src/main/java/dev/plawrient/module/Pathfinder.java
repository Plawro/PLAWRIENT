package dev.plawrient.module;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Baritone-lite: weighted A* nad nactenymi bloky. Pohyby: chuze (i diagonalne), schod nahoru (+1), seskok (az 3 bloky),
 * plavani. Vyhyba se lave, ohni, kaktusum, magme, sladkym keriku, pavucinam a utesum. Bezi po kouscich
 * (run(budget)) na hlavnim vlakne, takze je bezpecne cist svet; vysledek je nejlepsi dosud nalezena cesta.
 */
public final class Pathfinder {
    private static final class Node {
        final int x, y, z;
        Node parent;
        double g, f;
        boolean closed;
        Node(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
    private record Entry(Node n, double f) {}

    private static final double W = 1.5;
    private static final int[][] CARD = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAG = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final ClientLevel level;
    private final int gx, gy, gz;
    private final int maxNodes;
    private final PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(Entry::f));
    private final Map<Long, Node> nodes = new HashMap<>();
    private final Map<Long, LevelChunk> chunks = new HashMap<>();
    private final Map<Long, Integer> airCache = new HashMap<>();
    private final Map<Long, Boolean> groundCache = new HashMap<>();
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    private Node best;
    private double bestH = Double.MAX_VALUE;
    private boolean done, found;
    private int expanded;

    public Pathfinder(ClientLevel level, BlockPos start, BlockPos goal, int maxNodes) {
        this.level = level;
        this.maxNodes = maxNodes;
        int[] g = snap(goal.getX(), goal.getY(), goal.getZ());
        this.gx = g[0]; this.gy = g[1]; this.gz = g[2];
        Node s = new Node(start.getX(), start.getY(), start.getZ());
        nodes.put(BlockPos.asLong(s.x, s.y, s.z), s);
        open.add(new Entry(s, W * h(s.x, s.y, s.z)));
    }

    public boolean isDone() { return done; }
    public boolean found() { return found; }

    /** Aim target: the free cell in front of the block face you look at. */
    public static BlockPos lookTarget(Minecraft mc, double range) {
        Entity cam = mc.getCameraEntity();
        if (cam == null || mc.level == null) return null;
        HitResult hr = cam.pick(range, 1.0f, false);
        if (hr instanceof BlockHitResult b && hr.getType() == HitResult.Type.BLOCK)
            return b.getBlockPos().relative(b.getDirection());
        return null;
    }

    // ---------- search ----------
    public void run(long budgetNanos) {
        if (done) return;
        long end = System.nanoTime() + budgetNanos;
        int iter = 0;
        while (!open.isEmpty()) {
            if ((++iter & 31) == 0 && System.nanoTime() > end) return;
            Node n = open.poll().n();
            if (n.closed) continue;
            n.closed = true;
            expanded++;
            double h = h(n.x, n.y, n.z);
            if (h < bestH) { bestH = h; best = n; }
            if (Math.abs(n.x - gx) <= 1 && Math.abs(n.z - gz) <= 1 && Math.abs(n.y - gy) <= 2) {
                best = n; found = true; done = true; return;
            }
            if (expanded >= maxNodes) { done = true; return; }
            expand(n);
        }
        done = true;
    }

    public List<Vec3> path() {
        Node n = best;
        if (n == null) return List.of();
        List<Vec3> out = new ArrayList<>();
        while (n != null) { out.add(new Vec3(n.x + 0.5, n.y + 0.08, n.z + 0.5)); n = n.parent; }
        Collections.reverse(out);
        return out;
    }

    private double h(int x, int y, int z) {
        double dx = Math.abs(x - gx), dz = Math.abs(z - gz);
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + Math.abs(y - gy);
    }

    private void relax(Node from, int x, int y, int z, double cost) {
        long key = BlockPos.asLong(x, y, z);
        Node m = nodes.get(key);
        double ng = from.g + cost;
        if (m == null) {
            m = new Node(x, y, z);
            m.g = ng; m.parent = from; m.f = ng + W * h(x, y, z);
            nodes.put(key, m);
            open.add(new Entry(m, m.f));
        } else if (!m.closed && ng < m.g) {
            m.g = ng; m.parent = from; m.f = ng + W * h(x, y, z);
            open.add(new Entry(m, m.f));
        }
    }

    private void expand(Node n) {
        int x = n.x, y = n.y, z = n.z;
        boolean inWater = air(x, y, z) == 3;
        boolean supported = inWater || ground(x, y - 1, z);
        if (!supported) { // mid-air (e.g. the start): fall
            if (air(x, y - 1, z) >= 0) relax(n, x, y - 1, z, 1.0);
            return;
        }
        for (int[] d : CARD) move(n, d[0], d[1], false);
        for (int[] d : DIAG) move(n, d[0], d[1], true);
        if (inWater) {
            if (air(x, y + 1, z) >= 0 && air(x, y + 2, z) >= 0) relax(n, x, y + 1, z, 2.0);
            if (air(x, y - 1, z) >= 0) relax(n, x, y - 1, z, 2.0);
        }
    }

    private void move(Node n, int dx, int dz, boolean diag) {
        int x = n.x, y = n.y, z = n.z, nx = x + dx, nz = z + dz;
        int fa = air(nx, y, nz), ha = air(nx, y + 1, nz);
        if (fa >= 0 && ha >= 0) {
            if (fa == 3 || ground(nx, y - 1, nz)) { // traverse
                boolean ok = true;
                if (diag) ok = air(x + dx, y, z) >= 0 && air(x + dx, y + 1, z) >= 0
                        && air(x, y, z + dz) >= 0 && air(x, y + 1, z + dz) >= 0;
                if (ok) relax(n, nx, y, nz, (diag ? 1.4142 : 1.0) + fa * 0.5 + ha * 0.2);
            } else if (!diag) { // descend up to 3 blocks
                for (int d = 1; d <= 3; d++) {
                    int ca = air(nx, y - d, nz);
                    if (ca < 0) break;
                    if (ca == 3 || ground(nx, y - d - 1, nz)) { relax(n, nx, y - d, nz, 1.3 + 0.5 * d + ca * 0.5); break; }
                }
            }
        } else if (!diag) { // ascend by one
            if (air(x, y + 2, z) >= 0 && ground(nx, y, nz) && air(nx, y + 1, nz) >= 0 && air(nx, y + 2, nz) >= 0)
                relax(n, nx, y + 1, nz, 2.2);
        }
    }

    // ---------- world queries ----------
    private BlockState st(int x, int y, int z) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return null;
        int cx = x >> 4, cz = z >> 4;
        long ck = ChunkPos.asLong(cx, cz);
        LevelChunk ch = chunks.get(ck);
        if (ch == null && !chunks.containsKey(ck)) {
            ch = level.getChunkSource().getChunkNow(cx, cz);
            chunks.put(ck, ch);
        }
        if (ch == null) return null; // not loaded = not walkable
        mp.set(x, y, z);
        return ch.getBlockState(mp);
    }

    private static boolean hazard(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)
                || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE)
                || s.is(Blocks.COBWEB) || s.is(Blocks.LAVA);
    }

    /** -1 = cannot stand/pass here, 0 = free, 3 = water. */
    private int air(int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        Integer c = airCache.get(k);
        if (c != null) return c;
        int r = computeAir(x, y, z);
        airCache.put(k, r);
        return r;
    }

    private int computeAir(int x, int y, int z) {
        BlockState s = st(x, y, z);
        if (s == null || hazard(s)) return -1;
        FluidState f = s.getFluidState();
        if (f.is(FluidTags.LAVA)) return -1;
        mp.set(x, y, z);
        VoxelShape sh = s.getCollisionShape(level, mp);
        if (!sh.isEmpty() && sh.max(Direction.Axis.Y) > 0.2) return -1;
        return f.isEmpty() ? 0 : 3;
    }

    private boolean ground(int x, int y, int z) {
        long k = BlockPos.asLong(x, y, z);
        Boolean c = groundCache.get(k);
        if (c != null) return c;
        BlockState s = st(x, y, z);
        boolean r = false;
        if (s != null && !hazard(s)) {
            mp.set(x, y, z);
            VoxelShape sh = s.getCollisionShape(level, mp);
            r = !sh.isEmpty() && sh.max(Direction.Axis.Y) >= 0.4 && sh.max(Direction.Axis.Y) <= 1.0001;
        }
        groundCache.put(k, r);
        return r;
    }

    private int[] snap(int x, int y, int z) {
        for (int up = 0; up <= 3; up++) {
            if (air(x, y + up, z) >= 0 && air(x, y + up + 1, z) >= 0) { y += up; break; }
        }
        for (int d = 0; d < 8; d++) {
            if (air(x, y, z) == 3 || ground(x, y - 1, z)) return new int[]{x, y, z};
            if (air(x, y - 1, z) < 0) break;
            y--;
        }
        return new int[]{x, y, z};
    }
}
