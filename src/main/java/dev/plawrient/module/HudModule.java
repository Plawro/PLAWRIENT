package dev.plawrient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.Draw;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Logo (vlastni text + font) a seznam zapnutych modulu. Bez pozadi, pokud ho nezapnes. */
public class HudModule extends Module {
    public final StringSetting text = add(new StringSetting("text", "PLAWRIENT"));
    public final FontSetting font = add(new FontSetting("font", "minecraft:default"));
    public final NumberSetting scale = add(new NumberSetting("scale", 1.5, 0.5, 5.0));
    public final BoolSetting background = add(new BoolSetting("background", false));
    public final NumberSetting opacity = add(new NumberSetting("opacity", 0.55, 0.0, 1.0));
    public final BoolSetting arrayList = add(new BoolSetting("arraylist", true));

    public HudModule() {
        super("Hud", "Logo and module list", Category.HUD);
        this.enabled = true;
    }

    @Override public void onRender2D(GuiGraphics g) {
        Minecraft mc = mc();
        if (mc.options.renderDebug) return;

        Component logo = Draw.component(text.get());
        float s = scale.get().floatValue();
        int tw = (int) (mc.font.width(logo) * s);
        int th = (int) (mc.font.lineHeight * s);
        int pad = background.get() ? 5 : 0;
        if (background.get()) {
            Draw.rrect(g, 4, 4, tw + 12, th + 10, 5, Draw.bg(opacity.get()));
            Draw.rrect(g, 9, 4 + th + 8, tw, 1, 0, Draw.ACCENT);
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(s, s, 1f);
        g.drawString(mc.font, logo, (int) ((5 + pad) / s), (int) ((5 + pad) / s), Draw.TEXT, true);
        ps.popPose();

        if (arrayList.get()) {
            List<Module> on = ModuleManager.INSTANCE.all().stream()
                    .filter(m -> m != this && m.isEnabled() && m.showInList())
                    .sorted(Comparator.comparingInt((Module m) -> Draw.width(m.name)).reversed())
                    .toList();
            int y = 5;
            for (int i = 0; i < on.size(); i++) {
                Module m = on.get(i);
                int w = Draw.width(m.name) + 10;
                int x = g.guiWidth() - w - 4;
                float t = on.size() <= 1 ? 0 : i / (float) (on.size() - 1);
                if (background.get()) Draw.rrect(g, x, y - 1, w, 12, 4, Draw.bg(opacity.get()));
                Draw.rrect(g, x + w - 2, y, 2, 10, 1, Draw.lerp(Draw.ACCENT, Draw.ACCENT2, t));
                Draw.text(g, m.name, x + 3, y + 1, Draw.TEXT, !background.get());
                y += 13;
            }
        }
    }
}
