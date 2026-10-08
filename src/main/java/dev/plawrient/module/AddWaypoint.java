package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** One-shot module: bind a key to it and every press adds a waypoint where you look (works in freecam). */
public class AddWaypoint extends Module {
    public AddWaypoint() { super("AddWaypoint", "Bind a key: adds a waypoint where you look", Category.PLAYER); }

    @Override public void onEnable() {
        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) { enabled = false; return; }
        BlockPos p = Pathfinder.lookTarget(mc, 256);
        if (p == null) p = mc.player.blockPosition();
        Waypoints.add(null, p);
        enabled = false; // momentary
    }
}
