package dev.plawrient.module;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * "Nejlepsi cesta": greedy planner. Z pozice hrace po kratkych krocich vybira smer (az +-70 stupnu od pohledu)
 * s nejmensi cenou: lava/ohen/kaktus/magma, utes, prilis vysoky schod, zdi a blizkost nepratel.
 */
final class VisionPlanner {
    private VisionPlanner() {}

    record Result(List<Vec3> points, boolean[] danger, boolean blocked) {}
    private record Cell(double y, double cost) {}

    static final int STEPS = 14;
    private static final double STEP = 1.6;

    static Result plan(ClientLevel level, LocalPlayer p, List<? extends Entity> hostiles) {
        double x = p.getX(), z = p.getZ(), curY = p.getY();
        float yaw = p.getYRot();
        int[] offs = {0, -20, 20, -40, 40, -70, 70};

        List<Vec3> pts = new ArrayList<>();
        pts.add(new Vec3(x, curY, z));
        boolean[] danger = new boolean[STEPS];
        boolean blocked = false;
        double prevOff = 0;

        for (int i = 0; i < STEPS; i++) {
            double best = Double.MAX_VALUE, bx = x, bz = z, by = curY, bo = 0;
            for (int off : offs) {
                double rad = Math.toRadians(yaw + off);
                double nx = x - Math.sin(rad) * STEP, nz = z + Math.cos(rad) * STEP;
                Cell c = cell(level, nx, curY, nz);
                double cost = c.cost() + Math.abs(off) * 0.03 + Math.abs(off - prevOff) * 0.04;
                for (Entity h : hostiles) {
                    double dd = h.distanceToSqr(nx, c.y(), nz);
                    if (dd < 16) cost += 25 * (1 - Math.sqrt(dd) / 4);
                }
                if (cost < best) { best = cost; bx = nx; bz = nz; by = c.y(); bo = off; }
            }
            if (best >= 60 && i < 6) blocked = true;
            danger[i] = best >= 30;
            x = bx; z = bz; curY = by; prevOff = bo;
            pts.add(new Vec3(x, curY, z));
        }
        return new Result(pts, danger, blocked);
    }

    private static Cell cell(ClientLevel level, double x, double refY, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(x), 0, Mth.floor(z));
        int fy = Mth.floor(refY + 0.01);
        int found = Integer.MIN_VALUE;
        for (int yy = fy + 2; yy >= fy - 5; yy--) {
            m.setY(yy);
            BlockState s = level.getBlockState(m);
            if (!s.getCollisionShape(level, m).isEmpty() || !s.getFluidState().isEmpty()) { found = yy; break; }
        }
        if (found == Integer.MIN_VALUE) return new Cell(refY - 5, 60); // cliff / void

        m.setY(found);
        BlockState ground = level.getBlockState(m);
        FluidState fs = ground.getFluidState();
        double cost = 0;
        if (fs.is(FluidTags.LAVA)) cost += 100;
        else if (!fs.isEmpty()) cost += 6;
        else if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS) || ground.is(Blocks.SWEET_BERRY_BUSH)
                || ground.is(BlockTags.CAMPFIRES) || ground.is(BlockTags.FIRE)) cost += 70;

        for (int h = 1; h <= 2; h++) { // headroom
            m.setY(found + h);
            BlockState a = level.getBlockState(m);
            if (!a.getCollisionShape(level, m).isEmpty()) cost += 80;
            if (a.is(BlockTags.FIRE) || a.getFluidState().is(FluidTags.LAVA)) cost += 100;
        }

        double standY = found + 1;
        double dy = standY - refY;
        if (dy > 1.1) cost += 12 * (dy - 1);
        if (dy < -3) cost += 20 + 15 * (-dy - 3);
        return new Cell(standY, cost);
    }
}
