package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * When the last item of a stack in your hand is used up (blocks, food, pearls, arrows...) or a tool breaks, the same
 * item is taken from your inventory into that slot. Works for the main hand and the offhand. Dropping the item
 * (Q), swapping hands (F) and moving things in the inventory screen do not count as "used up".
 */
public class AutoRefill extends Module {
    public final BoolSetting mainHand = add(new BoolSetting("mainHand", true));
    public final BoolSetting offhand = add(new BoolSetting("offhand", true));
    public final BoolSetting fromHotbar = add(new BoolSetting("fromHotbar", false));
    public final BoolSetting message = add(new BoolSetting("message", false));

    private int prevSel = -1, cooldown;
    private Item prevMain = Items.AIR, prevOff = Items.AIR;
    private int prevMainTotal, prevOffTotal;
    private boolean prevKeys;

    public AutoRefill() { super("AutoRefill", "Refills your hand from the inventory when the item runs out", Category.PLAYER); }

    @Override public void onDisable() { prevSel = -1; prevMain = Items.AIR; prevOff = Items.AIR; }

    private static int menuSlot(int i) { return i < 9 ? 36 + i : i; }

    /** How many of `item` you carry in total (inventory + offhand): drops only when something is really used up. */
    private static int count(LocalPlayer p, Item item) {
        if (item == Items.AIR) return 0;
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = inv.getItem(i); if (s.is(item)) n += s.getCount(); }
        ItemStack off = p.getOffhandItem();
        if (off.is(item)) n += off.getCount();
        return n;
    }

    private void snap(LocalPlayer p, boolean keys) {
        Inventory inv = p.getInventory();
        prevSel = inv.selected;
        prevMain = inv.getItem(inv.selected).getItem();
        prevOff = p.getOffhandItem().getItem();
        prevMainTotal = count(p, prevMain);
        prevOffTotal = count(p, prevOff);
        prevKeys = keys;
    }

    /** Inventory index of another stack of `item` (main inventory first), or -1. */
    private int find(Inventory inv, Item item, int skip) {
        for (int i = 9; i < 36; i++) if (i != skip && inv.getItem(i).is(item)) return i;
        if (fromHotbar.get()) for (int i = 0; i < 9; i++) if (i != skip && inv.getItem(i).is(item)) return i;
        return -1;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) { prevSel = -1; return; }
        if (cooldown > 0) cooldown--;
        if (mc.screen != null || p.isDeadOrDying() || p.getAbilities().instabuild || p.containerMenu != p.inventoryMenu) {
            snap(p, false);
            return;
        }

        boolean keys = mc.options.keyDrop.isDown() || mc.options.keySwapOffhand.isDown();
        Inventory inv = p.getInventory();
        int sel = inv.selected;
        ItemStack main = inv.getItem(sel), off = p.getOffhandItem();

        if (cooldown == 0 && !keys && !prevKeys && prevSel >= 0) {
            // main hand: same slot as last tick, held something, now empty (and it did not just move to the offhand)
            if (mainHand.get() && sel == prevSel && prevMain != Items.AIR && main.isEmpty()
                    && count(p, prevMain) < prevMainTotal) {                // really used up, not just moved by another module
                int src = find(inv, prevMain, sel);
                if (src >= 0) {
                    mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot(src), sel, ClickType.SWAP, p);
                    cooldown = 2;
                    if (message.get()) Chat.send("Refilled: " + inv.getItem(sel).getHoverName().getString());
                }
            }
            // offhand (totems are AutoTotem's job when that module is on)
            else if (offhand.get() && prevOff != Items.AIR && off.isEmpty() && count(p, prevOff) < prevOffTotal
                    && !(prevOff == Items.TOTEM_OF_UNDYING && ModuleManager.INSTANCE.get("AutoTotem") != null
                         && ModuleManager.INSTANCE.get("AutoTotem").isEnabled())) {
                int src = find(inv, prevOff, -1);
                if (src >= 0) {
                    mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot(src), 40, ClickType.SWAP, p);
                    cooldown = 2;
                    if (message.get()) Chat.send("Refilled offhand: " + p.getOffhandItem().getHoverName().getString());
                }
            }
        }
        snap(p, keys);
    }
}
