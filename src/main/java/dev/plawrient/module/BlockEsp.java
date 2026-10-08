package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.EspRenderTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/** Zvyrazni vybrane bloky pres zdi (kazdy typ jinou barvou). Vyber: klik na "blocks" v menu. */
public class BlockEsp extends Module {
    public final BlockListSetting blocks = add(new BlockListSetting("blocks",
            "diamond_ore", "deepslate_diamond_ore", "ancient_debris", "spawner"));
    public final NumberSetting range = add(new NumberSetting("range", 24, 8, 64));

    private record Hit(BlockPos pos, float r, float g, float b) {}
    private List<Hit> hits = List.of();

    public BlockEsp() { super("BlockESP", "Selected blocks through walls", Category.RENDER); }

    @Override public void onDisable() { hits = List.of(); }

    @Override public void onTick() {
        var mc = mc();
        var level = mc.level;
        var p = mc.player;
        if (level == null || p == null) { hits = List.of(); return; }
        if (p.tickCount % 20 != 3) return;

        Set<Block> set = blocks.snapshot();
        if (set.isEmpty()) { hits = List.of(); return; }

        Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        int r = range.get().intValue();
        double r2 = (double) r * r;
        int cx0 = (int) Math.floor((c.x - r) / 16.0), cx1 = (int) Math.floor((c.x + r) / 16.0);
        int cz0 = (int) Math.floor((c.z - r) / 16.0), cz1 = (int) Math.floor((c.z + r) / 16.0);

        List<Hit> out = new ArrayList<>();
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
                    if (!sec.maybeHas(st -> set.contains(st.getBlock()))) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState st = sec.getBlockState(x, y, z);
                                Block b = st.getBlock();
                                if (!set.contains(b)) continue;
                                int wx = (cx << 4) + x, wy = baseY + y, wz = (cz << 4) + z;
                                double dx = wx + 0.5 - c.x, dy = wy + 0.5 - c.y, dz = wz + 0.5 - c.z;
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                int h = Math.abs(BuiltInRegistries.BLOCK.getKey(b).hashCode());
                                int rgb = Mth.hsvToRgb((h % 360) / 360f, 0.75f, 1f);
                                out.add(new Hit(new BlockPos(wx, wy, wz),
                                        (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f));
                                if (out.size() >= 1500) break outer;
                            }
                        }
                    }
                }
            }
        }
        hits = out;
    }

    @Override public void onRender3D(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cam, float pt) {
        if (hits.isEmpty()) return;
        var vc = buf.getBuffer(EspRenderTypes.LINES);
        for (Hit h : hits) {
            double x = h.pos.getX(), y = h.pos.getY(), z = h.pos.getZ();
            LevelRenderer.renderLineBox(ps, vc, x, y, z, x + 1, y + 1, z + 1, h.r, h.g, h.b, 1f);
        }
    }
}
