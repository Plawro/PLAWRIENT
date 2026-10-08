package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;

/**
 * 1.8 animace bloku mece (pouze vizualni, first person): drzis pravy klik s mecem
 * a nemas stit v offhandu. Transformace odpovidaji 1.8 (viz poznamky v kodu).
 */
public class SwordBlock extends Module {
    public static SwordBlock INSTANCE;

    public SwordBlock() {
        super("SwordBlock", "1.8 sword blocking animation", Category.RENDER);
        INSTANCE = this;
    }

    /** @return true = ruku jsme vykreslili my (zrusit vanilla render). */
    public boolean render(PoseStack ps, MultiBufferSource buf, int light, float equip, float swing,
                          ItemStack stack, InteractionHand hand) {
        if (!isEnabled() || hand != InteractionHand.MAIN_HAND) return false;
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.screen != null || Freecam.active()) return false;
        if (!(stack.getItem() instanceof SwordItem)) return false;
        if (p.getOffhandItem().getItem() instanceof ShieldItem) return false;
        if (p.getMainArm() != HumanoidArm.RIGHT || !mc.options.keyUse.isDown()) return false;

        ps.pushPose();
        // 1.8 transformFirstPersonItem
        ps.translate(0.56f, -0.52f + equip * -0.6f, -0.72f);
        ps.mulPose(Axis.YP.rotationDegrees(45f));
        float f = Mth.sin(swing * swing * (float) Math.PI);
        float f1 = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        ps.mulPose(Axis.YP.rotationDegrees(f * -20f));
        ps.mulPose(Axis.ZP.rotationDegrees(f1 * -20f));
        ps.mulPose(Axis.XP.rotationDegrees(f1 * -80f));
        ps.scale(0.4f, 0.4f, 0.4f);
        // 1.8 doBlockTransformations
        ps.translate(-0.5f, 0.2f, 0f);
        ps.mulPose(Axis.YP.rotationDegrees(30f));
        ps.mulPose(Axis.XP.rotationDegrees(-80f));
        ps.mulPose(Axis.YP.rotationDegrees(60f));
        // rozdil modelu 1.8 vs 1.20 (rotace -135 vs -90, scale 1.7 vs 0.68 -> 0.4 * 2.5 = 1)
        ps.mulPose(Axis.YP.rotationDegrees(-45f));
        ps.scale(2.5f, 2.5f, 2.5f);
        mc.getItemRenderer().renderStatic(p, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false,
                ps, buf, mc.level, light, OverlayTexture.NO_OVERLAY, p.getId());
        ps.popPose();
        return true;
    }
}
