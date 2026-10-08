package dev.plawrient.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import dev.plawrient.gui.ClickGuiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.player.Input;

/**
 * Walk, jump and sprint with a screen open. Reads the keyboard directly (Forge's KeyMapping#isDown
 * is always false while a GUI is open, so polling the key mappings does not work).
 */
public class InventoryMove extends Module {
    public InventoryMove() { super("InventoryMove", "Move with inventory open", Category.MOVEMENT); }

    private static boolean down(long window, KeyMapping km) {
        return km.getKey().getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(window, km.getKey().getValue());
    }

    @Override public void onInput(Input in) {
        Minecraft mc = mc();
        var scr = mc.screen;
        var p = mc.player;
        if (scr == null || p == null) return;
        if (scr instanceof ChatScreen || scr instanceof ClickGuiScreen
                || scr instanceof AbstractSignEditScreen || scr instanceof BookEditScreen) return;
        if (scr.getFocused() instanceof EditBox) return; // typing into a search box, anvil, ...

        long w = mc.getWindow().getWindow();
        var o = mc.options;
        boolean up = down(w, o.keyUp), dn = down(w, o.keyDown), l = down(w, o.keyLeft), r = down(w, o.keyRight);
        in.up = up; in.down = dn; in.left = l; in.right = r;
        in.forwardImpulse = (up ? 1f : 0f) - (dn ? 1f : 0f);
        in.leftImpulse = (l ? 1f : 0f) - (r ? 1f : 0f);
        in.jumping = down(w, o.keyJump);
        if (in.forwardImpulse > 0 && down(w, o.keySprint)) p.setSprinting(true);
    }
}
