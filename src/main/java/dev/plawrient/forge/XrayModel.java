package dev.plawrient.forge;

import dev.plawrient.module.Xray;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Obal kolem modelu bloku. Kdyz je Xray zapnuty:
 *  - nevybrane bloky nevraci zadne quady (jsou neviditelne),
 *  - vybrane bloky vraci vsechny quady jako "necullovane" (side == null), takze se vykresli
 *    i kdyz je ze vsech stran obklopene kamenem; quady maji zapecene plne svetlo a vypnute AO,
 *    jinak by byly cerne (sousedi jsou pevne bloky).
 */
public class XrayModel extends BakedModelWrapper<BakedModel> {
    public XrayModel(BakedModel original) { super(original); }

    private static boolean selected(@Nullable BlockState state) {
        Xray x = Xray.INSTANCE;
        return x != null && x.isEnabled() && state != null && x.blocks.contains(state.getBlock());
    }

    // Bez @Override zamerne: kdyby nektera z techto signatur ve Forge 1.20.1 nebyla, jen se nepouzije.
    public boolean useAmbientOcclusion(BlockState state) {
        return !selected(state) && originalModel.useAmbientOcclusion();
    }

    public boolean useAmbientOcclusion(BlockState state, RenderType renderType) {
        return !selected(state) && originalModel.useAmbientOcclusion();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return filtered(state, side, rand, null, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        return filtered(state, side, rand, data, renderType);
    }

    private List<BakedQuad> orig(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                 @Nullable ModelData data, @Nullable RenderType rt) {
        return data == null ? originalModel.getQuads(state, side, rand)
                            : originalModel.getQuads(state, side, rand, data, rt);
    }

    /** Kopie quadu s plnym svetlem (lightmap je v 7. int kazdeho vrcholu; Forge bere maximum). */
    private static BakedQuad bright(BakedQuad q) {
        int[] v = q.getVertices().clone();
        for (int i = 0; i < 4; i++) v[i * 8 + 6] = 0x00F000F0;
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade());
    }

    private List<BakedQuad> filtered(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                     @Nullable ModelData data, @Nullable RenderType rt) {
        Xray x = Xray.INSTANCE;
        if (state == null || x == null || !x.isEnabled()) return orig(state, side, rand, data, rt);
        if (!x.blocks.contains(state.getBlock())) return List.of();
        if (side != null) return List.of();

        List<BakedQuad> all = new ArrayList<>();
        for (Direction d : Direction.values()) all.addAll(orig(state, d, RandomSource.create(42L), data, rt));
        all.addAll(orig(state, null, RandomSource.create(42L), data, rt));
        all.replaceAll(XrayModel::bright);
        return all;
    }
}
