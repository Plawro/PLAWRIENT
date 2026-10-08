package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.EspRenderTypes;
import dev.plawrient.gui.NoDepth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Chests, ender chests, barrels and shulkers through walls: outline boxes + the real model ("chams"). */
public class StorageEsp extends Module {
    public final BoolSetting chests = add(new BoolSetting("chests", true));
    public final BoolSetting enderChests = add(new BoolSetting("enderchests", true));
    public final BoolSetting barrels = add(new BoolSetting("barrels", true));
    public final BoolSetting shulkers = add(new BoolSetting("shulkers", true));
    public final BoolSetting boxes = add(new BoolSetting("boxes", true));
    public final BoolSetting chams = add(new BoolSetting("chams", true));
    public final NumberSetting range = add(new NumberSetting("range", 96, 16, 256));

    private record Hit(AABB box, BlockEntity be, float r, float g, float b) {}
    private List<Hit> hits = List.of();

    public StorageEsp() { super("StorageESP", "Chests, barrels, shulkers through walls", Category.RENDER); }

    @Override public void onDisable() { hits = List.of(); }

    @Override public void onTick() {
        var mc = mc();
        var level = mc.level;
        var p = mc.player;
        if (level == null || p == null) { hits = List.of(); return; }
        if (p.tickCount % 10 != 0) return;

        Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        double r = range.get();
        double r2 = r * r;
        int rd = Math.min((int) Math.ceil(r / 16.0) + 1, mc.options.getEffectiveRenderDistance() + 1);
        int pcx = (int) Math.floor(c.x / 16.0), pcz = (int) Math.floor(c.z / 16.0);

        List<Hit> out = new ArrayList<>();
        for (int cx = pcx - rd; cx <= pcx + rd; cx++) {
            for (int cz = pcz - rd; cz <= pcz + rd; cz++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    float[] col = colorFor(be);
                    if (col == null) continue;
                    BlockPos pos = be.getBlockPos();
                    if (c.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r2) continue;
                    VoxelShape shape = be.getBlockState().getShape(level, pos);
                    AABB box = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(pos).inflate(0.002);
                    out.add(new Hit(box, be, col[0], col[1], col[2]));
                }
            }
        }
        hits = out;
    }

    private float[] colorFor(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return chests.get() ? new float[]{1f, 0.25f, 0.25f} : null;
        if (be instanceof ChestBlockEntity) return chests.get() ? new float[]{1f, 0.65f, 0.1f} : null;
        if (be instanceof EnderChestBlockEntity) return enderChests.get() ? new float[]{0.7f, 0.3f, 1f} : null;
        if (be instanceof BarrelBlockEntity) return barrels.get() ? new float[]{0.75f, 0.55f, 0.3f} : null;
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get() ? new float[]{1f, 0.4f, 0.8f} : null;
        return null;
    }

    @Override public void onRender3D(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float pt) {
        if (hits.isEmpty()) return;
        var mc = mc();

        if (chams.get()) {
            var dispatcher = mc.getBlockEntityRenderDispatcher();
            var blockRenderer = mc.getBlockRenderer();
            MultiBufferSource src = NoDepth.wrap(buf);
            for (Hit h : hits) {
                BlockEntity be = h.be;
                if (be.isRemoved() || be.getLevel() == null) continue;
                BlockPos pos = be.getBlockPos();
                ps.pushPose();
                ps.translate(pos.getX(), pos.getY(), pos.getZ());
                BlockEntityRenderer<BlockEntity> r = dispatcher.getRenderer(be);
                if (r != null) r.render(be, pt, ps, src, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                else blockRenderer.renderSingleBlock(be.getBlockState(), ps, src, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            buf.endBatch();
        }

        if (boxes.get()) {
            var vc = buf.getBuffer(EspRenderTypes.LINES);
            for (Hit h : hits) {
                AABB b = h.box;
                LevelRenderer.renderLineBox(ps, vc, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, h.r, h.g, h.b, 1f);
            }
        }
    }
}
