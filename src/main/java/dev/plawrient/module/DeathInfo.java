package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** When you die: death coordinates + dimension + cause in chat (click to copy) and an automatic "Death" waypoint. */
public class DeathInfo extends Module {
    public final BoolSetting chat = add(new BoolSetting("chat", true));
    public final BoolSetting waypoint = add(new BoolSetting("waypoint", true));

    private boolean dead, reported;
    private int deadTicks;
    private Vec3 pos = Vec3.ZERO;
    private String dim = "?";

    public DeathInfo() {
        super("DeathInfo", "Shows death position in chat + Death waypoint", Category.PLAYER);
        this.enabled = true;
    }

    /** Reads the (private) cause-of-death text from the vanilla death screen. */
    private static String cause(Screen s) {
        try {
            for (Field f : DeathScreen.class.getDeclaredFields()) {
                if (f.getType() == Component.class && Modifier.isFinal(f.getModifiers())) {
                    f.setAccessible(true);
                    Component c = (Component) f.get(s);
                    return c == null ? null : c.getString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        var p = mc.player;
        if (p == null || mc.level == null) { dead = false; reported = false; return; }

        if (!p.isDeadOrDying()) { dead = false; reported = false; return; }
        if (!dead) {
            dead = true; reported = false; deadTicks = 0;
            pos = p.position();
            dim = mc.level.dimension().location().toString();
        }
        if (reported) return;
        deadTicks++;
        if (!(mc.screen instanceof DeathScreen) && deadTicks < 20) return; // give the death screen a moment to appear
        reported = true;

        int x = (int) Math.floor(pos.x), y = (int) Math.floor(pos.y), z = (int) Math.floor(pos.z);
        String why = mc.screen instanceof DeathScreen ? cause(mc.screen) : null;
        if (chat.get()) {
            Chat.copyable("You died at " + x + ", " + y + ", " + z + " (" + dim + ")" + (why != null ? " - " + why : ""),
                    x + " " + y + " " + z);
        }
        if (waypoint.get()) {
            Waypoints.removeByName("Death");
            Waypoints.add("Death", BlockPos.containing(pos));
            if (chat.get()) Chat.send("Path back: enable Route and type &wp go Death");
        }
    }
}
