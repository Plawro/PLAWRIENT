package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;

/**
 * When you are on fire: quickly looks straight down, uses the extinguishing item (water bucket by default),
 * picks the water back up and restores your slot and view. In dimensions where water evaporates (Nether) it uses
 * the "netherItems" list instead (default: powder snow bucket; add modded items with the item picker) or drinks a
 * Fire Resistance potion.
 */
public class AutoWater extends Module {
    public static AutoWater INSTANCE;
    public final ItemListSetting items = add(new ItemListSetting("items", "water_bucket"));
    public final ItemListSetting netherItems = add(new ItemListSetting("netherItems", "powder_snow_bucket"));
    public final BoolSetting netherPotion = add(new BoolSetting("netherFireResistance", true));
    public final BoolSetting fromInventory = add(new BoolSetting("fromInventory", true));
    public final BoolSetting message = add(new BoolSetting("message", true));

    private enum S { IDLE, AIM, USE, WAIT, PICKUP, DONE }

    private S state = S.IDLE;
    private int timer, waited, prevSlot, swapInv = -1, swapHot = -1, cooldown;
    private float prevPitch;
    private boolean needPickup, hold;
    private Item usedItem = Items.AIR;

    public AutoWater() { super("AutoWater", "Extinguishes you when on fire", Category.PLAYER); INSTANCE = this; }

    /** True while an extinguish sequence is running (WaterClutch then stays out of the way). */
    public boolean busy() { return state != S.IDLE; }

    @Override public void onDisable() { abort(); }

    private static boolean fireResistance(ItemStack s) {
        if (!s.is(Items.POTION)) return false;
        for (MobEffectInstance e : PotionUtils.getMobEffects(s)) if (e.getEffect() == MobEffects.FIRE_RESISTANCE) return true;
        return false;
    }

    private int find(Inventory inv, boolean nether) {
        List<Item> prefs = (nether ? netherItems : items).items();
        for (Item it : prefs)
            for (int i = 0; i < 36; i++)
                if (inv.getItem(i).is(it) && (i < 9 || fromInventory.get())) return i;
        if (nether && netherPotion.get())
            for (int i = 0; i < 36; i++)
                if (fireResistance(inv.getItem(i)) && (i < 9 || fromInventory.get())) return i;
        return -1;
    }

    private void restore(Minecraft mc, LocalPlayer p) {
        p.setXRot(prevPitch);
        p.xRotO = prevPitch;
        if (hold) mc.options.keyUse.setDown(false);
        if (swapInv >= 0 && mc.gameMode != null)
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, swapInv, swapHot, ClickType.SWAP, p);
        p.getInventory().selected = prevSlot;
    }

    private void abort() {
        Minecraft mc = mc();
        if (state != S.IDLE && mc.player != null) restore(mc, mc.player);
        state = S.IDLE; swapInv = -1; swapHot = -1; hold = false;
    }

    private void finish(Minecraft mc, LocalPlayer p) {
        restore(mc, p);
        state = S.IDLE; swapInv = -1; swapHot = -1; hold = false;
        cooldown = 40;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) { state = S.IDLE; return; }
        if (cooldown > 0) cooldown--;
        Inventory inv = p.getInventory();

        if (state == S.IDLE) {
            if (cooldown > 0 || mc.screen != null || Freecam.active() || WaterClutch.active()) return;
            if (!p.isOnFire() || p.isInWater() || p.isSpectator() || p.getAbilities().instabuild || p.isUsingItem()) return;
            if (p.hasEffect(MobEffects.FIRE_RESISTANCE)) return;
            boolean nether = mc.level.dimensionType().ultraWarm();
            int slot = find(inv, nether);
            if (slot < 0) return;

            prevSlot = inv.selected;
            prevPitch = p.getXRot();
            if (slot < 9) { inv.selected = slot; timer = 1; }
            else {
                swapHot = inv.selected; swapInv = slot;
                mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, slot, inv.selected, ClickType.SWAP, p);
                timer = 2;
            }
            ItemStack used = inv.getItem(inv.selected);
            usedItem = used.getItem();
            needPickup = used.is(Items.WATER_BUCKET);
            hold = used.getUseAnimation() != UseAnim.NONE;   // potions etc. have to be held
            waited = 0;
            state = S.AIM;
            if (message.get()) Chat.send("On fire - using " + used.getHoverName().getString());
            return;
        }

        if (mc.screen != null || p.isDeadOrDying()) { abort(); return; }
        p.setXRot(90f);            // look straight down; the server needs a tick to learn the new rotation
        p.xRotO = 90f;

        switch (state) {
            case AIM -> { if (--timer <= 0) state = S.USE; }
            case USE -> {
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                if (hold) mc.options.keyUse.setDown(true);
                waited = 0;
                state = S.WAIT;
            }
            case WAIT -> {
                waited++;
                if (hold) {
                    mc.options.keyUse.setDown(true);
                    if ((waited > 2 && !p.isUsingItem()) || waited > 60) { state = S.DONE; timer = 2; }
                    return;
                }
                boolean placed = !p.getMainHandItem().is(usedItem);        // bucket turned into an empty bucket
                if (!placed) { if (waited >= 4) { state = S.DONE; timer = 1; } return; }
                if (!p.isOnFire() || waited >= 10) {
                    if (needPickup && p.getMainHandItem().is(Items.BUCKET)) { state = S.PICKUP; timer = 1; }
                    else { state = S.DONE; timer = 2; }
                }
            }
            case PICKUP -> {
                if (--timer > 0) return;
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);        // empty bucket on the water source below you
                state = S.DONE;
                timer = 3;
            }
            case DONE -> { if (--timer <= 0) finish(mc, p); }
            default -> {}
        }
    }
}
