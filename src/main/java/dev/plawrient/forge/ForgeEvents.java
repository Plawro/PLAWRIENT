package dev.plawrient.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.CommandManager;
import dev.plawrient.core.ModuleManager;
import dev.plawrient.gui.ClickGuiScreen;
import dev.plawrient.gui.EspRenderTypes;
import dev.plawrient.gui.Projection;
import dev.plawrient.module.BloodMoon;
import dev.plawrient.module.Freecam;
import dev.plawrient.module.LowFire;
import dev.plawrient.module.Nametags;
import dev.plawrient.module.SwordBlock;
import dev.plawrient.module.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/** Vsechny Forge eventy na jednom miste. Pri portu na Fabric se prepise jen tohle. */
public class ForgeEvents {
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ModuleManager.INSTANCE.tick();
    }

    @SubscribeEvent
    public void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (e.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT) { mc.setScreen(new ClickGuiScreen()); return; }
        ModuleManager.INSTANCE.onKey(e.getKey());
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent e) {
        String msg = e.getMessage();
        if (CommandManager.handle(msg)) {
            e.setCanceled(true);
            Minecraft.getInstance().gui.getChat().addRecentChat(msg);
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post e) {
        ModuleManager.INSTANCE.render2D(e.getGuiGraphics());
    }

    /** 3D vykreslovani (Storage/Block ESP) + aktualizace projekce pro 2D ESP a nametagy. */
    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            ModuleManager.INSTANCE.renderSky(e.getPoseStack(), e.getPartialTick());
            return;
        }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        Projection.update(ps.last().pose(), e.getProjectionMatrix(), cam);

        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        ModuleManager.INSTANCE.render3D(ps, bs, cam, e.getPartialTick());
        bs.endBatch(EspRenderTypes.LINES);
        bs.endBatch(EspRenderTypes.LINES_DEPTH);
        ps.popPose();
    }

    /** Skryje vanilla nametag, kdyz je zapnuty nas. */
    @SubscribeEvent
    public void onNameTag(RenderNameTagEvent e) {
        if (Nametags.hides(e.getEntity())) e.setResult(Event.Result.DENY);
    }

    /** Po vypoctu vstupu (WASD) - Speed a Freecam. */
    @SubscribeEvent
    public void onMovementInput(MovementInputUpdateEvent e) {
        ModuleManager.INSTANCE.input(e.getInput());
    }

    /** Freecam: kamera se otaci podle mysi. */
    @SubscribeEvent
    public void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!Freecam.active()) return;
        e.setYaw(Freecam.yaw());
        e.setPitch(Freecam.pitch());
    }

    /** Freecam: zadne kliky na server. */
    @SubscribeEvent
    public void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (Freecam.active()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    /** First person ruka: 1.8 blokovani mecem; ve freecamu se ruka neresi. */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent e) {
        if (Freecam.active()) { e.setCanceled(true); return; }
        SwordBlock sb = SwordBlock.INSTANCE;
        if (sb != null && sb.render(e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight(),
                e.getEquipProgress(), e.getSwingProgress(), e.getItemStack(), e.getHand())) {
            e.setCanceled(true);
        }
    }

    /** HUD vrstva pod HUD (cerveny nadech BloodMoon, low fire). */
    @SubscribeEvent
    public void onRenderGuiPre(RenderGuiEvent.Pre e) {
        ModuleManager.INSTANCE.renderPreHud(e.getGuiGraphics());
    }

    /** BloodMoon: cervena mlha. */
    @SubscribeEvent
    public void onFogColor(ViewportEvent.ComputeFogColor e) {
        BloodMoon b = BloodMoon.INSTANCE;
        if (b == null || !b.isEnabled()) return;
        float[] c = b.fogColor(e.getRed(), e.getGreen(), e.getBlue());
        e.setRed(c[0]); e.setGreen(c[1]); e.setBlue(c[2]);
    }

    @SubscribeEvent
    public void onFogDistance(ViewportEvent.RenderFog e) {
        BloodMoon b = BloodMoon.INSTANCE;
        if (b == null || !b.isEnabled() || e.getType() != FogType.NONE || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = b.fogFar(e.getFarPlaneDistance());
        if (far >= e.getFarPlaneDistance()) return;
        e.setNearPlaneDistance(far * 0.05f);
        e.setFarPlaneDistance(far);
        e.setCanceled(true);
    }

    /** LowFire: zrusi vanilla ohnivy overlay pres celou obrazovku (kresli ho LowFire modul dole). */
    @SubscribeEvent
    public void onScreenEffect(RenderBlockScreenEffectEvent e) {
        if (e.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.FIRE && LowFire.hidesVanilla())
            e.setCanceled(true);
    }

    /** Zoom: plynule zmenseni FOV. */
    @SubscribeEvent
    public void onFov(ViewportEvent.ComputeFov e) {
        Zoom z = Zoom.INSTANCE;
        if (z == null || !z.isEnabled() || !e.usedConfiguredFov()) return;
        double f = z.factor();
        if (f > 1.0001) e.setFOV(Zoom.apply(e.getFOV(), f));
    }

    /** Zoom: kolecko mysi meni mnozstvi priblizeni (a neprepina hotbar). */
    @SubscribeEvent
    public void onScroll(InputEvent.MouseScrollingEvent e) {
        Zoom z = Zoom.INSTANCE;
        if (z != null && z.consumeScroll(e.getScrollDelta())) e.setCanceled(true);
    }
}
