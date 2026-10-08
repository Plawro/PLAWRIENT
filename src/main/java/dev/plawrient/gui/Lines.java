package dev.plawrient.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Jedna usecka pro EspRenderTypes (POSITION_COLOR_NORMAL, mode LINES). PoseStack uz je posunuty o -kamera. */
public final class Lines {
    private Lines() {}

    public static void line(PoseStack ps, VertexConsumer vc, double x1, double y1, double z1,
                            double x2, double y2, double z2, float r, float g, float b, float a) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        dx /= len; dy /= len; dz /= len;
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
    }

    public static float red(int argb) { return (argb >> 16 & 255) / 255f; }
    public static float green(int argb) { return (argb >> 8 & 255) / 255f; }
    public static float blue(int argb) { return (argb & 255) / 255f; }
}
