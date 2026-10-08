package dev.plawrient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderType;

/**
 * Cary pres zdi / s depth testem. Pozor: ve vanille NO_DEPTH_TEST depth test jen "nezapina", ale nevypina ho,
 * proto pouzivame vlastni layering shard, ktery depth test opravdu vypne a po vykresleni zase zapne.
 */
public final class EspRenderTypes extends RenderType {
    private EspRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean a, boolean s, Runnable r1, Runnable r2) {
        super(n, f, m, b, a, s, r1, r2);
    }

    private static final LayeringStateShard NO_DEPTH = new LayeringStateShard("plawrient_no_depth",
            RenderSystem::disableDepthTest, RenderSystem::enableDepthTest);
    private static final LayeringStateShard WITH_DEPTH = new LayeringStateShard("plawrient_with_depth",
            () -> { RenderSystem.enableDepthTest(); RenderSystem.depthFunc(515); }, () -> {});

    /** Pres zdi. */
    public static final RenderType LINES = create("plawrient_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(NO_DEPTH)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));

    /** Jen kde je videt (napr. cesta na zemi). */
    public static final RenderType LINES_DEPTH = create("plawrient_lines_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                    .setLayeringState(WITH_DEPTH)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));
}
