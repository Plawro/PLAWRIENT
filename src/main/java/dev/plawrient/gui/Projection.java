package dev.plawrient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Prevod svetovych souradnic na souradnice GUI (pro 2D ESP a nametagy). Aktualizuje se kazdy snimek. */
public final class Projection {
    private Projection() {}

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 cam = Vec3.ZERO;
    private static boolean valid;

    public static void update(Matrix4f view, Matrix4f proj, Vec3 camPos) {
        VIEW.set(view);
        PROJ.set(proj);
        cam = camPos;
        valid = true;
    }

    public static Vec3 camPos() { return cam; }

    /** @return {x, y} v GUI souradnicich, nebo null kdyz je bod za kamerou. */
    public static float[] toScreen(double x, double y, double z) {
        if (!valid) return null;
        Vector4f v = new Vector4f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1f);
        v.mul(VIEW);
        v.mul(PROJ);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        var w = Minecraft.getInstance().getWindow();
        return new float[]{(nx * 0.5f + 0.5f) * w.getGuiScaledWidth(), (1f - (ny * 0.5f + 0.5f)) * w.getGuiScaledHeight()};
    }

    /** Obdelnik na obrazovce kolem AABB: {minX, minY, maxX, maxY}, nebo null kdyz je nektery roh za kamerou. */
    public static float[] screenRect(net.minecraft.world.phys.AABB bb) {
        double[] xs = {bb.minX, bb.maxX}, ys = {bb.minY, bb.maxY}, zs = {bb.minZ, bb.maxZ};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++)
                for (int k = 0; k < 2; k++) {
                    float[] p = toScreen(xs[i], ys[j], zs[k]);
                    if (p == null) return null;
                    minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                }
        return new float[]{minX, minY, maxX, maxY};
    }
}
