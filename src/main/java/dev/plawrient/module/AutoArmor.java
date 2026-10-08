package dev.plawrient.module;

import dev.plawrient.core.*;
import dev.plawrient.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** When an armor piece breaks, the best replacement for that slot is equipped from your inventory (shift-click). */
public class AutoArmor extends Module {
    public final BoolSetting message = add(new BoolSetting("message", true));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
    private final ItemStack[] prev = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final boolean[] pending = new boolean[4];
    private int cooldown;

    public AutoArmor() { super("AutoArmor", "Replaces broken armor from your inventory", Category.PLAYER); }

    private static double score(ItemStack s) {
        if (!(s.getItem() instanceof ArmorItem a)) return -1;
        if (EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BINDING_CURSE, s) > 0) return -1;
        if (s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 2) return -1;
        double v = a.getDefense() + a.getToughness() * 0.7
                + EnchantmentHelper.getTagEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, s) * 0.8;
        if (s.isDamageableItem()) v += 0.5 * (1.0 - s.getDamageValue() / (double) s.getMaxDamage());
        return v;
    }

    @Override public void onTick() {
        Minecraft mc = mc();
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) { java.util.Arrays.fill(prev, ItemStack.EMPTY); return; }
        Inventory inv = p.getInventory();
        if (cooldown > 0) cooldown--;

        for (int i = 0; i < 4; i++) {
            ItemStack cur = inv.armor.get(i);
            if (cur.isEmpty() && !prev[i].isEmpty() && mc.screen == null && !p.getAbilities().instabuild) {
                ItemStack old = prev[i];
                if (old.isDamageableItem() && old.getMaxDamage() - old.getDamageValue() <= 30) pending[i] = true; // it broke
            }
            prev[i] = cur.copy();
        }

        if (cooldown > 0) return;
        for (int i = 0; i < 4; i++) {
            if (!pending[i]) continue;
            if (!inv.armor.get(i).isEmpty()) { pending[i] = false; continue; }

            int bestIdx = -1;
            double best = 0;
            for (int s = 0; s < 36; s++) {
                ItemStack st = inv.getItem(s);
                if (!(st.getItem() instanceof ArmorItem a) || a.getEquipmentSlot() != SLOTS[i]) continue;
                double v = score(st);
                if (v > best) { best = v; bestIdx = s; }
            }
            pending[i] = false;
            if (bestIdx < 0) { if (message.get()) Chat.send("Armor broke - no replacement in inventory."); continue; }

            int menuSlot = bestIdx < 9 ? bestIdx + 36 : bestIdx; // hotbar items are menu slots 36-44
            String name = inv.getItem(bestIdx).getHoverName().getString();
            mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot, 0, ClickType.QUICK_MOVE, p);
            if (message.get()) Chat.send("Armor broke - equipped " + name);
            cooldown = 4;
            return;
        }
    }
}
