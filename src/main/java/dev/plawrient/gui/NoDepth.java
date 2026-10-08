package dev.plawrient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Obali libovolny MultiBufferSource tak, ze vsechno, co do nej vanilla renderer kresli, jde videt pres zdi. */
public final class NoDepth {
    private NoDepth() {}

    private static final Map<RenderType, RenderType> CACHE = new IdentityHashMap<>();

    private static final class Variant extends RenderType {
        Variant(RenderType o) {
            super("plawrient_nodepth_" + o, o.format(), o.mode(), o.bufferSize(), o.affectsCrumbling(), false,
                    () -> { o.setupRenderState(); RenderSystem.disableDepthTest(); },
                    () -> { o.clearRenderState(); RenderSystem.enableDepthTest(); });
        }
    }

    public static RenderType of(RenderType o) {
        synchronized (CACHE) { return CACHE.computeIfAbsent(o, Variant::new); }
    }

    public static MultiBufferSource wrap(MultiBufferSource src) { return rt -> src.getBuffer(of(rt)); }
}
